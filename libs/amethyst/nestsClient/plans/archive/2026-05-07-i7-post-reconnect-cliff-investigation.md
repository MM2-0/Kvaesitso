# I7 post-reconnect cliff investigation

> **Status:** shipped — investigation-only record; documents the post-reconnect cliff and pins the residual cause on the upstream moq-relay 0.10.x forward queue (no in-repo fix).
> _Audited 2026-06-30._

**Status: investigation only.** No production code change. Documents the
observation, traces the listener-side and relay-side suspects, and
records what would be needed to actually root-cause and fix it.

## The observation (from `feat/nests-i7-publisher-reconnect`)

`HangInteropReverseTest.rust_hang_publish_reconnect_kotlin_listener_recovers`
asserts ≥ 2.5 s of decoded mono PCM after the Rust `hang-publish`
binary cycles its session at the 2.5 s mark of a 5 s broadcast. The
test passes at 2.5 s but only marginally:

| Phase | Wallclock window | Captured |
|---|---|---|
| Pre-reconnect (cycle 1) | 0.0–2.5 s | ~1.9 s of Opus (~95 frames × 20 ms) |
| Re-issuance gap | ~2.5–2.6 s | empty (100 ms `RESUBSCRIBE_BACKOFF_MS`) |
| Post-reconnect (cycle 2) | 2.6–5.0 s | ~1.0 s of Opus (groupSeq 0–9), then nothing |
| **Total observed** | | **~2.86 s out of ~5.0 s possible** |

The Rust publisher's stdout shows it continued emitting cycle-2
groupSeq 10–24 (~1.5 s more audio) AFTER the listener stopped
receiving uni streams. The relay logs show only cycle-1's subscription
was cancelled — the cycle-2 subscription is still "active" from the
relay's POV.

So the failure mode is the well-known moq-relay 0.10.x silent
forward stall: relay still considers the subscription healthy, the
listener still sees a connected session, but the relay never opens
new uni streams for the post-cycle frames.

## Listener side: ruled out

Walking the Kotlin side end-to-end:

### A. `subscribeId` reuse / stale routing

`MoqLiteSession.subscribeSpeaker` allocates a fresh `subscribeId`
for every subscribe (`MoqLiteSession.kt:249` —
`val next = nextSubscribeId++`). When the inner handle's bidi
collector exits (the relay's SubscribeDrop on cycle-1 publisher
end), the entry is removed from `subscriptionsBySubscribeId` at
`MoqLiteSession.kt:393`. Cycle-2 gets a fresh subscribeId
distinct from cycle-1's. Group headers (`drainOneGroup#$streamSeq
header subId=...`) carry the wire-level subscribeId; mismatches
would surface as `droppedNoSub` in the trace logs (line 632), and
those didn't increase. **Not the bug.**

### B. `MAX_STREAMS_UNI` credit

The cliff investigation already raised `initialMaxStreamsUni` to
1M (`c3d6cadff`); a 5-second 50-fps broadcast at
`framesPerGroup = 5` is 50 streams total. We never approach the cap.
Flow-control snapshots in the round-2 sweep showed
`peerMaxStreamsUniNow = 10000` (the relay's `max_concurrent_uni_streams`
default), with `peerInitiatedUni == received + 1` cleanly. Same
ceiling applies here. **Not the bug.**

### C. SUBSCRIBE_BUFFER overflow

`ReconnectingNestsListener.SUBSCRIBE_BUFFER = 64`, with
`onBufferOverflow = DROP_OLDEST`. ~5 s of audio at 50 fps is 250
frames; if the consumer were slow, oldest frames would drop, but the
total count would still cap near 250. We see ~143 frames (2.86 s ×
50 fps). **Not consistent with consumer-side back-pressure.**

### D. Inner-pump opener threw

If the cycle-2 `opener(listener)` threw (relay rejected the new
subscribe), the wrapper retries with exponential backoff
(250 → 500 → 1000 ms, capped). 2.5 s of headroom would still allow
~5 retries. The wrapper's `Log.w("NestRx") { "ReconnectingHandle.opener
threw ..." }` would have fired. The I7 agent's transcript doesn't
show this log. **Not the bug.**

## Relay side: the prime suspect

Per the cliff investigation's moq-rs 0.10.25 source audit
(`2026-05-01-quic-stream-cliff-investigation.md:99-150`):

> 3. **Unbounded task pool feeding the awaits.** The publisher pushes
>    blocked `serve_group` tasks into a `FuturesUnordered`
>    (publisher.rs:325, 346). The receive loop keeps spawning more
>    serve tasks as upstream groups arrive. No backpressure path
>    back to the publisher to slow upstream ingestion.

The cycle-1 → cycle-2 transition at the relay involves:

1. Cycle-1 publisher session ends → relay propagates `Announce::Ended`
   for the broadcast suffix.
2. Relay drops cycle-1's subscriptions (Drop frame on each subscribe
   bidi) — the listener observes this as `handle.objects` flow
   completing.
3. Cycle-2 publisher session opens → relay propagates `Announce::Active`
   for the same suffix.
4. Listener's wrapper re-subscribes with a fresh subscribeId on the
   same QUIC session.
5. Relay routes cycle-2's incoming groups to the new subscriber.

The opening for cycle-2 frames going dark after group ~10 fits the
**publisher-side `serve_group` task pool** described above:

- During cycle 1, the relay had cycle-1's subscriber forward tasks
  queued in the pool. When the publisher session ended, those tasks
  may have completed cleanly (they FIN'd uni streams to the
  listener), OR they may have been left in `Pending` if the upstream
  source vanished mid-write.
- The cycle-1 subscription's removal from the per-track subscriber
  list does NOT necessarily cancel queued forward tasks for that
  subscriber — moq-rs 0.10.25's `serve_group` doesn't take a
  cancellation handle from the per-subscription bookkeeping.
- Cycle 2 starts fresh, but the per-track group queue
  (`groups: VecDeque<Option<(GroupProducer, Instant)>>`,
  `track.rs:69-90`) is shared across publisher sessions for the same
  broadcast suffix.
- The ~10-group budget before cycle-2 stalls correlates with the
  task pool's residual cycle-1 footprint — once the pool's effective
  ceiling is reached, new `open_uni().await`s park indefinitely.

This is consistent with the I7 commit's hypothesis: "moq-relay 0.10.x
per-broadcast forward queue holding cycle-2 frames behind cycle-1
fan-out". It's the *same* per-subscriber forward cliff the cliff plan
already documented, surfacing here as a per-broadcast cliff because
the listener's QUIC session straddles two publisher cycles for the
same broadcast suffix.

## Confirming the diagnosis (what would need to happen)

The two listener-side and one relay-side suspects narrow down to one
hypothesis, but the data isn't conclusive. To confirm:

1. **Reproduce in the diagnostic Kotlin↔Kotlin path.** Add a
   `KotlinSpeakerCyclesKotlinListenerThroughNativeRelayTest` that
   mirrors I7 but uses `connectReconnectingNestsSpeaker` cycling
   on a 2.5 s timer instead of the Rust `hang-publish`
   `--reconnect-after-ms` flag. Same listener wrapper. If the
   Kotlin↔Kotlin reproducer hits the cliff, it's relay-side
   confirmed (Kotlin↔Kotlin shares NO publisher code with the Rust
   path; only the relay is common).
2. **`flowControlSnapshot` during cycle 2.** Reuse the listener-side
   snapshot wiring from `:quic` (commit `d391ae1d`'s fix). If
   `peerInitiatedUni` stops incrementing while
   `peerMaxStreamsUniNow == 10000` stays unchanged AND
   `pendingBytes == 0`, the relay is the one that stopped opening
   streams.
3. **Listener-side QUIC packet capture.** Wrap the loopback UDP
   socket via `udp-loss-shim` modified to also tap+log packets.
   Cycle-1 closing should show STREAM FINs + RESET_STREAM frames;
   cycle-2 starting should show fresh STREAM frames addressed at a
   higher stream id. Stalled cycle-2 = no further STREAM frames
   after stream id N.

Steps 1 and 2 are mechanical; step 3 is harder but most diagnostic.

## Mitigations to consider (if confirmed)

### Listener side: force a fresh moq session on inner cycle

In `ReconnectingNestsListener.reissuingSubscribe`, when the inner
`handle.objects` flow ends, instead of just looping back to call
`opener(listener)`, call `recycleSession()` to tear down the entire
inner moq session and let the orchestrator open a fresh one.

**Tradeoff:** ~500–1000 ms more of audio gap (full QUIC handshake +
moq-lite ALPN vs. just a fresh subscribe bidi). Currently 100 ms
gap. Plus a fresh JWT session token (which the wrapper already
mints on cycle).

**Justification:** the relay's per-broadcast forward queue is
process-global at the relay; the only client-side leverage to
clear it is to make the relay drop and re-create the per-listener
subscriber state from scratch, which a fresh QUIC session does.

This is hypothesis-driven and would need (1) confirmation per the
section above and (2) a regression test (the I7 scenario, but with
the threshold raised from 2.5 s to ~3.8 s after the mitigation
lands).

### Relay side: file upstream

The cliff plan's existing open follow-up #1 already proposes this
upstream feature request:

> File a feature request at `kixelated/moq` describing the
> per-subscriber forward-queue cliff and proposing (a) per-deployment
> tuning of the unbounded `FuturesUnordered` task pool, and (b) a
> deadline on `serve_group()`'s `open_uni().await` derived from the
> active subscriber's smallest `max_latency`.

If the upstream lands either knob, the per-broadcast cliff goes away
too.

### Production side: nothing for now

The I7 scenario stresses the relay specifically by forcing a session
cycle every 2.5 s. Production audio rooms don't cycle this
aggressively — `connectReconnectingNestsSpeaker.tokenRefreshAfterMs`
defaults to 540_000 ms (9 minutes), and the relay's per-broadcast
forward queue has 9-minutes of breathing room between cycles. The
cliff is not currently observed in production traffic.

## Files referenced

- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropReverseTest.kt` (in `feat/nests-i7-publisher-reconnect`)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/ReconnectingNestsListener.kt:317-465` (`reissuingSubscribe`)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/MoqLiteSession.kt:249,320,393,630` (subscribeId allocation + map mgmt)
- `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md:99-150` (moq-rs 0.10.25 source audit)

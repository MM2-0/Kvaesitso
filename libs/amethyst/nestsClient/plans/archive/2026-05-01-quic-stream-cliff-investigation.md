# QUIC stream cliff against nostrnests.com — investigation plan

> **Status:** shipped — investigation closed PRODUCTION-FIXED via a `:quic` bug fix + `NestMoqLiteBroadcaster` cadence tuning; the `max_latency` knob is wired through the public API.
> _Audited 2026-06-30._

**⚠️ Recommendation 2 (cadence tuning) was overridden 4 days
later.** Subsequent two-phone production tests on
`claude/fix-nests-audio-receiver-HCgOY` (commit `a36ccb569`,
2026-05-05) showed `framesPerGroup = 5` itself cliffs after ~13 s
on the same production deployment, just slower than `framesPerGroup
= 1`'s 3 s. Production now defaults to `framesPerGroup = 50`
(1 stream/sec). The interop tests still pin `5` because the local
`moq-relay 0.10.25 --auth-public ""` minimal setup hits a *different*
cliff (per-stream byte volume) at 50.

Both values are correct in their own environments. See
`nestsClient/plans/2026-05-07-framespergroup-reconciliation.md`
for the full reconciliation. A planned re-run of the HCgOY field
tests against the current production deployment is queued in
`nestsClient/plans/2026-05-07-framespergroup-production-rerun.md`
to settle whether the two rigs can converge.

**Status: PRODUCTION-FIXED via two-layer fix.** The investigation closed
with one true bug fix in `:quic` and one tuning change in
`NestMoqLiteBroadcaster`. A third layer — exposing moq-lite's
`max_latency` knob to listener callers — is now wired through the
public API so future tuning can happen at the call site without
touching the listener internals.

  1. **Bug fix.** `:quic` now emits `MAX_STREAMS_UNI` extensions to
     widen the listener's peer-initiated stream-id cap (commit
     `d391ae1d`). Closes the original 99-frame cliff for short
     broadcasts. Pre-fix, `:quic` only ever *parsed* inbound
     `MaxStreamsFrame` — it never sent the outbound counterpart, so the
     peer's stream-id cap stayed at our [QuicConnectionConfig.initialMaxStreamsUni]
     default (`100`) for the lifetime of the connection. The relay
     forwards each broadcast group as a fresh peer-initiated uni
     stream, so any broadcast longer than 100 frames silently truncated
     at the listener.

  2. **Cadence tuning.** `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP = 5`
     packs five Opus frames per moq-lite group, dropping client
     uni-stream creation from 50/sec to 10/sec — comfortably under the
     production nostrnests relay's sustained per-subscriber forward
     ceiling of ~40 streams/sec, which the moq-lite spec explicitly
     leaves to relay implementations to size. Closes the long-broadcast
     residual where the relay's per-subscriber queue would overflow
     after 6–14 seconds of continuous push and silently terminate
     forwarding to that subscriber.

  3. **API knob.** [NestsListener.subscribeSpeaker] now takes a
     `maxLatencyMs: Long = 0L` parameter, plumbed into the moq-lite
     SUBSCRIBE frame's `max_latency` field. `0` (default) preserves
     prior behavior — the relay falls back to its own `MAX_GROUP_AGE
     = 30 s`. Low-latency live audio callers can set e.g. `500L` so
     the listener prefers fresh frames over stale buffered ones during
     transient relay backpressure. Per the moq-lite spec, the relay
     aggregates `max_latency` across subscribers with `0` winning, so
     a single legacy listener wedges everyone — but for a fully-tuned
     deployment this is the on-wire knob the spec gives us.

Listener-side flow-control snapshots in the round-2 sweep confirmed
the residual was strictly relay-side: every uni stream the relay
opened to the listener delivered cleanly to the application
(`peerInitiatedUni == received + 1`, where `+1` is the WT control
stream); `pendingBytes == 0`; cap headroom unused. The relay simply
stopped opening new streams to the listener once its per-subscriber
buffer overflowed. Reducing the stream-creation rate to 10/sec keeps
the relay's queue from ever filling.

Late-join latency cost from `framesPerGroup = 5`: ≤ 100 ms initial
audio gap (one group boundary) instead of ≤ 20 ms with `framesPerGroup
= 1`. Imperceptible for live audio.

## Decisions vs. bugs — the spec audit

The earlier draft of this plan called the relay's behavior a stack of
"upstream bugs". A spec audit (moq-transport-17, moq-lite Lite-03,
RFC 9000) showed that's not accurate. The honest framing:

- **Our `:quic` not emitting `MAX_STREAMS_UNI` was a real bug** —
  RFC 9000 §4.6 / §19.11 require the receiver to extend the peer's
  stream-id cap by sending `MAX_STREAMS` once existing streams have
  been consumed. Not optional. Fixed in `d391ae1d`.

- **The relay's per-subscriber forward queue policy is a deliberate
  architectural decision in a spec gap, not a bug.** The moq-lite
  Lite-03 spec's "Congestion" section explicitly says: *"MoQ puts
  each subscriber in control"* via the per-subscribe `max_latency`
  field. The relay is **expected** to keep streams flowing while
  groups are within tolerance, and to evict groups (not subscribers)
  when they age past tolerance. moq-rs's choices —
  - unbounded per-track group queue with age-only eviction
    (`MAX_GROUP_AGE = 30 s` default),
  - `serve_group()` blocking on `open_uni().await` with no per-task
    timeout,
  - `FuturesUnordered` task pool for forward tasks (unbounded),
  - no proactive lagging-consumer detection / RESET / STOP_SENDING
  — are all spec-permitted (`MAY`-level) implementation choices. They
  collectively *can* starve a subscriber whose downstream is
  temporarily slow, but the spec puts the burden of fixing that on
  the subscriber via `max_latency`, not on the relay. Calling these
  "bugs" in the prior draft was incorrect.

- **Our prior shipped behavior of `max_latency = 0` (= unlimited) on
  every SUBSCRIBE was the missing piece.** With `0`, the relay was
  doing exactly what the spec asks: hold groups for up to its
  `MAX_GROUP_AGE` default, then evict. We never told it we cared about
  freshness over completeness. The new `subscribeSpeaker(pubkey,
  maxLatencyMs = …)` parameter lets a caller fix that — though the
  defaults stay at `0` for back-compat with the JS reference watcher.

- **Worth filing upstream:** an issue at `kixelated/moq` *suggesting*
  (not asserting as a bug) that the relay expose its per-subscriber
  queue depth as a config knob, and that `serve_group()` `open_uni()`
  take a deadline derived from the active subscriber's smallest
  `max_latency`. This is a feature request, not a defect report.

## Source audit (moq-rs 0.10.25)

Read against the version production nostrnests is running
(`moq-relay 0.10.25-5063dbfe`):

1. **Per-track group queue is unbounded.**
   `moq/rs/moq-lite/src/model/track.rs:69-90` —
   ```rust
   groups: VecDeque<Option<(GroupProducer, tokio::time::Instant)>>,
   ```
   Inserts are unconditional (subscriber.rs:363-393); only eviction
   is age-based at `MAX_GROUP_AGE = 30 s` (track.rs:29). When a
   subscriber falls behind, groups pile up indefinitely until they age
   out 30 s later — which is what the spec wants if `max_latency = 0`.

2. **`serve_group` blocks on `open_uni().await` with no timeout.**
   `moq/rs/moq-lite/src/lite/publisher.rs:317-379` —
   ```rust
   let stream = session.open_uni().await.map_err(Error::from_transport)?;
   ```
   No `select!` against a deadline. If the subscriber's Quinn CWND
   has collapsed or its advertised `MAX_STREAMS_UNI` is exhausted,
   this `await` blocks the task indefinitely. Spec-permitted but
   user-hostile under sustained load.

3. **Unbounded task pool feeding the awaits.**
   The publisher pushes blocked `serve_group` tasks into a
   `FuturesUnordered` (publisher.rs:325, 346). The receive loop (line
   334) keeps spawning more serve tasks as upstream groups arrive.
   No backpressure path back to the publisher to slow upstream
   ingestion. Combined with (1)+(2), once any in-flight write blocks,
   queued tasks pile up and the per-track queue ages everything out.

4. **`max_concurrent_uni_streams = 10000`** in
   `moq/rs/moq-native/src/quinn.rs:30-33` (override of Quinn's
   default 1024). High enough that we never hit the connection's
   stream-id cap on our listener side at typical audio rates —
   confirmed by our `peerMaxStreamsUniNow=10000` snapshots — so the
   stall is *not* about Quinn's stream-concurrency limit.

5. **No proactive "lagging consumer" detection.** The relay never
   gives up on a slow subscriber via RESET / STOP_SENDING. From the
   subscriber's POV the subscription stays "active",
   `peerInitiatedUni` is frozen, and the connection stays alive —
   exactly the symptom we observed.

These are architectural choices, not spec violations. The
`framesPerGroup = 5` mitigation works because it cuts the upstream
publisher rate to ~10 streams/sec — slow enough that even a
temporarily-stalled subscriber's queue drains before any of (1)/(2)/(3)
accumulate beyond recovery.

## What `flowControlSnapshot` proved (the smoking gun)

After wiring [QuicConnection.flowControlSnapshot] into [SendTraceScenario],
running `sweep_frames_200` against production produced:

```
fc-pre:        peerInitMaxData=4611686018427387903   peerInitMaxStreamDataUni=1250000
               peerInitMaxStreamsUni=10000           sendCredit=4611686018427387903
               consumed=786                          peerMaxStreamsUniNow=10000
               nextLocalUniIdx=1                     pendingBytes=0   pendingStreams=0/4
fc-post-pump:  sendCredit=4611686018427387903        consumed=18729
               peerMaxStreamsUniNow=10000            nextLocalUniIdx=201
               pendingBytes=0                        pendingStreams=0/205
fc-post-grace: (identical to fc-post-pump)
sub[0]:        received=99/200                       firstSentButLost=99
```

This rules out every speaker-side hypothesis at once:

- **`peerInitMaxData = 2⁶²−1`** ⇒ ~5 EB connection-level send credit;
  we used **18 KB**. Not a connection-level flow-control wedge.
- **`peerInitMaxStreamsUni = 10000`, `nextLocalUniIdx = 201`** ⇒
  speaker opened all 200 broadcast streams + 1 control stream, well
  under cap. Not a peer-granted stream-id wedge.
- **`pendingBytes = 0`, `pendingStreams = 0/205`** ⇒ no bytes are
  stuck in any stream's send buffer; everything we wrote made it past
  the writer.
- **`consumed` is identical between post-pump and post-grace** ⇒ we
  weren't even *trying* to send more after the pump finished.

So the speaker → relay path was healthy. The 99-frame ceiling on the
listener side had to be on the **relay → listener** half of the
connection.

Listener-side, our [QuicConnection] advertises `initial_max_streams_uni
= 100` at handshake. The relay can open up to 100 uni streams to us
*for the lifetime of the connection* unless we send `MAX_STREAMS_UNI(N)`
to extend it. Grepping `:quic` for `MaxStreamsFrame` showed it was only
ever *parsed inbound* — no outbound emission. So the peer's stream-id
allowance was capped at 100 forever. Production validation:
`sweep_frames_200` went from `received=99/200` (pre-fix) to
`received=200/200` (post-fix).

## Sweep evidence (production, before mitigation)

`NostrnestsProdAudioTransmissionTest` (commit `2da18859`-era, before
any QUIC changes), one row per scenario:

| Scenario | streams opened | `received` | cliff |
|---|---|---|---|
| `sweep-frames50` | 50 | 50/50 | none ✅ |
| `sweep-baseline` 100 frames | 100 | 99/100 | last 1 |
| any cadence (5/10/40/80/200 ms) at 100 frames | 100 | 99/100 | last 1 |
| any payload (80 B / 1 KB / 4 KB) at 100 frames | 100 | 99/100 | last 1 |
| `sweep-frames200` | 200 | **99**/200 | hard at 99 |
| `sweep-frames400` | 400 | **99**/400 | hard at 99 |
| `sweep-30s` (1500 frames) | 1500 | **99**/1500 | hard at 99 |
| `sweep-120s` (6000 frames) | 6000 | 52/6000 | hard at 52 |
| `sweep-burst` (cadence 0) | 100 | 65/100 | hard at 65 |
| `sweep-payload-16kb` 100 frames × 16 KB | 100 | 49/100 | hard at 49 |
| **`sweep-frames-per-group-5`** | **20** groups | **100/100** | none ✅ |
| **`sweep-frames-per-group-20`** | **5** groups | **100/100** | none ✅ |
| **`sweep-frames-per-group-all`** | **1** group | **100/100** | none ✅ |

The signal is unambiguous: **fewer streams → no loss, regardless of
frame count, payload, or duration**. The cliff scaled with stream-
opening rate, not byte volume or wall-clock time.

## Sweep evidence (after MAX_STREAMS_UNI fix)

Full prod sweep (all 27 scenarios, commit `c0269859`):

**Now perfect (100% received):** `baseline 100×20ms`, `frames200`,
`frames50`, `1kb`, `4kb`, `cad10`, `cad40`, `cad80`, `cad200`,
`2subs` (both subs), `slowconsumer`, `fpg5`, `fpg20`, `fpg-all`.
14 of 27 scenarios.

**Improved but still partial (residual relay-side loss):**

| Scenario | Pre-fix | Post-fix | Pattern |
|---|---|---|---|
| `frames400` | 99/400 | 368/400 | last 32 frames lost (tail) |
| `30s` (1500 frames) | 99/1500 | 653/1500 | cliffed at frame 653 mid-pump |
| `120s` (6000 frames) | 52/6000 | 61/6000 | cliffed at frame 61, t≈2 s |
| `burst` cadence=0 | 65/100 | 96/100 | last 4 lost (tail) |
| `pause` 50+5 s+50 | 99/100 | 32/100 | regressed; pause kills it |
| `cad5` 100×5 ms | 99/100 | 54/100 | regressed (faster burst) |
| `3subs` sub[2] | varied | 77/100 | one sub lags |

The cliff is no longer at "exactly 99 streams" — it's now timing/load-
sensitive. The speaker side remains pristine (`fc-post-pump`:
`consumed > 0`, `pendingBytes = 0`, `nextLocalUniIdx` matches frame
count) — the loss is on the relay→listener path. Closing the residual
required the `framesPerGroup = 5` cadence change.

## Round 2 — chasing the residual (preserved for context)

Three test-side changes ruled out collector-side biases before
concluding the residual was relay-side:

### 2a. Test-side stdout serialisation

`SendTraceScenario.Scenario.verbosePerFrame` defaulted to `true`,
which logged a per-frame `tx i=…` and `rx[idx] gid=…` line via
`InteropDebug.checkpoint`. Under JUnit's stdout capture, that's a
synchronous write per event. At 50 frames/sec sustained the capture
thread can serialise the receive coroutine and starve the QUIC read
loop, biasing the `received` count downward. **Default flipped to
`false`**; specific tests opt in for debugging.

### 2b. Test-side `CopyOnWriteArrayList` add cost

The collector accumulated arrivals in a `CopyOnWriteArrayList`, which
is O(N) per add. For 1500-frame runs that's ~1.1 M element copies and
~35 MB of memory ops cumulative. Replaced with
`Collections.synchronizedList(ArrayList(capacityHint))` for O(1)
amortized adds.

### 2c. Listener-side kernel UDP receive buffer

`UdpSocket.connect` did not configure `SO_RCVBUF`, so the kernel's
default applied (~200 KB on Linux/macOS). At MTU-sized datagrams
that's room for ~130 packets. Bumped to 4 MiB at socket-bind time
(Linux doubles + clamps via `rmem_max`).

Added lifetime UDP datagram counters (`receivedDatagramCount`,
`receivedByteCount`, `receiveBufferSizeBytes`) to the `UdpSocket`
expect surface; the driver wires them into the existing
[QuicConnection.flowControlSnapshot] via a `udpStatsSupplier` hook.
The `fc-pre / fc-post-pump / fc-post-grace` lines now include
`udpDatagrams=…  udpBytes=…  udpRcvBuf=…` so future sweeps can
correlate "frames missing on subscription" against "datagrams the
kernel actually delivered".

None of these fully closed the residual — the cliff after round-2 was
still relay-side, finally addressed by `framesPerGroup = 5`.

## Symptom (preserved for context)

When the speaker pumps Opus frames at the production 20 ms cadence
and packs **one frame per moq-lite group** (= one client-initiated
QUIC unidirectional stream per Opus frame), the listener stops
receiving frames somewhere around the 99th frame. From there to
end-of-broadcast, **`MoqLitePublisherHandle.send` keeps returning
`true` for every subsequent frame**, so the application has no signal
that anything is wrong; the audio simply cuts out a few seconds in.

The bug is invisible to:

- `NestMoqLiteBroadcaster`'s outer `runCatching {…}.onFailure {…}`
  (no exception is thrown on the silent-loss path).
- `MoqLitePublisherHandle.send`'s return value (always `true` past
  the cliff).
- `QuicConnection`'s peer-cap check.

**Reproduces against:** `https://moq.nostrnests.com:4443` (production
relay).

## Reproducing / verifying

```bash
./gradlew :nestsClient:jvmTest \
  --tests com.vitorpamplona.nestsclient.interop.NostrnestsProdAudioTransmissionTest \
  -DnestsProd=true -DnestsInteropDebug=true --rerun-tasks
grep -E "sub\[|missing=" \
  nestsClient/build/test-results/jvmTest/TEST-com.vitorpamplona.nestsclient.interop.NostrnestsProdAudioTransmissionTest.xml \
  | sort
```

Acceptance criterion: every sweep row showing `received=N/N
missing=[]`, except the explicit late-join scenarios
(`sweep_late_subscribe_after_25` / `_after_50`) which legitimately
report `received < N` due to from-latest semantics.

## Open follow-ups

1. **File a feature request at `kixelated/moq`** describing the
   per-subscriber forward-queue cliff and proposing (a) per-deployment
   tuning of the unbounded `FuturesUnordered` task pool, and (b) a
   deadline on `serve_group()`'s `open_uni().await` derived from the
   active subscriber's smallest `max_latency`. This is a feature
   request, not a bug report.

2. **Surface `maxLatencyMs` in the audio-room VM/UI** if a future
   product requirement wants per-listener freshness preference. The
   listener wire path now supports it; nothing in the current
   `NestViewModel` consumes it.

3. **Reset `DEFAULT_FRAMES_PER_GROUP` back to 1** if a future relay
   version ships either a config knob or a fix for the per-subscriber
   forward starvation. The 100 ms late-join initial gap is the only
   remaining audio-quality tradeoff from the current mitigation.

   **Update 2026-05-05:** the value did NOT stay at `5` — HCgOY field
   tests overrode it to `50`. See the banner at the top of this
   doc. The "reset to 1" follow-up still applies in spirit (use the
   smallest value that doesn't cliff) but the right value is now
   data-dependent on whichever production deployment is being
   targeted. Tracked in
   `nestsClient/plans/2026-05-07-framespergroup-production-rerun.md`.

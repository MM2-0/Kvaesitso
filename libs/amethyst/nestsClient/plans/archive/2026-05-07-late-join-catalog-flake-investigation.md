# `late_join_listener_still_decodes_tail` catalog-cancelled flake investigation

> **Status:** shipped — closed by merging `origin/main`'s 5 `:quic` commits; 5/5 sweep / 55/55 tests pass post-merge.
> _Audited 2026-06-30._

**Status: ✅ closed by merging `origin/main` (5 `:quic` commits, see
`2026-05-07-moq-relay-routing-investigation.md` § Closure). 5/5
sweep BUILD SUCCESSFUL, 55/55 tests pass post-merge. Pre-merge
baseline on the same branch: 3 fail / 5 sweeps, all here.**

## 2026-05-07 update: relay-side hypothesis disproven

The "smoking gun" trace below (speaker logs an `ANNOUNCE inbound`
but no `SUBSCRIBE inbound` for the failing broadcast) was
**correct as a description of the symptom** but **wrong about the
cause**. With the relay-side trace now captured (Step 1 of the
routing-investigation plan), we can see:

- The relay DID receive the listener's wire SUBSCRIBE
  (`subscribed started` on conn{id=1}).
- The relay DID open a peer-initiated bidi to the speaker and
  encode `Subscribe { id:0, track:"catalog.json" }` onto it
  (`subscribe started`, `encoding self=Subscribe` on conn{id=0}).
- The speaker NEVER logs `SUBSCRIBE inbound id=0` — the bidi
  doesn't reach `MoqLiteSession.handleInboundBidi`.

The same speaker connection HAS handled an earlier peer-bidi (the
relay's AnnounceInterest at T=0) correctly. So the failure isn't a
permanent dead pump; it's a per-bidi loss that affects bidi #2
some 40-60 % of the time when bidi #2 arrives ~2 s after bidi #1.

Pivot: action moved from "investigate moq-relay" to "investigate
`:quic` peer-bidi surfacing under delayed arrival" in the
routing-investigation plan, which reframes the closure-roadmap
Priority 1 actor accordingly.

The flake also affects four browser-tier scenarios after the
Browser I7 work landed:
`chromium_listener_late_join_still_decodes_tail`,
`chromium_publisher_baseline_kotlin_listener_decodes`,
`chromium_publisher_reconnect_kotlin_listener_recovers`, and
intermittently `chromium_listener_long_broadcast_60s_tone_440`.
Browser scenarios soft-pass listener-side assertions on 0-frame
outcomes; hard floors planned in
`2026-05-07-tighten-cross-stack-assertions.md`.

`HangInteropTest.late_join_listener_still_decodes_tail`,
`packet_loss_1pct_does_not_kill_audio`,
`long_broadcast_60s_tone_round_trips`, and
`amethyst_speaker_to_hang_listener_stereo_440_660` intermittently
fail with `hang-listen` exiting non-zero on a `subscribe error`
during catalog read. Pre-fix flake rate: 5/5 fail in a sweep.
After three layered mitigations: ~2-3/5 fail. The remaining flake
is in moq-relay 0.10.x's per-broadcast announce → subscribe-pump
setup race; the test-side mitigations have hit diminishing returns.

## Pre-fix root cause: moq-rs cancel cascade

The previous hang-listen catalog-read shape:

```rust
for attempt in 0..3 {
    let catalog_track = broadcast.subscribe_track(...)?;
    let mut catalog = hang::CatalogConsumer::new(catalog_track);
    match tokio::time::timeout(Duration::from_secs(2), catalog.next()).await { ... }
    // catalog_track + catalog drop at iteration boundary
}
```

is broken on moq-rs 0.10.x. The flow:

1. Attempt 0: `subscribe_track` creates a TrackConsumer. Wire
   subscribe id=0 fires.
2. Speaker's `setOnNewSubscriber` hook is supposed to write the
   catalog via `send(catalogJson) + endGroup()`. **For some reason
   this doesn't deliver in time** — see "residual root cause" below.
3. 2 s timeout fires. Loop iteration ends. `catalog_track` drops.
4. moq-rs sees `track.unused()` resolve (no consumers left), aborts
   the wire subscribe with `Error::Cancel`.
5. **`Error::Cancel` maps to wire stream-reset code 0** per
   `moq-lite/src/error.rs:96-105`.
6. Attempt 1: `subscribe_track(catalog.json)` returns a consumer
   whose internal state is already in the just-cancelled state.
   `.next()` resolves immediately with `cancelled`.
7. Attempt 2+: subscribe_track itself returns `Err(cancelled)`.
8. Loop bails; `Error: subscribe catalog. cancelled.`

**Fix (commit `8cc7cbd42`):** hold ONE subscription open for the
full 10 s budget; inner timeouts on `.next()` poll for the first
group; outer timeout caps the total wait. Code is in
`hang-interop/hang-listen/src/main.rs`.

This eliminates the cancel-cascade failure mode. 2 of 5 sweep runs
post-fix go all-green; 3 hit the residual described next.

## Residual root cause (unidentified)

Same test, post-fix, fresh repro from sweep run 4:

```
12:35:45.333625  subscribe started id=0 catalog.json
                 (no further logs from hang-listen for 2.94 s)
12:35:48.267341  subscribe error id=0 err=remote error: code=0
                 Error: catalog read | moq lite error: cancelled
```

The single, long-lived subscribe is cancelled by the **peer** (relay
or speaker) ~3 s after start. Wallclock alignment:

- Speaker started broadcasting at T=0
- `delay(listenerLateJoinDelayMs = 2_000)` → T=2 s
- hang-listen connects + subscribes → T=2.05 s
- Speaker's broadcast window ends at T=5 s
  (helper does `delay(speakerSeconds * 1_000 - listenerLateJoinDelayMs)`
   = `delay(3_000)` AFTER hang-listen starts)
- Listener-observed cancel at speaker-T+~5 s = ~3 s after subscribe

So the cancel coincides with the speaker tearing down. The catalog
data **never arrived during the 3 s subscribe window**, despite the
speaker presumably having the hook installed AND the inbound
SUBSCRIBE arriving normally.

## Ruled out

- **Hook installation race.** `MoqLiteNestsSpeaker.setOnNewSubscriber`
  is called BEFORE the speaker transitions to `Broadcasting` state
  (`MoqLiteNestsSpeaker.kt:176-182`). Listener subscribes 2 s
  later — hook is definitively installed.
- **Stale `inboundSubs`.** `@BeforeTest` calls `resetShared()` which
  restarts the moq-relay subprocess. Speaker session is fresh per
  test (new `pumpScope` + fresh `MoqLiteSession`). No cross-test
  state leak.
- **Hook captured-but-null.** `registerInboundSubscription` reads
  `onNewSubscriberHook` inside the gate AFTER the sub is added.
  By T=2 s the hook is non-null.
- **`inboundSubs.isEmpty()` race in `send()`.** Hook is launched
  AFTER `inboundSubs += sub` inside the same gate. The hook's
  `send()` re-acquires the gate; sees `inboundSubs` non-empty.
- **`MAX_STREAMS_UNI` exhaustion at speaker.** Catalog uni stream
  is one stream per subscriber; cap is 10000.
- **Idle timeout.** Quinn's default (per moq-native) is 30 s, not 3.
- **Audio publisher's `onTerminalFailure` firing.** Only triggered
  by `MAX_CONSECUTIVE_SEND_ERRORS` thrown errors, not the
  no-subscribers `return false` path that the audio publisher
  takes during the 2 s warmup.

## Plausible remaining hypotheses

1. **Relay-side per-track state race.** The relay's downstream
   subscriber (forwarding to hang-listen) is created when
   hang-listen's SUBSCRIBE arrives. The relay's upstream subscriber
   (subscribed-on-speaker) might be created on-demand and might race
   with the speaker's hook firing. If the relay's upstream consumer
   isn't fully alive by the time the speaker's uni stream arrives at
   the relay, the relay drops the uni without forwarding.
2. **Catalog uni stream priority/scheduling.** The audio publisher
   (running silent — `inboundSubs.isEmpty() → return false`) could
   somehow contend with the catalog publisher's uni-stream open via
   the shared `transport.openUniStream()`, even though they're
   separate publishers with separate gates. Less likely.
3. **moq-rs CLIENT-side `subscribe_track` returning a stale
   consumer.** Even on the first call, if the broadcast's track-
   producer pool has ANY residual entry from an earlier test (despite
   `resetShared()`), the consumer might be born already-cancelled.
   The 2/5 pass rate suggests there's a timing component.
4. **`Track.unused()` racing with the long subscribe.** The hang
   crate's `CatalogConsumer::new(track)` may temporarily drop an
   internal handle, causing a brief `unused()` flicker that aborts
   the upstream subscription before the speaker's data arrives.

## What would confirm a hypothesis

1. **Speaker-side log instrumentation.** Add `Log.d("NestTx") {
   "catalog hook fired for subId=$id" }` inside the
   `setOnNewSubscriber` lambda; `"catalog send returned $result for
   subId=$id"` after each `send()`; `"catalog endGroup completed for
   subId=$id"`. Run the failing test under `--info` Gradle output
   to see if the hook fires + writes succeed on the SPEAKER side.
   If yes → the data is being written but the listener isn't
   receiving it (relay-side issue).
2. **Relay-side log instrumentation.** Boot moq-relay with
   `RUST_LOG=moq_relay=debug,moq_lite=debug` and capture per-test
   stderr to a file. Look for "subscribe started" / "serving group" /
   "subscribe cancelled" timing on the relay side. Cross-reference
   with hang-listen's view.
3. **Listener-side QUIC-level capture.** Wrap hang-listen's UDP
   socket via `udp-loss-shim` modified to packet-log instead of drop.
   See exactly which streams open + close.

## Mitigations attempted (in order)

1. **Per-method `resetShared()`** (`706ccda67`) — kills the relay
   subprocess between test methods. Closes a moq-rs accumulated-
   state class but the catalog-cancel pattern persists.
2. **hang-listen single long-lived subscribe** (`8cc7cbd42`) —
   replaces the create-drop-recreate retry shape with one
   subscribe held for the full 10 s read budget. Eliminates the
   moq-rs `Error::Cancel` cascade. **5/5 fail → ~2-3/5 pass.**
3. **Speaker warmup bump 150 ms → 600 ms** (`00f6cba31`) — gave
   the relay more time to register the speaker's broadcast in
   its origin before the listener subscribed. **NET NEGATIVE,
   reverted in `1ddf4967c`** — same failure pattern AND ate
   into the listener's catalog-read window (5 s broadcast minus
   600 ms warmup leaves ~4.4 s instead of 4.85 s).
4. **hang-listen 250 ms post-`origin.announced()` sleep**
   (`207057374`) — gave the relay time to fully prime its
   per-broadcast upstream-subscribe pump. **NET NEGATIVE,
   reverted in `9b8b5692b`** — combined with #3 produced 0/5
   sweep pass (worse than single-subscribe-fix-alone's 2/5)
   because the cumulative ~850 ms of pre-subscribe delay
   shrank the catalog-read window into the speaker tear-down
   region.

Lesson: the failure window for the broken-routing case is
~3 seconds (until the speaker tears down at end of broadcast).
ANY pre-subscribe delay shrinks the available retry budget on
the listener side. Mitigations should NOT add delays.

## Smoking gun (from speaker stderr trace)

For broadcasts that fail (`10d4b6f2…`, `c75e2648…`, `f1be27ef…`),
the speaker-side `Log.d("NestTx")` trace shows ONE event for the
broadcast suffix:

    12:53:32.293 ANNOUNCE inbound prefix='' → emitted Active suffix='f1be27ef…'

…and then NOTHING for the entire 10 s catalog-read window. No
`SUBSCRIBE inbound`, no `openGroupStream`. The audio publisher
keeps logging `send returning false — no inboundSubs` at 50 fps
until hang-listen times out. Meanwhile hang-listen's moq-rs client
is logging `subscribe started id=0 catalog.json` and waiting.

**Interpretation:** the relay accepts the listener's wire
SUBSCRIBE on the downstream connection, BUT does not open an
upstream SUBSCRIBE bidi to the speaker. The two sides are
disconnected; nothing the test code can fix from above.

For broadcasts that succeed (`688e130b…`, `6e577e5f…`), the same
trace shows:

    12:58:10.334  ANNOUNCE inbound prefix='' → emitted Active suffix='6e577e5f…'
    12:58:11.246  SUBSCRIBE inbound id=0 broadcast='6e577e5f…' track='catalog.json'
    12:58:11.246  SUBSCRIBE registered id=0 …
    12:58:11.247  openGroupStream subId=0 seq=0
    …

The relay successfully forwards the upstream SUBSCRIBE. It's a
binary "the relay does or does not forward" — there's no partial
state.

## Conclusion

The flake is **moq-relay 0.10.x's relay-side per-broadcast
forward-subscribe routing**, not anything the test or speaker can
mitigate from outside. Some component of the relay's
`Origin::announced()` → `broadcast.subscribe_track(...)` →
upstream subscribe pump is set up asynchronously and intermittently
fails to wire up the upstream subscribe.

Possible next steps if this matters more:

1. **Boot the relay with `RUST_LOG=moq_relay=trace,moq_lite=trace`**
   under the test harness (currently `RUST_LOG=info`); capture per-
   test relay stderr to a tempfile; check whether the relay logs
   the upstream subscribe attempt for the failing broadcast suffix.
2. **File an upstream issue at `kixelated/moq`** with this
   reproducer (HangInteropTest 5x sweep, ~40-60% flake under load)
   citing the smoking-gun trace pair above.
3. **Stop pinning to `moq-relay 0.10.25`** and try the next minor
   release — maybe the race has been fixed upstream since.

Currently rejected: bumping `speakerSeconds` to 8 s and changing
the test's threshold. That masks a real bug; the failure mode is
worth surfacing.

## Files referenced

- `nestsClient/tests/hang-interop/hang-listen/src/main.rs:160-220`
  (post-fix catalog read shape)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/MoqLiteNestsSpeaker.kt:170-181`
  (`setOnNewSubscriber` hook installation)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/MoqLiteSession.kt:1167-1192`
  (`registerInboundSubscription` + hook-launch path)
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropTest.kt:170-180`
  (`late_join_listener_still_decodes_tail` scenario)
- Pre/post sweep results: `0/5 → 2/5` over 10 total sweep runs.

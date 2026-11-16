# framesPerGroup reconciliation: cliff plan vs. HCgOY field tests

> **Status:** shipped — documentation closing the test-pin (5) vs production-default (50) contradiction; both are correct for their own relay cliffs, no code change.
> _Audited 2026-06-30._

**Status: documentation, no production code change recommended.** The
investigation closes with: both values are correct in their own
environments. The interop test pin (`5`) and production default
(`50`) are tuned for different cliffs in the same `moq-relay 0.10.25`
binary. Reconciling onto a single value would require changes outside
this codebase.

## The contradiction

Two plans on this branch's history reach opposite conclusions about
`NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP`:

| Plan | Value | Evidence |
|---|---|---|
| `2026-05-01-quic-stream-cliff-investigation.md` | `5` (then-set in commit `85691cce2`) | Sweep tests against `https://moq.nostrnests.com:4443` showed `framesPerGroup = 5` (10 streams/sec) "comfortably under the production nostrnests relay's sustained per-subscriber forward ceiling of ~40 streams/sec". |
| HCgOY commit `a36ccb569` (2026-05-05, currently in `main`) | `50` | Two-phone production logs at `6e4df4a` showed `framesPerGroup = 5` itself cliffs after ~13 s of streaming. Bumped to `50` (1 stream/sec) where the relay's queue does not measurably fill. |

The cliff investigation called the fix `5` and labelled itself
"PRODUCTION-FIXED". Four days later, two-phone field tests on the
same relay deployment showed `5` cliffs too — just slower. `50`
overrides the cliff plan's recommendation.

## Why the tests show different behavior

T16's `HangInteropTest.long_broadcast_60s_tone_round_trips` runs 60 s
at `framesPerGroup = 5` and **passes**. Per HCgOY's cliff table, that
should fail at ~13 s. Yet locally it doesn't. This is consistent
with the cliff being load-dependent, not just rate-dependent:

| Local interop test | Production deployment |
|---|---|
| Loopback (127.0.0.1), zero RTT | Real internet, 40-200 ms RTT |
| Loss-free (or 1 % via `udp-loss-shim` in I9) | Variable real loss |
| Single subscriber | 1-N subscribers |
| Quinn CWND stable | CWND can transiently collapse |
| `MAX_STREAMS_UNI` cap = 10000, never approached | Same cap, but stream-id consumption higher under multi-subscriber |
| `serve_group` task pool drains at line rate | Task pool backs up when any `open_uni().await` blocks |

Per the cliff plan's source audit (moq-rs 0.10.25):

> 2. `serve_group` blocks on `open_uni().await` with no timeout. If
>    the subscriber's Quinn CWND has collapsed or its advertised
>    `MAX_STREAMS_UNI` is exhausted, this `await` blocks the task
>    indefinitely.
> 3. Unbounded task pool feeding the awaits. The publisher pushes
>    blocked `serve_group` tasks into a `FuturesUnordered`. No
>    backpressure path back to upstream.

This is a "head-of-line block" story. In the local interop env the
pre-conditions (CWND collapse, transient stalls) effectively never
fire. In production they fire intermittently, and once one
`serve_group` task is parked, every subsequent group at the
publisher's rate piles into the task pool until everything ages out
at `MAX_GROUP_AGE = 30 s`.

So:

- **Production cliff** (need `framesPerGroup = 50`): per-stream
  *rate* — `serve_group` task pool's tolerance for any blocked
  `open_uni().await`. Slower stream creation gives the pool time to
  drain between any individual stall.
- **Local interop cliff** (need `framesPerGroup = 5`): per-stream
  *byte volume* — moq-relay 0.10.25's per-subscriber forward buffer
  holds the data side of large groups. With `framesPerGroup = 50`
  on loopback the relay forwards the `Group` control header but the
  frame payload never reaches the listener. (Reproduced cleanly in
  this branch's `KotlinSpeakerKotlinListenerThroughNativeRelayTest`
  — same Kotlin↔Kotlin path through the same relay.)

These cliffs are NOT contradictory at the protocol level. They are
two distinct code paths inside `moq-relay 0.10.25` triggered by two
different traffic shapes.

## Why no single value works for both

| `framesPerGroup` | Local interop | Production |
|---|---|---|
| `5` | ✅ passes | ❌ cliffs at ~13 s (HCgOY) |
| `50` | ❌ frames never delivered (I1 forward) | ✅ no measurable cliff |
| anything between | not tested | not tested |

There's no value tested in *both* environments that's known to work
in *both*. Suggesting an intermediate value (e.g. `25`) without
empirical evidence in the production deployment is a regression risk
on production audio.

## Options

### A — Status quo (recommended)

- Production: keep `DEFAULT_FRAMES_PER_GROUP = 50`. HCgOY field
  tests vetted this; touching it without re-running those tests is
  unsafe.
- Interop: keep `framesPerGroup = 5` as a per-test pin in
  `HangInteropTest.runSpeakerToHangListen` and the diagnostic
  `KotlinSpeakerKotlinListenerThroughNativeRelayTest`. Document
  that this is an *interop env* value, not a production
  recommendation.
- Add a comment at `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP`
  pointing here so the next reader sees the contradiction
  pre-resolved.

### B — Configure the local relay to mirror production

`moq-relay` has internal limits but they aren't all CLI-flag-tunable
in 0.10.25. The local cliff appears to be the per-subscriber forward
buffer; without an upstream knob, the only way to mirror production's
buffer pressure is to introduce real loss/latency on the loopback
path:

- I9 already drives the speaker through `udp-loss-shim` at 1 % loss.
  Could add a `framesPerGroup = 50, --loss-rate 0.05, duration = 30 s`
  variant that intentionally tries to reproduce the production cliff
  in the local environment. **If reproducible, the test would gate
  any `DEFAULT_FRAMES_PER_GROUP` change.**

This is real but speculative work — needs ~half a day of bisect to
find a loss/latency profile that triggers the production cliff
locally. Out of scope for the T16 closure.

### C — Make `framesPerGroup` per-environment

Add an `AudioBroadcastConfig.framesPerGroup` (alongside the existing
`channelCount` from PR #2755) so call sites can pick. The interop
tests already pass it via the existing `framesPerGroup` constructor
arg on `NestMoqLiteBroadcaster`; the production assembly path
(`NestsConnect.kt:188`) already takes it as a default-50 parameter.
The plumbing is in place — there's just no UI/config surface to
flip it from production code without recompiling.

This option only matters if some production deployment ever wants
the test's value (or vice versa), which there's currently no
demand for.

## Recommendation

**A — status quo**, with one clarifying comment. The two values are
each correct in their own rig; the test pin is documented in
`runSpeakerToHangListen`'s call site, the production default is
documented in `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP`'s
kdoc. Both kdocs cross-reference field-test runs.

The right escalation if this matters again is:

1. Re-run the HCgOY two-phone field tests with `framesPerGroup = 5`
   on whatever the current production deployment is, to confirm the
   cliff still hits at ~13 s in 2026-05+.
2. If it does — file the upstream feature request in
   `2026-05-01-quic-stream-cliff-investigation.md`'s open follow-ups
   list (deadline on `serve_group`'s `open_uni().await` derived from
   the active subscriber's smallest `max_latency`).
3. If the upstream lands a fix, reset both rigs to `1` per cliff
   plan follow-up #3.

## Files referenced

- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/audio/NestMoqLiteBroadcaster.kt:495-543`
- `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`
- `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
- HCgOY commit `a36ccb569` (current `main`)
- Cliff-plan commit `85691cce2`

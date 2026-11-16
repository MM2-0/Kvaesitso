# Plan: re-run HCgOY field tests against current production

> **Status:** queued — specced and pickup-ready but not started; needs prod-rig access to run the two-phone field test that settles the framesPerGroup pin-vs-default question.
> _Audited 2026-06-30._

**Status:** specced — pickup ready (needs prod-rig access).
**Cross-ref:** `nestsClient/plans/2026-05-07-framespergroup-reconciliation.md`
documents the conflict between the cliff plan's value (5) and
HCgOY's value (50). This plan settles which is current truth.

## What we're trying to settle

Production currently runs `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP = 50`
based on HCgOY two-phone field tests at commit `6e4df4a`
(2026-05-05) which observed:

| `framesPerGroup` | streams/sec @ 50 fps | observed cliff window |
|---|---|---|
| 1 | 50 | ~3 s |
| 5 | 10 | ~13 s |
| 10 | 5 | ~16 s |
| 50 | 1 | not reached |
| 100 | 0.5 | never observed |

Interop tests pin `5` because the local `--auth-public ""` minimal
relay setup hits a *different* cliff (per-stream byte volume) at
`framesPerGroup = 50`.

The production deployment may have changed since 2026-05-05:
- nostrnests may have updated their `moq-relay` version
- their resource limits may have shifted
- the upstream `kixelated/moq` may have addressed one or both
  cliffs

We don't know without re-running. **Cliff value at `framesPerGroup
= 5` may now be unbounded — if so, both rigs can converge on 5
and the test pin matches the prod default.**

## Test setup

### Rig A — interop env (already exists)

`./gradlew :nestsClient:jvmTest --tests HangInteropTest -DnestsHangInterop=true`
runs against local `moq-relay 0.10.25 --auth-public "" --tls-generate localhost`.
Long-broadcast scenario `long_broadcast_60s_tone_round_trips` is
the existing 60-second sustained-stream test pinned at
`framesPerGroup = 5`.

To probe other values, parameterize the helper:

```kotlin
// HangInteropTest.kt — runSpeakerToHangListen helper
private suspend fun runSpeakerToHangListen(
    speakerSeconds: Int,
    framesPerGroup: Int = 5,  // ← new parameter
    // ...existing params
): HangListenOutput { ... }
```

Then add scenarios `long_broadcast_60s_framesPerGroup_50` etc.
that pin different values. Expected outcomes today (per the
2026-05-01 cliff plan):
- `framesPerGroup = 5` — passes (current pin)
- `framesPerGroup = 10` — passes
- `framesPerGroup = 50` — fails (per-stream byte volume cliff
  at the local minimal relay)

### Rig B — production deployment (needs maintainer access)

This is the gap. Rerunning the HCgOY two-phone field test pattern
needs:
- Two physical Android devices
- A nostrnests room (production endpoint
  `wss://nostrnests.com/v0/ws` per `NestsConnect.kt`)
- The diagnostic-build of Amethyst that emits the cliff-detector
  trace logs (see commit `6e4df4a`'s logcat run from 18:37:43..18:38:08)

The maintainer should run the same test pattern at:
- `framesPerGroup = 5` (current test value)
- `framesPerGroup = 10`
- `framesPerGroup = 25` (untested, midpoint)
- `framesPerGroup = 50` (current prod value)
- `framesPerGroup = 100` (full group; the cliff plan's
  `fpg-all` reference)

For each, broadcast for 120 s and observe:
- Total streams forwarded by the relay
- Time-to-cliff if any (when the listener-side flow-control
  snapshot stops incrementing `peerInitiatedUni`)
- Audio dropouts (perceptual + sample-count)

## Decision matrix after data lands

| Rig A passes at | Rig B passes at | Decision |
|---|---|---|
| 5, 10 | 5, 10, 25, 50, 100 | Keep prod 50; test pins 5 (current state) |
| 5, 10, 50 | 5, 10, 25, 50, 100 | Both rigs converge → unify on 50, test pin matches prod |
| 5, 10 | 50, 100 only (5 still cliffs) | Current state is correct; document permanently as "two cliffs in one binary" |
| 5 only | 50, 100 only (5 still cliffs) | The two cliffs are real; consider per-environment config |
| 5, 10, 50 | 50, 100 only (5 still cliffs) | Test rig fixed; production cliff still hits at 5. Test pin doesn't catch prod regression — bigger problem. |

The "decision" column drives the production-side change (or
non-change) to `DEFAULT_FRAMES_PER_GROUP`.

## What lands as code

After Rig B data is in:

1. Update kdoc on `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP`
   citing the new run's logcat dates / commit.
2. If the values converge, change the default to match. Update
   the test-side `framesPerGroup = 5` pin to match the new
   default — keep both rigs aligned.
3. If values still diverge, document it explicitly as a known
   environment-dependent value. Consider exposing
   `framesPerGroup` as a per-deployment config (currently only
   exposed as a constructor parameter — wire to a config knob if
   product wants per-deployment tuning).
4. Update `nestsClient/plans/2026-05-07-framespergroup-reconciliation.md`'s
   "Recommendation" section with the data-driven outcome.

## Acceptance criteria

- A logcat dump from Rig B with `framesPerGroup = 5` for ≥ 60 s
  showing whether the cliff still hits at ~13 s.
- Decision logged in the framesPerGroup reconciliation doc.
- If a value change lands, the test-side pin and production
  default agree.

## Out of scope

- The local interop env's per-stream byte cliff at
  `framesPerGroup = 50`. That's a separate thread; addressing it
  would require either a different relay configuration or
  patching moq-relay itself.

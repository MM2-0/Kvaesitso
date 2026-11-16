# Cross-stack interop tests

> Manually invoked. Not part of `build.yml` — see "Why not in CI" below.

Two suites that drive the production Amethyst Kotlin nests stack against
external reference implementations of moq-lite-03:

| Suite | Path | Runs | What it asserts |
|---|---|---|---|
| **`HangInteropTest`** | `nestsClient/tests/hang-interop/` | Rust `hang-listen` / `hang-publish` (kixelated reference) ↔ Amethyst Kotlin via a real `moq-relay` 0.10.x subprocess | Wire-byte capture, FFT peaks on decoded PCM, sample-count floors, mute / hot-swap / packet-loss / late-join / 60 s long broadcast / multi-listener fan-out. |
| **`BrowserInteropTest`** | `nestsClient/tests/browser-interop/` | Headless Chromium running `@moq/lite` + `@moq/hang` via Playwright ↔ Amethyst Kotlin (forward + reverse) | Same scenario family as hang-interop, plus WebCodecs `AudioDecoder` / `AudioEncoder` correctness, ALPN negotiation, browser-side reconnect. |

Coverage matrix: [`nestsClient/plans/archive/2026-05-06-cross-stack-interop-test-gap-matrix.md`][gap]

[gap]: ../plans/archive/2026-05-06-cross-stack-interop-test-gap-matrix.md

## When to run

Run **both suites locally before merging** any change that touches:

- `quartz/.../nip53` (audio rooms event types, catalog wire format)
- `nestsClient/src/.../moq/lite/` (moq-lite session, publisher/subscriber, codec)
- `nestsClient/src/.../audio/` (capture, encoder/decoder, broadcaster, player)
- `nestsClient/src/.../MoqLiteNestsSpeaker.kt` / `MoqLiteNestsListener.kt`
- `nestsClient/src/.../ReconnectingNests*.kt`
- a quic-kmp (`libs.quic`) version bump (WebTransport, packet header protection, key updates, stream demux)
- The hang/browser sidecars themselves

Skip if the change is documentation, UI-only, build-script-only, or otherwise
can't affect wire bytes / decoded audio.

## Quick start

```bash
# Hang-tier (Rust ↔ Kotlin via real moq-relay subprocess)
./gradlew :nestsClient:jvmTest \
    --tests "com.vitorpamplona.nestsclient.interop.native.HangInteropTest" \
    -DnestsHangInterop=true

# Browser-tier (Chromium ↔ Kotlin) — also boots the moq-relay
./gradlew :nestsClient:jvmTest \
    --tests "com.vitorpamplona.nestsclient.interop.native.BrowserInteropTest" \
    -DnestsHangInterop=true \
    -DnestsBrowserInterop=true

# Both together
./gradlew :nestsClient:jvmTest \
    --tests "com.vitorpamplona.nestsclient.interop.native.HangInteropTest" \
    --tests "com.vitorpamplona.nestsclient.interop.native.BrowserInteropTest" \
    -DnestsHangInterop=true -DnestsBrowserInterop=true
```

The opt-in flags (`-DnestsHangInterop=true` / `-DnestsBrowserInterop=true`)
also act as JUnit `Assume` gates — without them, the suites mark every
scenario `skipped` rather than failing on missing prerequisites.

## Prerequisites

| Tool | Why | Install |
|---|---|---|
| **Rust ≥ 1.95** | `moq-relay` 0.10.25's transitive dep `constant_time_eq 0.4.3` requires it | `rustup install 1.95 && rustup default 1.95` |
| **`cargo`** on PATH | Builds the hang-interop sidecars + `cargo install`s `moq-relay`, `moq-token-cli` | Comes with rustup |
| **`bun` ≥ 1.3.x** (browser only) | Bundles the Chromium harness + drives Playwright | `curl -fsSL https://bun.sh/install \| bash` |
| **Playwright Chromium** (browser only) | Headless Chromium for `@moq/lite` + `@moq/hang` | Auto-installed by `interopInstallPlaywrightChromium` Gradle task |

The Gradle build automates everything from there. First run installs and
caches:

- `moq-relay` 0.10.25 + `moq-token-cli` 0.5.23 → `~/.cache/amethyst-nests-interop/hang-interop-cargo/bin/`
- Sidecar release binaries → `nestsClient/tests/hang-interop/target/release/`
- bun's `node_modules` + `dist/` → `nestsClient/tests/browser-interop/`
- Playwright Chromium → `~/.cache/ms-playwright/`

Cold first run: ~10 min hang, ~13 min browser. Cached: ~3-4 min hang,
~5-7 min browser.

## Configuration knobs

| Flag | Default | Purpose |
|---|---|---|
| `-DnestsHangInterop=true` | unset (skip) | Enable HangInteropTest. |
| `-DnestsBrowserInterop=true` | unset (skip) | Enable BrowserInteropTest. Implies `nestsHangInterop` (the browser harness boots the same `moq-relay` subprocess). |
| `-DnestsHangInteropTraceRelay=true` | unset | Per-test moq-relay trace capture. Writes the relay subprocess's combined stdout/stderr (with `RUST_LOG=info,moq_relay=trace,moq_lite=trace,moq_native=debug`) to `nestsClient/build/relay-logs/<methodName>-<seq>-<ts>.log`. Use for debugging suite flakes. |
| `-DnestsHangInteropDiagnostic=true` | unset | Runs the Kotlin↔Kotlin variant gated separately (`KotlinSpeakerKotlinListenerThroughNativeRelayTest`) — useful to bisect wire-format bugs without involving Rust or Chromium. |
| `BUN_BIN`, `NPX_BIN` (env) | autodetected | Override the bun / npx binary path if your install lives outside the agent default (`/root/.bun/bin/bun`). |

## Known limitations

- **`chromium_listener_speaker_hot_swap_does_not_crash`** soft-passes its
  PCM assertion — Chromium's `@moq/lite` 0.2.x doesn't re-attach across
  `Active::Ended → Active`, so the browser captures only ~100 ms post-swap.
  T12 (group sequence carry across hot-swaps) is hard-asserted by the
  hang-tier counterpart.
- **`framesPerGroup`** is pinned to `5` in the test scenarios. Production
  defaults to `50`. The two haven't been reconciled against a single
  multi-rig benchmark — see
  [`framespergroup-reconciliation`](../plans/archive/2026-05-07-framespergroup-reconciliation.md).
- **I7 cycle 2 truncation**: `moq-relay` 0.10.x truncates the second
  cycle of a publisher reconnect at ~1.0 s out of ~2.5 s. Tests assert
  `≥ 2.5 s` floor which the truncated cycle still clears; a future
  upstream fix may let us tighten further.
- **I12 GOAWAY**: not applicable to moq-lite-03; only the IETF
  moq-transport target (currently disabled) would exercise it.

Full open-issues list:
[`2026-05-06-cross-stack-interop-test-results.md` § Pending follow-ups][results].

[results]: ../plans/archive/2026-05-06-cross-stack-interop-test-results.md

## Debugging a flaking scenario

1. Re-run the failing scenario in isolation (no `--tests` filter races):
   ```bash
   ./gradlew :nestsClient:jvmTest \
       --tests "*HangInteropTest.late_join_listener_still_decodes_tail" \
       -DnestsHangInterop=true \
       -DnestsHangInteropTraceRelay=true \
       --rerun-tasks
   ```
2. Inspect `nestsClient/build/relay-logs/<methodName>-*.log` for
   `subscribed started` / `encoding self=Subscribe` /
   `decoded result=SubscribeOk` / `subscribed cancelled` events.
3. Cross-reference with the speaker-side `Log.d("NestTx")` lines
   captured in JUnit XML's `<system-err>`
   (`nestsClient/build/test-results/jvmTest/TEST-*.xml`) by timestamp.
4. The fastest diagnostic loop bypasses Browser/Chromium entirely — use
   the diagnostic test:
   ```bash
   ./gradlew :nestsClient:jvmTest \
       --tests "*KotlinSpeakerKotlinListenerThroughNativeRelayTest" \
       -DnestsHangInteropDiagnostic=true
   ```

Sample pre/post-merge trace pair for the previously-flaking late-join
scenario lives at
[`plans/artefacts/2026-05-07-routing-race-disproven/`](../plans/artefacts/2026-05-07-routing-race-disproven/)
+ [`plans/artefacts/2026-05-07-routing-race-closed-by-merge/`](../plans/artefacts/2026-05-07-routing-race-closed-by-merge/).

## Why not in CI

Both suites are slow on a cold cache (~10–13 min each), and even on a
warm cache the browser tier dominates the critical path at ~5-7 min.
Running them on every PR doubles CI time for changes that don't touch
audio / MoQ / QUIC.

History:

- `21947bc5` re-added both jobs after the T16 closure roadmap closed
  Priority 1 (`:quic` post-handshake bidi-drop fixed via `origin/main`
  merge) and Priority 2 (assertion hardening). 10/10 sweep × 22 tests
  = 220/220 hard-pass on the merged branch.
- A subsequent maintainer review judged the wallclock cost too high
  for the change-pattern (most PRs don't touch the relevant code) and
  removed the jobs.

The suites still run locally per the rules above. If a PR DOES touch
audio / MoQ / QUIC code paths and the author hasn't run them, ask in
review.

## Files

- `hang-interop/REV` — pinned upstream versions (`MOQ_RELAY_VERSION`,
  `MOQ_TOKEN_CLI_VERSION`, `HANG_VERSION`, `MOQ_LITE_VERSION`,
  `MOQ_NATIVE_VERSION`). Bump deliberately.
- `hang-interop/Cargo.toml` + `hang-{listen,publish}/`,
  `udp-loss-shim/` — Rust sidecar workspace.
- `browser-interop/package.json` + `src/` + `playwright.config.ts` —
  bun + Playwright harness.
- `nestsClient/src/jvmTest/kotlin/.../interop/native/`:
  - `NativeMoqRelayHarness.kt` — boots the relay subprocess + captures
    per-test trace.
  - `HangInteropTest.kt`, `HangInteropReverseTest.kt`,
    `HangInteropMultiListenerTest.kt`,
    `KotlinSpeakerKotlinListenerThroughNativeRelayTest.kt` —
    hang-tier scenarios.
  - `BrowserInteropTest.kt`, `PlaywrightDriver.kt` — browser-tier.

## See also

- [`2026-05-06-cross-stack-interop-test.md`](../plans/archive/2026-05-06-cross-stack-interop-test.md)
  — original spec / Definition of Done.
- [`2026-05-07-t16-closure-roadmap.md`](../plans/2026-05-07-t16-closure-roadmap.md)
  — closure roadmap (Priorities 1, 2 closed; Priority 3 deferred).
- [`2026-05-07-cross-stack-interop-ci-gating.md`](../plans/2026-05-07-cross-stack-interop-ci-gating.md)
  — the deferred CI-gating plan (kept around in case the wallclock
  cost calculus changes).

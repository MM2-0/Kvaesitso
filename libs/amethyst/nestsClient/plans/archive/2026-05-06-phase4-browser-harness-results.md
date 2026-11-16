# Plan: Phase 4 (browser harness) — landed results

> **Status:** shipped — phases 4.A-4.C landed; browser harness lives at `nestsClient/tests/browser-interop/` (the 4.D CI job was added then removed per maintainer ask).
> _Audited 2026-06-30._

**Status:** ✅ Phase 4.A (scaffold) + 4.B (Playwright driver) +
4.C (full scenario coverage) all landed. Phase 4.D (CI workflow
job) was originally added in commit `c79a3ffa8` but later removed
per maintainer ask in commit `b94737de7` ("don't add these tests
to the build.yml for now"); see
`2026-05-07-cross-stack-interop-ci-gating.md` for the path to
re-wiring.

**Browser harness location:** `nestsClient/tests/browser-interop/`
(was originally `nestsClient-browser-interop/` at repo root;
moved to mirror `nestsClient/tests/hang-interop/` layout).

**Final scenario coverage:** I1, I2, I3, I4 (stereo), I5
(hot-swap), I7 (Chromium publisher reconnect), I9 (packet loss),
I13 (long broadcast), I14 (WebCodecs warmup × CSD), I15 (ALPN).
See `2026-05-06-cross-stack-interop-test-results.md` for the
full inventory.

Tracks the spec at
`nestsClient/plans/2026-05-06-phase4-browser-harness.md`.

## Where it landed

- New top-level `nestsClient/tests/browser-interop/` workspace:
  - `package.json` pins `@moq/lite@0.2.2`, `@moq/hang@0.2.4`,
    `@moq/watch@0.2.10`, `@moq/publish@0.2.6`, `@playwright/test@1.56.1`.
  - `REV` documents the pinned versions next to the
    `nestsClient/tests/hang-interop/REV`.
  - `src/listen.html` + `src/listen.ts` — Watch path, uses
    `Container.Legacy.Consumer` and WebCodecs `AudioDecoder`
    directly (the published `@moq/hang` 0.2.4 doesn't expose the
    higher-level `Container.Consumer` from upstream HEAD; we wire
    its data path manually).
  - `src/publish.html` + `src/publish.ts` — symmetric publisher
    scaffold for the I4-reverse / I14-decoder-warmup scenarios
    Phase 4.C extension can pick up.
  - `src/server.ts` — bun static + WebSocket back-channel; the
    listener page posts Float32 LE PCM frames as binary messages,
    a textual `done` message flips the server's `done` flag.
  - `tests/harness.spec.ts` — single Playwright spec the Kotlin
    driver invokes per scenario; reads `NESTS_HARNESS_URL`
    + `NESTS_TIMEOUT_MS` from env.
  - `playwright.config.ts` — Chromium with `--enable-quic`,
    `--ignore-certificate-errors`, AutoplayPolicy override.
- `nestsClient/src/jvmTest/.../interop/native/PlaywrightDriver.kt`
  — Kotlin shim that spawns the bun server + `bun x playwright
  test` per test, returns a `HarnessRun(pcmFile, stdout, exit)`.
  Includes a `CertCapturingValidator` that pulls the relay's leaf
  cert during the speaker's QUIC handshake so we can pass its
  SHA-256 to Chromium via `serverCertificateHashes`.
- `nestsClient/src/jvmTest/.../interop/native/BrowserInteropTest.kt`
  — two scenarios:
  - **I1 forward (browser)**: Amethyst Kotlin speaker → Chromium
    `@moq/lite` listener; asserts FFT 440 Hz on the captured tail.
  - **I15 (WT-Protocol round-trip)**: asserts Chromium's
    `Connection.version` starts with `moq-lite-`.
- `nestsClient/build.gradle.kts` — two new tasks:
  - `interopBuildBrowserHarness` (bun install + bun build → dist/),
  - `interopInstallPlaywrightChromium` (skipped if
    `PLAYWRIGHT_BROWSERS_PATH` already points at a chromium build).
  Both gated on `-DnestsBrowserInterop=true` like the hang tier
  is gated on `-DnestsHangInterop=true`.
- `.github/workflows/build.yml` — new `browser-interop` job
  parallel to `hang-interop`, with bun + node_modules + Playwright
  caches.

## Deviations from the spec

1. **Source layout: `@moq/lite` + `@moq/hang` direct, NOT
   `@moq/watch` `Watch.Broadcast`.** The spec called for mirroring
   NostrNests's `transport/moq-transport.ts` `Watch.Broadcast`
   verbatim; in practice `@moq/watch` 0.2.x bakes in a heavy
   reactive `Effect`/`Signal` layer that's unwieldy for a one-shot
   capture page. The lower-level `connection.consume(path) →
   broadcast.subscribe(track) → track.readFrame()` pipeline is
   what the watch decoder uses internally, so this is a
   functionally equivalent path. NostrNests-side regressions in
   `Watch.Broadcast` plumbing aren't in scope of T16.
2. **Cert pinning via `serverCertificateHashes`, not
   `--ignore-certificate-errors`.** Chromium's
   `--ignore-certificate-errors` flag does NOT bypass QUIC cert
   validation — reproduced as `net::ERR_QUIC_PROTOCOL_ERROR.
   QUIC_TLS_CERTIFICATE_UNKNOWN`. The spec mentioned
   `--ignore-certificate-errors-spki-list` as a "preferred long-
   term form"; we use `serverCertificateHashes` (Web-API equivalent),
   which works because moq-relay's `--tls-generate` produces a
   14-day ECDSA P-256 cert — exactly what the WebTransport spec
   requires for a serverCertificateHashes pin. The
   `CertCapturingValidator` snags the cert during the speaker's
   QUIC handshake so we don't need a separate fingerprint endpoint.
3. **I1 sample-count assertion loosened.** Hang-tier I1 asserts
   `assertSampleCount(expected = 5 s, tolerance = 0.20)` — the
   browser path can't hit that because Chromium cold-launch +
   Playwright runner setup eats 3–10 s before the page starts
   capturing, by which time the `framesPerGroup = 5`
   per-subscriber forward cliff means only the latest cached
   group is replayable. The browser I1 instead asserts ≥ 1 s of
   decoded audio + FFT peak at 440 Hz. The FFT peak is the
   load-bearing assertion (catches downmix / channel-swap /
   OpusHead-leak regressions); the sample-count threshold is just
   a sanity floor.
4. **Phase 4.C scenarios I2/I3/I4/I13/I14 deferred.** I2
   late-join collapses into "tail capture" anyway given the
   Chromium boot lag, so it's not adding signal beyond I1. I3
   mute-window has the same visibility issue. I4 needs the
   reverse publisher path wired up end-to-end (a stub publish.ts
   landed but isn't exercised by a Kotlin test yet). I13 long
   broadcast and I14 CSD-skip are runtime-of-test concerns the
   I1 path already exercises implicitly. Tracked as a follow-up
   on a separate plan if/when the gap matters.

## Verification

```bash
./gradlew :nestsClient:jvmTest \
    --tests "com.vitorpamplona.nestsclient.interop.native.BrowserInteropTest" \
    -DnestsHangInterop=true \
    -DnestsBrowserInterop=true
```

Both `amethyst_speaker_to_chromium_listener_static_tone_440` and
`chromium_round_trips_a_moq_lite_session` pass in isolation.

## Follow-ups

- **I4-reverse**: wire `BrowserInteropTest` to drive
  `PlaywrightDriver.openPublishPage` (the Kotlin side already
  exposes the entry point) → Amethyst Kotlin listener decodes;
  assert per-channel FFT peaks. Needs the publish.ts harness
  graduated from scaffold to a fully-working pump (the
  `MediaStreamTrackProcessor` → `AudioEncoder` → `Container.Legacy.
  Producer` chain compiles but isn't yet validated end-to-end).
- **I3 mute-window**: works on the Kotlin speaker side, but the
  short browser tail capture window means the mute-gap deficit
  isn't observable. Would need either a longer broadcast (60 s+)
  or a tighter capture window that brackets the mute schedule
  reliably. Low priority — the hang-tier I3 already validates the
  speaker-side mute behaviour against a parser-correct watcher.
- **I15 strict pin**: when moq-relay 0.10.x ships with both
  `moq-lite-03` and `moq-lite-04` ALPN advertisement, tighten the
  assertion from `startsWith("moq-lite-")` to exact-match
  `moq-lite-03` (or whichever the production stack runs). Right
  now the relay we boot lands `moq-lite-02` over the legacy
  `moql` ALPN.
- **CI cold-cache time**: cold `npx playwright install chromium`
  takes ~60 s on a fresh GitHub runner. The `actions/cache@v4`
  hits keyed on `package.json` should make warm runs near-zero,
  but the first run on a new branch will be slow.

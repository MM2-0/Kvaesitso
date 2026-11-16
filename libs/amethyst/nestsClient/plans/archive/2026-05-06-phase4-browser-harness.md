# Plan: Phase 4 — browser-side cross-stack harness (T16)

> **Status:** shipped — landed; `BrowserInteropTest.kt` covers I1-I15 scenarios under `nestsClient/tests/browser-interop/`.
> _Audited 2026-06-30._

**Status:** ✅ Landed. Browser harness lives at
`nestsClient/tests/browser-interop/`; tests are
`BrowserInteropTest.kt` covering I1, I2, I3, I4 (stereo), I5
(hot-swap), I7 (publisher reconnect), I9 (packet loss), I13
(long broadcast), I14 (WebCodecs warmup × CSD), I15 (ALPN).
Companion landed-results doc:
`2026-05-06-phase4-browser-harness-results.md`.

The spec text below is preserved for archaeology; some pieces
shifted during implementation:
- `@moq/watch` / `@moq/publish` weren't directly used; the
  harness uses `@moq/lite` + `@moq/hang` `Container.Legacy.Consumer/Producer`
  because the published `@moq/hang` 0.2.4 didn't expose the
  high-level `Container.Consumer` API.
- Cert pinning uses `serverCertificateHashes` not
  `--ignore-certificate-errors` because Chromium's flag does
  NOT bypass QUIC cert validation.
- Phase 4.C originally deferred I2/I3/I4/I13/I14 — those
  subsequently landed, see results doc.

**Origin:** parent plan
`nestsClient/plans/2026-05-06-cross-stack-interop-test.md`,
"Phase 4 — Browser harness (1.5 days)".

**Branch convention:** new branch — don't fold into the
cross-stack-test branch. Suggested name
`feat/nests-browser-interop`.

## Why a browser path

`hang-listen` validates the *wire format* against the canonical
Rust `kixelated/moq` parser. The browser path additionally
validates:

  - Chromium's QUIC + WebTransport stack (different
    implementation from quinn / `:quic`),
  - WebCodecs `AudioDecoder` (different from libopus —
    different look-ahead, different first-frame handling
    behaviour, same `OpusHead` regression risk per T8/T14),
  - AudioWorklet rendering timing (200 ms playback buffer +
    AudioContext clock drift),
  - `WT-Available-Protocols` / `WT-Protocol` ALPN negotiation
    over Chromium's WebTransport — completely separate from
    quinn's TLS-ALPN exchange.

The reference NostrNests web app runs on `@moq/watch` /
`@moq/publish` — this is the actual production stack the
project ships against. A wire-byte round-trip through
hang-listen alone doesn't catch a Chromium quirk that breaks
real users.

## Goal

End-to-end verify:

1. **forward** — Amethyst Kotlin speaker → headless Chromium
   listener (`@moq/watch`), tone recoverable from PCM tap.
2. **reverse** — headless Chromium publisher (`@moq/publish`)
   → Amethyst Kotlin listener, tone recoverable.
3. browser-only scenarios I13 (`framesPerGroup=50` long
   broadcast against `Container.Consumer`), I14 (WebCodecs
   warmup × CSD-skip interaction), I15
   (`WT-Available-Protocols` Chromium round-trip).

All scenarios drive the same `NativeMoqRelayHarness` from
Phase 1 — no Docker, no second relay, no fake auth sidecar.

## Architecture

```
                    Test runner (Gradle :nestsClient:jvmTest)
                                    │
            ┌───────────────────────┼─────────────────────────────────┐
            │                       │                                 │
            ▼                       ▼                                 ▼
 ┌──────────────────────┐   ┌──────────────────┐         ┌─────────────────────────────┐
 │ NativeMoqRelayHarness│   │ Kotlin in-proc   │         │ Browser harness             │
 │ (existing — Phase 1) │   │ speaker / listener│        │ nestsClient/tests/browser-interop/│
 │ moq-relay subprocess │   │ via               │         │  - bun static + WS server   │
 │ 127.0.0.1:<rand>     │   │ connectNestsSpeaker        │  - listen.html + listen.ts  │
 │ --auth-public ""     │   │ connectNestsListener       │  - publish.html+publish.ts  │
 │ --tls-generate       │   │                  │         │  - pcm-tap-worklet.ts       │
 └──────────▲───────────┘   └──────────────────┘         │  - Playwright driver         │
            │ WebTransport over UDP                       │  - PCM capture via WS back- │
            │                                             │    channel                   │
            │                                             └─────────────────────────────┘
            └─────────────────────────────────────────────┘
```

## Components

### 1. `nestsClient/tests/browser-interop/` — bun + Playwright workspace

New top-level directory, mirrors the parent plan's
specification. Contents:

```
nestsClient/tests/browser-interop/
├── package.json
├── tsconfig.json
├── bun.lockb                           # pinned via REV file
├── REV                                 # @moq/* npm versions
├── src/
│   ├── listen.html                     # static page driving Watch.Broadcast
│   ├── publish.html                    # static page driving Publish.Broadcast
│   ├── listen.ts                       # imports @moq/watch + @moq/lite + PCM tap
│   ├── publish.ts                      # imports @moq/publish + @moq/lite + Oscillator src
│   ├── pcm-tap-worklet.ts              # AudioWorklet that posts Float32Array on every
│   │                                   #   inputs[0] frame to the main thread
│   └── server.ts                       # bun static server + WebSocket back-channel
└── playwright.config.ts                # Chromium-only; --enable-quic flags
```

Pin all `@moq/*` deps to the same versions `nostrnests/nests`
ships in `NestsUI-v2/package.json`. Document the rev in
`nestsClient/tests/browser-interop/REV` (parallel to
`nestsClient/tests/hang-interop/REV`).

### 2. `listen.ts` — browser listener

Mirrors NostrNests' `transport/moq-transport.ts`
`Watch.Broadcast` configuration verbatim where possible.
Reads `relay`, `path`, `jwt` (optional), `ws`, `duration`
from query params. Sets up an `AudioContext`, registers
`pcm-tap-worklet`, hooks the worklet into
`broadcast.audio.root`, posts every `inputs[0]`
`Float32Array` over the WebSocket back-channel as a binary
frame. Closes when `duration` elapses.

### 3. `publish.ts` — browser publisher

Reads same params plus `freqHz` and `channels`. Builds an
`OscillatorNode` at `freqHz` connected to a
`MediaStreamAudioDestinationNode`. Passes the resulting
MediaStream's audio track into `Publish.Broadcast`'s
`audio.source`. Closes after `duration`.

### 4. `server.ts` — bun static + WebSocket back-channel

Bun HTTP server: serves `listen.html`, `publish.html`, and
the bundled JS from `dist/`. Bun WebSocket on a separate path
(e.g. `/pcm`): receives PCM chunks from the harness page and
appends them to a file the Gradle test reads. Argv: `--port
<int>` `--out-pcm <path>`.

### 5. `playwright.config.ts` — Chromium with QUIC enabled

```ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  use: {
    launchOptions: {
      args: [
        '--enable-quic',
        '--ignore-certificate-errors',  // self-signed harness cert
        '--enable-features=AutoplayPolicy=NoUserGestureRequired',
        // For tighter cert pinning:
        // '--ignore-certificate-errors-spki-list=<sha256-of-test-cert>',
      ],
    },
  },
});
```

`--ignore-certificate-errors` is acceptable for a test-only
Chromium instance; preferred form
`--ignore-certificate-errors-spki-list=<base64-sha256>` is
documented but optional (cert SPKI is hard to compute from
the relay's auto-generated cert without parsing).

### 6. `interopBuildBrowserHarness` Gradle task

`nestsClient/build.gradle.kts` parallel to
`interopBuildHangSidecars`:

```kotlin
val interopBuildBrowserHarness by tasks.registering(Exec::class) {
    description = "bun install && bun build for the browser interop harness"
    group = "interop"
    workingDir = file("nestsClient/tests/browser-interop")
    commandLine("bash", "-c", "bun install && bun build src/listen.ts src/publish.ts src/pcm-tap-worklet.ts --outdir dist --target browser")
    inputs.files(
        fileTree("nestsClient/tests/browser-interop") {
            include("package.json", "bun.lockb", "src/**/*")
        }
    )
    outputs.dir("nestsClient/tests/browser-interop/dist")
}
```

A second task installs Playwright's Chromium:

```kotlin
val interopInstallPlaywrightChromium by tasks.registering(Exec::class) {
    description = "Install Playwright Chromium + dependencies"
    group = "interop"
    workingDir = file("nestsClient/tests/browser-interop")
    commandLine("bash", "-c", "npx playwright install --with-deps chromium")
    onlyIf {
        // Skip if Chromium binary exists in the cache
        val home = System.getProperty("user.home")
        !file("$home/.cache/ms-playwright/chromium-*").exists() // glob matches if any
    }
}
```

Forward to test workers:

```kotlin
tasks.withType<Test>().configureEach {
    val isBrowserInterop = System.getProperty("nestsBrowserInterop") == "true"
    if (isBrowserInterop) {
        dependsOn(interopBuildBrowserHarness, interopInstallPlaywrightChromium)
    }
    systemProperty(
        "nestsBrowserInteropHarnessDir",
        file("nestsClient/tests/browser-interop").absolutePath,
    )
    System.getProperty("nestsBrowserInterop")?.let {
        systemProperty("nestsBrowserInterop", it)
    }
}
```

### 7. Kotlin-side `PlaywrightDriver` + `BrowserInteropTest`

Path:
`nestsClient/src/jvmTest/.../interop/native/PlaywrightDriver.kt`
(new) and
`nestsClient/src/jvmTest/.../interop/native/BrowserInteropTest.kt`
(new).

`PlaywrightDriver` shells out to `npx playwright test` (or
uses `playwright-java` from Maven Central — verify
availability at implementation time). Returns when the
harness page reports completion via a final WebSocket
message or a console log.

```kotlin
object PlaywrightDriver {
    fun openListenPage(
        harnessUrl: String,
        relayUrl: String,
        path: String,
        jwt: String?,
        durationSec: Int,
        wsOutPcm: File,
    ): Process { … }

    fun openPublishPage(
        harnessUrl: String,
        relayUrl: String,
        path: String,
        jwt: String?,
        freqHz: Int,
        channels: Int,
        durationSec: Int,
    ): Process { … }
}
```

Each invocation:
1. Runs the bun static server on a random port (one per
   test for isolation; reuses the same `:0`-bound socket
   pattern as `NativeMoqRelayHarness`).
2. Spawns `npx playwright test` with `--config playwright.config.ts`
   and a per-test runner that opens the right URL with the
   right query params.
3. Plays through `durationSec` seconds; WS server appends PCM
   frames to `wsOutPcm` as native-endian Float32 LE.
4. Returns the Process so the test can kill it cleanly.

### 8. `BrowserInteropTest` scenarios

Mirror of `HangInteropTest`'s shape. P0 scenarios per the
parent plan:

| ID | Direction | Speaker | Listener | Asserts |
|---|---|---|---|---|
| **I1 browser** | A→ref | Amethyst Kotlin | Chromium @moq/watch | FFT 440 Hz |
| **I2 browser** | both | … | … | late-join still gets tail |
| **I3 browser** | A→ref | Amethyst Kotlin | Chromium | mute window |
| **I4 browser** | both | Amethyst (stereo) | Chromium | per-channel FFT |
| **I13** | A→ref | Amethyst | Chromium | 60 s, no eviction-driven silence |
| **I14** | A→ref | Amethyst | Chromium | WebCodecs 3-frame warmup × T8 CSD-skip |
| **I15** | A→ref | Amethyst | Chromium | `WT-Protocol` matches `moq-lite-03` |

I1–I4 reuse the existing `runSpeakerToHangListen` harness
infrastructure but swap the listener subprocess from
`hang-listen` to `PlaywrightDriver.openListenPage`. The
harness already exposes `relayUrl` + relay UDP loopback;
no new harness API needed.

## Phases

Total: ~1.5 days.

### Phase 4.A — bun harness scaffold (~3 hr)

1. `bun init` in `nestsClient/tests/browser-interop/`. Pin `@moq/lite`,
   `@moq/watch`, `@moq/publish`, `@moq/hang` to the versions
   `nostrnests/nests` `NestsUI-v2/package.json` ships at the
   time of implementation. Document in `REV`.
2. Write `listen.ts` + `pcm-tap-worklet.ts` + `listen.html`.
   Mirror NostrNests' `transport/moq-transport.ts`
   `Watch.Broadcast` configuration verbatim.
3. Write `publish.ts` + `publish.html` (sine source via
   `OscillatorNode` → `MediaStreamAudioDestinationNode`).
4. Write `server.ts` (bun static + WebSocket back-channel,
   writes PCM to a file on disk).
5. Wire `interopBuildBrowserHarness` Gradle task.

Verify by running the bun server manually + opening
`http://localhost:<port>/listen.html` in a desktop Chromium
with the `--enable-quic` + `--ignore-certificate-errors`
flags; confirm a manual moq-relay + hang-publish behind it
delivers tone.

### Phase 4.B — Playwright driver + Kotlin tests (~3 hr)

6. Add Playwright (`@playwright/test`) to the bun harness's
   dev deps. Wire `interopInstallPlaywrightChromium` Gradle
   task.
7. Write `PlaywrightDriver.kt` shelling out to
   `npx playwright test` (or `playwright-java` if
   available). Cert-pin via `--ignore-certificate-errors`
   for the test-only Chromium instance.
8. Write `BrowserInteropTest.kt` with the I1 forward
   scenario as the smoke test (Amethyst speaker → Chromium
   listener, FFT 440 Hz on the captured PCM).

Verify green via:
```bash
./gradlew :nestsClient:jvmTest \
    --tests "com.vitorpamplona.nestsclient.interop.native.BrowserInteropTest" \
    -DnestsHangInterop=true \
    -DnestsBrowserInterop=true
```

### Phase 4.C — additional P0 scenarios (~3 hr)

9. I2 (late-join), I3 (mute), I4 (stereo if I4 stereo plan
   has landed; else skip and unblock when stereo merges).
10. I13 (`framesPerGroup=50` long broadcast — interesting
    because the Chromium path may have a different per-group
    cliff threshold than `hang-listen`; this scenario likely
    NEEDS `framesPerGroup=5` like the hang-listen ones).
11. I14 (WebCodecs warmup × CSD-skip): assert that with
    T8's CODEC_CONFIG filter active, the browser receives
    a normal decode after the standard 3-frame warmup —
    no extra warmup penalty.
12. I15 (`WT-Available-Protocols` round-trip): Playwright's
    `browser.newContext()` exposes the response headers;
    assert `WT-Protocol` matches `moq-lite-03`.

Per-scenario commits (one per `BrowserInteropTest` test
method).

### Phase 4.D — CI integration (~1 hr)

13. Add `browser-interop` job to `.github/workflows/build.yml`
    parallel to `hang-interop`. Cache
    `nestsClient/tests/browser-interop/node_modules` and
    `~/.cache/ms-playwright` on the bun.lockb hash.
14. Run `./gradlew :nestsClient:jvmTest -DnestsBrowserInterop=true`
    on Linux runners. macOS / Windows would double the matrix
    cost without catching new defects (Chromium QUIC behaviour
    is consistent across platforms in the test scenarios we
    care about).

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| Chromium WebTransport rejects self-signed cert | Use `--ignore-certificate-errors` for test-only Chromium. Long-term, `--ignore-certificate-errors-spki-list=<sha256>` is preferable but needs SPKI extraction from the relay's auto-generated cert. |
| WebCodecs `AudioDecoder` not available in headless Chromium | WebCodecs is in stable Chromium since 94 (2021); Playwright bundles current Chromium. Verify on PR. |
| AudioWorklet on a headless context — `AudioContext.resume()` requires user gesture in some Chromium configs | Pass `--enable-features=AutoplayPolicy=NoUserGestureRequired` (already in `playwright.config.ts`) AND call `AudioContext.resume()` explicitly in the harness page before adding the audio source. |
| `@moq/watch` API changes between bun.lockb pins | Pin to specific versions matching `nostrnests/nests`. Bump deliberately. |
| Bun → Playwright integration weird on CI runners | Fall back to `node` if `bun` doesn't ship Playwright runner properly; the harness server doesn't depend on bun-specific APIs. |
| WS back-channel binary frames vs JSON: Playwright captures only stdout, not WS | Server.ts writes PCM directly to disk; the test reads the file path forwarded from the runner. No WS-from-test path. |
| Cold cache: 60s+ for `npx playwright install --with-deps chromium` | Cache `~/.cache/ms-playwright` on `package.json` hash. Document the cold cost in CI docs. |

## Definition of done

1. `nestsClient/tests/browser-interop/` directory complete with
   bun + Playwright + sources building cleanly via
   `interopBuildBrowserHarness`.
2. P0 scenarios green: I1 forward, I2, I3, I13, I14 (and I4
   if the stereo plan landed).
3. P1 scenarios green: I15 (`WT-Protocol` round-trip).
4. CI: `browser-interop` job green on PRs and main.
5. `nestsClient/plans/2026-05-06-phase4-browser-harness-results.md`
   summarising what landed, deviations, and follow-ups.
6. `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
   gets a "Phase 4" section appended.
7. The hang-tier scenarios (HangInteropTest) stay green when
   `-DnestsBrowserInterop=true` is OFF — no regression.

## Out of scope (intentionally)

- **iOS Safari WebKit** — not on Playwright's main browser
  list, separate matrix.
- **Mobile Chromium variants** (Android Chrome, Samsung
  Internet) — desktop Chromium is what the production stack
  ships against today.
- **Real device microphone in the publisher path** — sine
  via `OscillatorNode` is enough for wire-format and decoder
  correctness. Real-microphone parity is a field-test
  concern.
- **`moq-lite-04` ALPN bump** — the parent plan's
  out-of-scope, separate task. Pin `--client-version
  moq-lite-03` (matches the existing hang-tier scenarios).

## When picking up

This plan is self-contained. The agent should:

1. Read `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`
   (parent) and
   `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
   (Phase 1–3 status) for context on the harness.
2. Skim `nestsClient/src/jvmTest/.../interop/native/HangInteropTest.kt`
   for the existing scenario shape — `BrowserInteropTest`
   reuses `runSpeakerToHangListen`'s harness orchestration
   pattern.
3. Re-clone `kixelated/moq` to `/tmp/moq` for reference:
   `git clone --depth=1 https://github.com/kixelated/moq.git /tmp/moq`.
   Confirm `/tmp/moq/js/watch`, `/tmp/moq/js/publish`,
   `/tmp/moq/js/lite`, `/tmp/moq/js/hang` are present
   (sparse checkout if needed). The browser harness's
   `listen.ts` / `publish.ts` mirror that JS.
4. Verify `bun --version` ≥ 1.3 and `npx playwright
   --version` available on the host. The cargo + Rust
   toolchain from Phase 1 stays unchanged.
5. Implement Phase 4.A first (scaffolding + manual
   verification), then 4.B (driver + first Kotlin test).
   Don't proceed to scenario expansion (4.C) until 4.B is
   green.
6. Each scenario commits separately. The harness setup
   (4.A + 4.B) is one logical chunk.

# Plan: cross-stack interop test (T16)

> **Status:** shipped — implemented and merged; `HangInteropTest`/`BrowserInteropTest` suites exist under `nestsClient/tests/`.
> _Audited 2026-06-30._

**Status:** ✅ Implemented and merged. See:
- `2026-05-06-cross-stack-interop-test-results.md` for the scenario
  inventory + per-scenario status
- `2026-05-06-cross-stack-interop-test-gap-matrix.md` for the
  T-series wire-fix → asserting-scenario mapping (DoD #5)
- `2026-05-07-t16-closure-roadmap.md` for the next-steps roadmap
  (residual upstream relay flake → tighten assertions → wire CI)

The spec text below is preserved for archaeology; some scenarios
were re-shaped during implementation (I12 GOAWAY is N/A in
moq-lite-03; I13's `framesPerGroup = 50` got pinned to 5 due to the
local relay's per-stream byte cliff).

**Origin:** audit of `claude/debug-audio-dropout-n0g6Z` against the audio
path verified all wire fixes (T1–T14) by inspection, but the existing
interop tests are Kotlin↔Kotlin only. They pass even when both ends
share a wire-format bug. This plan adds a real cross-stack test against
two reference listener/publisher stacks: the kixelated/moq Rust `hang`
crate and a Chromium browser running `@moq/watch`.

**Branch convention:** implement on a fresh branch (e.g.
`feat/nests-interop-cross-stack`). Don't fold into the audit branch.

## Goal

End-to-end verify that an Amethyst Kotlin speaker is intelligible to a
`@moq/hang`-based listener (the canonical NostrNests web stack), and
vice versa, against a real `kixelated/moq-rs` `moq-relay`. No Docker.
Catches every audio-path wire regression (T1–T14) with at least one
asserting scenario.

## Decision: two P0 stacks, both required

- **Rust path** uses the `kixelated/moq` Rust `hang` crate as the
  listener / publisher. Validates **wire format** (catalog JSON,
  legacy container envelope, Subscribe routing, group framing). Fast,
  deterministic, no headless deps.
- **Browser path** uses `@moq/watch` and `@moq/publish` running in
  headless Chromium via Playwright. Validates the **actual production
  stack**: Chromium QUIC, WebCodecs `AudioDecoder`, AudioWorklet
  rendering, `WT-Available-Protocols` sub-protocol negotiation.

Browser is NOT optional. NostrNests is a browser, and the Rust path
can't see WebCodecs warmup quirks, AudioWorklet timing, or Chromium's
QUIC stack behaviour.

## Why no Docker

The previous interop tests use `nostrnests/nests` Docker compose for
relay + auth. Building those native instead is strictly simpler:

- `moq-relay`: cargo binary, no infra.
- `moq-auth`: removed from the loop. JWT minting moves into Kotlin (we
  already have ES256 primitives via `:quartz`). The relay's JWT
  validator is the security-relevant half; issuance is implementation
  detail.
- TLS cert: self-signed P-256 generated at test setup.

Result: no Docker daemon, no Node sidecar, native `moq-relay`
subprocess + native cargo binaries + Chromium-via-Playwright. Runs in
any CI runner with rustup + bun + Chromium installed.

`moq-auth`'s NIP-98 path is exercised separately by the existing
`NostrNestsAuthInteropTest` (which keeps the Docker'd auth sidecar).
Don't conflate.

## Architecture

```
                    Test runner (Gradle :nestsClient:jvmTest)
                                    │
            ┌───────────────────────┼─────────────────────────────────┐
            │                       │                                 │
            ▼                       ▼                                 ▼
 ┌──────────────────────┐   ┌──────────────────┐         ┌─────────────────────────────┐
 │ moq-relay subprocess │   │ Kotlin in-proc   │         │ Reference sidecars          │
 │ (native cargo build) │   │ JWT minter ES256 │         │                             │
 │ relay.toml + cert    │   │ JWKS public key  │         │ Path A — RUST (cargo)       │
 │ 127.0.0.1:<rand>     │   │ matches relay's  │         │  - hang-listen <args>       │
 └─────────▲────────────┘   └──────────────────┘         │  - hang-publish <args>      │
           │                                             │                             │
           │                                             │ Path B — BROWSER (bun)      │
           │ WebTransport over UDP loopback              │  - listen.html via @moq/watch│
           └─────────────────────────────────────────────┤  - publish.html via @moq/publish│
                                                         │  - PCM tap worklet           │
                       Kotlin speaker / listener         │  - WebSocket back-channel    │
                       (in-process, real :quic stack)    │  - driven by Playwright      │
                                                         └─────────────────────────────┘
```

## Components

### Native moq-relay subprocess

Built once via cargo from the `kixelated/moq` workspace at a pinned
git rev. Cached.

- Source: `https://github.com/kixelated/moq` pinned to a known-good
  rev. Use the same rev as the production `nostrnests/nests` Docker
  image's `moq-relay` build, if discoverable; otherwise pin to the
  latest commit on `main` at implementation time and document it in a
  `nestsClient/tests/hang-interop/REV` file.
- Build: `cargo build --release -p moq-relay --manifest-path <pinned/checkout>/Cargo.toml`.
- Cache key: pinned rev sha. First run: ~30 s cold build. Warm: < 1 s.
- Config (rendered to a temp file at test setup):
  ```toml
  # relay.toml
  [server]
  listen = "127.0.0.1:<random-port>"

  [tls]
  cert = "<temp>/cert.pem"
  key = "<temp>/key.pem"

  [auth]
  jwks_path = "<temp>/test.jwks"
  ```
  Exact field names depend on the pinned moq-relay version. Verify at
  implementation time by reading `moq-relay/src/config.rs` at the
  pinned rev. If the relay version requires different fields, adapt.

### In-process JWT minter (Kotlin)

ES256 keypair generated once per test class. Public half written to
`test.jwks` for the relay; private half held in-memory. Mint per-test
JWTs with claims:
```
{
  "root": "<broadcast-namespace>",
  "put":  ["<pubkey>"]      // for publisher tokens
  "get":  [""]              // for listener tokens (read all under root)
  "iat":  <now>,
  "exp":  <now + 600>
}
```
ES256 signing via `java.security.Signature` with `SHA256withECDSA`
plus DER→raw conversion (or pull `nimbus-jose-jwt` if simpler — same
group of dependencies that may already be on the classpath via OkHttp).

### Self-signed TLS cert

Generated at suite setup via Java's `KeyPairGenerator` + Bouncy Castle
(`X509v3CertificateBuilder`) or by shelling out to `openssl req -x509`.
Output `cert.pem` + `key.pem` in PEM format for moq-relay; the
in-process Kotlin client uses a `PermissiveCertValidator` (already
available — see `nestsClient/src/jvmAndroid/.../transport/QuicWebTransportFactory.kt`'s
`certificateValidator` constructor parameter) so it accepts the
self-signed cert without populating the system trust store.

### Rust hang sidecar binaries

New cargo workspace at `nestsClient/tests/hang-interop/` in this repo.

```toml
# nestsClient/tests/hang-interop/Cargo.toml
[workspace]
members = ["hang-listen", "hang-publish", "udp-loss-shim"]

[workspace.dependencies]
hang        = { git = "https://github.com/kixelated/moq", rev = "<pinned>" }
moq-lite    = { git = "https://github.com/kixelated/moq", rev = "<pinned>" }
web-transport-quinn = "0.11"
tokio       = { version = "1", features = ["full"] }
anyhow      = "1"
```

Three binaries:

#### `hang-listen` — `nestsClient/tests/hang-interop/hang-listen/src/main.rs`
Args: `--relay-url <https://...>` `--jwt <token>` `--broadcast <path>` `--duration <secs>` `--output-pcm <path-or-->`.
Behaviour:
1. Connect to relay via `web-transport-quinn` with WT-Available-Protocols
   = `["moq-lite-04", "moq-lite-03"]` (matches Amethyst).
2. Open a `moq-lite` session.
3. Spawn `hang::Watch` on the broadcast.
4. Subscribe `catalog.json` via `hang::Catalog::fetch`. Pick the first
   audio rendition with `container.kind == "legacy"`.
5. Subscribe to that rendition's track. For each frame:
   `Container::Legacy::Format::decode` → `EncodedAudioChunk` →
   `opus::Decoder::decode` (or `audiopus`) → write Float32 samples
   to `--output-pcm`.
6. Exit after `--duration` seconds OR if catalog says `Ended`.

Output format: little-endian Float32 PCM, no header. One channel for
mono, interleaved L/R for stereo (controlled by catalog's
`numberOfChannels`). The Gradle test reads this file or pipe.

#### `hang-publish` — `nestsClient/tests/hang-interop/hang-publish/src/main.rs`
Args: `--relay-url <...>` `--jwt <token>` `--broadcast <path>` `--freq-hz <int>` `--duration <secs>` `--channels <1|2>`.
Behaviour:
1. Connect, open session, claim broadcast under given path.
2. Publish a `catalog.json` track with hang Root JSON declaring one
   `audio/data` rendition: `{ codec: "opus", container: { kind: "legacy" },
   sampleRate: 48000, numberOfChannels: <channels>, bitrate: 32000,
   jitter: 20 }`.
3. Generate sine wave at `freq-hz` Hz at 48 kHz, encode via libopus
   (`audiopus` crate), wrap each packet as `varint(timestamp_us) +
   opus_packet`, push into groups of 5 frames, FIN per group.
4. Run for `duration` seconds, then send `Announce::Ended` and exit.

#### `udp-loss-shim` — `nestsClient/tests/hang-interop/udp-loss-shim/src/main.rs`
Args: `--listen 127.0.0.1:<port>` `--upstream 127.0.0.1:<port>` `--loss-rate <0..1>`.
Behaviour: standard tokio UDP relay. For each datagram received,
`if rng.gen::<f32>() < loss_rate { drop }` else forward. Used for I9.

### Browser harness (bun + Playwright)

New module at `nestsClient/tests/browser-interop/`.

```
nestsClient/tests/browser-interop/
├── package.json
├── tsconfig.json
├── src/
│   ├── listen.html
│   ├── publish.html
│   ├── listen.ts        # imports @moq/watch + @moq/lite, sets up PCM tap
│   ├── publish.ts       # imports @moq/publish + @moq/lite, sets up sine source
│   ├── pcm-tap-worklet.ts  # AudioWorkletProcessor that posts Float32 to main
│   └── server.ts        # bun static + WebSocket back-channel
└── bun.lockb
```

`package.json` deps (all pinned via bun.lockb):
```
{
  "dependencies": {
    "@moq/lite": "<pinned>",
    "@moq/watch": "<pinned>",
    "@moq/publish": "<pinned>",
    "@moq/hang": "<pinned>"
  }
}
```

Pin to the same npm versions that `nostrnests/nests` `NestsUI-v2/package.json`
ships. Document the rev in `nestsClient/tests/browser-interop/REV`.

#### `listen.ts`
Mirrors NostrNests' `transport/moq-transport.ts` `Watch.Broadcast`
configuration verbatim where possible. Reads `relay`, `path`, `jwt`,
`ws`, `duration` from query params. Sets up an `AudioContext`,
registers `pcm-tap-worklet`, hooks the worklet into
`broadcast.audio.root`, posts every `inputs[0]` Float32Array out to
`ws://localhost:<port>/pcm` over WebSocket. Closes when `duration`
elapses.

#### `publish.ts`
Reads same params plus `freqHz` and `channels`. Builds an
`OscillatorNode` at `freqHz` connected to a
`MediaStreamAudioDestinationNode`. Passes the resulting MediaStream's
audio track into `Publish.Broadcast`'s `audio.source`. Closes after
`duration`.

#### `server.ts`
Bun HTTP server: serves `listen.html`, `publish.html`, and the bundled
JS from `dist/`. Bun WebSocket server on a separate path: receives
PCM chunks from the harness page, writes them to a file the Gradle
test reads. Argv: `--port <int>` `--out-pcm <path>`.

#### Build
`bun install && bun build src/listen.ts src/publish.ts src/pcm-tap-worklet.ts --outdir dist --target browser`.
Wrapped by a Gradle task `interopBuildBrowserHarness` that runs once
per Cargo.lock-equivalent change.

#### Driver

`nestsClient/src/jvmTest/.../interop/native/PlaywrightDriver.kt`
shells out to `npx playwright test` (or uses `playwright-java` if
available; check Maven Central). Key flags for Chromium:
```
--ignore-certificate-errors-spki-list=<sha256-of-our-test-cert>
--enable-quic
--origin-to-force-quic-on=127.0.0.1:<relay-port>
```
Or simpler for a one-off test instance:
`--ignore-certificate-errors`.

The driver returns when the harness page reports completion (via a
console message Playwright watches for, or a final WebSocket message).

## Kotlin test infrastructure

### `NativeMoqRelayHarness.kt`
Path: `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/NativeMoqRelayHarness.kt`.

Public API:
```kotlin
class NativeMoqRelayHarness {
    val relayUrl: String  // https://127.0.0.1:<port>/<broadcast-path>
    fun mintJwt(broadcastNamespace: String, publish: Boolean, pubkeyHex: String): String
    fun loopbackHostPort(): Pair<String, Int>
    fun hangListenBin(): Path
    fun hangPublishBin(): Path
    fun udpLossShimBin(): Path
    fun browserHarnessUrl(): String  // bun static server
    fun browserPcmFile(): Path
}
```

Owned via JUnit `@BeforeAll` / `@AfterAll`. Lifecycle:
1. Lazily build cargo binaries on first `@BeforeAll`.
2. Generate ES256 keypair + JWKS file.
3. Generate self-signed cert.
4. Start `moq-relay` subprocess; await stderr "listening on" line; record port.
5. Start bun static server (only if browser path used in the test class).
6. Subprocesses tracked for kill in `@AfterAll`.

### `SineWaveAudioCapture.kt`
Path: `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/audio/SineWaveAudioCapture.kt`.

Implements `AudioCapture` from
`nestsClient/src/commonMain/.../audio/Audio.kt`. Per-frame produces
`960` 16-bit PCM samples of a `freqHz` sine at 48 kHz, advancing a
sample-counter (deterministic, frame-perfect, no wall-clock).

```kotlin
class SineWaveAudioCapture(
    private val freqHz: Int = 440,
    private val channels: Int = 1,
    private val amplitude: Short = 16_383,  // ~half-scale, leaves headroom
) : AudioCapture {
    private var sampleIdx: Long = 0L
    override fun start() { /* no-op */ }
    override suspend fun readFrame(): ShortArray? {
        val samplesPerFrame = AudioFormat.FRAME_SIZE_SAMPLES
        val out = ShortArray(samplesPerFrame * channels)
        for (i in 0 until samplesPerFrame) {
            val t = sampleIdx + i
            val v = (amplitude * sin(2.0 * PI * freqHz * t / AudioFormat.SAMPLE_RATE_HZ)).toInt().toShort()
            for (ch in 0 until channels) out[i * channels + ch] = v
        }
        sampleIdx += samplesPerFrame
        return out
    }
    override fun stop() { /* no-op */ }
}
```

### `CapturingOpusDecoder.kt`
Path: `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/audio/CapturingOpusDecoder.kt`.

Wraps the real `MediaCodecOpusDecoder` (or `JvmOpusDecoder` if added)
and tees decoded PCM into a thread-safe buffer the test asserts on.
Used for "Rust → Amethyst" and "browser → Amethyst" directions.

### `PcmAssertions.kt`
Path: `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/audio/PcmAssertions.kt`.

Signal-domain assertions:
```kotlin
object PcmAssertions {
    /** Sample count within ±tolerance of expected duration. */
    fun assertSampleCount(samples: FloatArray, expectedDurationSec: Double, sampleRate: Int = 48_000, tolerance: Double = 0.05)
    /** FFT peak frequency in expected range. Implements a tiny power-of-2 FFT inline. */
    fun assertFftPeak(samples: FloatArray, expectedHz: Double, halfWindowHz: Double = 5.0, sampleRate: Int = 48_000)
    /** RMS amplitude in [minRms, maxRms]. */
    fun assertRms(samples: FloatArray, minRms: Float = 0.3f, maxRms: Float = 0.9f)
    /** Zero-crossing rate within tolerance — catches Opus predictor warble. */
    fun assertZeroCrossingRate(samples: FloatArray, expectedPerSecond: Double, tolerance: Double = 0.10, sampleRate: Int = 48_000)
    /** Find a contiguous silence window (RMS < threshold) of at least minDurSec, return its [startSec, endSec]. */
    fun findSilenceWindow(samples: FloatArray, minDurSec: Double, threshold: Float = 0.01f, sampleRate: Int = 48_000): ClosedRange<Double>?
}
```

FFT: use a small in-test radix-2 implementation (~50 lines) so we don't
pull a JTransforms / JCommons dep.

## Test scenarios

All scenarios live in two files:
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropTest.kt`
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/BrowserInteropTest.kt`

Gated by `-DnestsHangInterop=true` and `-DnestsBrowserInterop=true`
respectively (mirror existing `-DnestsInterop=true`).

Each scenario asserts (a) a process / Kotlin side runs to completion
(b) PCM samples arrive (c) signal-domain bounds hold.

| ID | Scenario | Direction | Hang (Rust) | Browser |
|---|---|---|---|---|
| **I1** | 5 s sine 440 Hz, listener attached pre-broadcast | both | ✅ P0 | ✅ P0 |
| **I2** | Late-join: listener attaches at T+2 s of a 5 s broadcast | both | ✅ P0 | ✅ P0 |
| **I3** | Mute 1 s mid-3 s broadcast; assert silence window | A→ref | ✅ P0 | ✅ P0 |
| **I4** | Stereo Opus (numberOfChannels=2); freq differs L/R (440 / 660) | both | ✅ P0 | ✅ P0 |
| **I5** | Speaker hot-swap at T+2.5 s (force JWT refresh); listener no audio gap | A→ref | ✅ P1 | ✅ P1 |
| **I6** | One speaker, three listeners | A→ref | ✅ P1 | optional |
| **I7** | Web speaker drops + reconnects mid-broadcast | ref→A | ✅ P1 | ✅ P1 |
| **I8** | SubscribeDrop on unknown track | both | ✅ P0 | optional |
| **I9** | 1% packet loss via udp-loss-shim | A→ref | ✅ P1 | ✅ P1 |
| **I10** | 60 s broadcast, sample count ≥ 95% expected | A→ref | ✅ P0 | ✅ P0 |
| **I11** | Wire-byte capture: assert no `OpusHead\\1\\1...` in first audio frame | A→ref | ✅ P0 | n/a |
| **I12** | Goaway: drive moq-relay to send Goaway; speaker accepts gracefully | A→ref | ✅ P0 | optional |
| **I13** | `framesPerGroup=50` long broadcast against actual `Container.Consumer` | A→ref | n/a | ✅ P0 |
| **I14** | WebCodecs 3-frame warmup absorbs T8 OpusHead skip cleanly | A→ref | n/a | ✅ P0 |
| **I15** | `WT-Available-Protocols` Chromium roundtrip (verify negotiated subprotocol) | A→ref | n/a | ✅ P1 |

I1–I4, I8, I10–I12 + I13, I14 are P0 — they cover every BLOCKER fix
in the audit (T1, T2, T3, T6, T7, T8, T9, T10) plus the framesPerGroup
regression risk and CSD-warmup interaction. I5, I7, I9 are P1
(hot-swap + transport robustness, T11–T13). I6, I15 are P1 (multi-
listener + ALPN drift detection).

### How each scenario is implemented

Pattern for **Amethyst → Rust** (e.g. I1):
```kotlin
@Test
fun amethyst_speaker_to_hang_listener_static_tone() {
    val broadcast = "test/${UUID.randomUUID()}"
    val speakerJwt = harness.mintJwt(broadcast, publish = true, pubkeyHex = TEST_PUBKEY)
    val listenerJwt = harness.mintJwt(broadcast, publish = false, pubkeyHex = TEST_PUBKEY)

    // Spawn hang-listen subprocess, capturing PCM to a temp file.
    val pcmFile = createTempFile(prefix = "hang-listen-pcm", suffix = ".bin")
    val listenProc = ProcessBuilder(
        harness.hangListenBin().toString(),
        "--relay-url", harness.relayUrl,
        "--jwt", listenerJwt,
        "--broadcast", broadcast,
        "--duration", "6",
        "--output-pcm", pcmFile.toString(),
    ).redirectErrorStream(true).start()

    // Drive Kotlin speaker via the production connect path.
    val speaker = connectNestsSpeaker(
        room = NestsRoomConfig(
            authBaseUrl = "<unused>",
            endpoint = harness.relayUrl,
            hostPubkey = TEST_PUBKEY,
            roomId = broadcast.substringAfterLast("/"),
        ),
        signer = TEST_SIGNER,
        speakerPubkeyHex = TEST_PUBKEY,
        captureFactory = { SineWaveAudioCapture(freqHz = 440) },
        encoderFactory = { JvmOpusEncoder() /* or whatever audiopus wrapper */ },
        // Bypass moq-auth: pre-mint our own JWT.
        httpClient = StaticJwtNestsClient(speakerJwt),
        transport = QuicWebTransportFactory(certificateValidator = PermissiveCertValidator),
        scope = backgroundScope,
    )
    val handle = speaker.startBroadcasting { /* level callback */ }
    delay(5_000)
    handle.close()
    speaker.close()

    // Wait for hang-listen to exit cleanly.
    assertTrue(listenProc.waitFor(2, TimeUnit.SECONDS))

    // Read decoded PCM, run signal-domain assertions.
    val pcm = readFloat32Pcm(pcmFile)
    PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0)
    PcmAssertions.assertFftPeak(pcm, expectedHz = 440.0)
    PcmAssertions.assertZeroCrossingRate(pcm, expectedPerSecond = 880.0)
}
```

Pattern for **Rust → Amethyst** (reverse of I1):
```kotlin
@Test
fun hang_publisher_to_amethyst_listener_static_tone() {
    // ... spawn hang-publish subprocess ...
    val capture = CapturingOpusDecoder(real = JvmOpusDecoder(channelCount = 1))
    val listener = connectNestsListener(/* injecting `capture` as the decoder */)
    val handle = listener.subscribeSpeaker(TEST_PUBKEY, maxLatencyMs = 0)
    NestPlayer(decoder = capture, ...).play(handle.objects)
    delay(5_500)
    val pcm = capture.takePcm()
    PcmAssertions.assertFftPeak(pcm, expectedHz = 440.0)
    // ... etc
}
```

Pattern for **Amethyst → browser** (e.g. I1 browser):
```kotlin
@Test
fun amethyst_speaker_to_browser_listener_static_tone() {
    val broadcast = "test/${UUID.randomUUID()}"
    val speakerJwt = harness.mintJwt(broadcast, publish = true, pubkeyHex = TEST_PUBKEY)
    val listenerJwt = harness.mintJwt(broadcast, publish = false, pubkeyHex = TEST_PUBKEY)

    // Drive browser harness via Playwright.
    val pcmFile = harness.browserPcmFile()
    val playwrightProc = PlaywrightDriver.openListenPage(
        harnessUrl = "${harness.browserHarnessUrl()}/listen.html",
        relayUrl = harness.relayUrl,
        path = broadcast,
        jwt = listenerJwt,
        durationSec = 6,
        wsOutPcm = pcmFile,
    )

    // Same Kotlin speaker setup as the hang test.
    val speaker = connectNestsSpeaker(...)
    val handle = speaker.startBroadcasting { }
    delay(5_000)
    handle.close()
    speaker.close()

    assertTrue(playwrightProc.waitFor(3, TimeUnit.SECONDS))
    val pcm = readFloat32Pcm(pcmFile)
    PcmAssertions.assertFftPeak(pcm, expectedHz = 440.0)
    // Browser-side WebCodecs warmup drops first 3 frames; allow looser tolerance:
    PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0, tolerance = 0.10)
}
```

## Phases

Total: ~5 days. P0 deliverable (1+2+4) is **3 days**.

### Phase 1 — Native moq-relay + Rust sidecars (1 day)

1. Pick `kixelated/moq` rev. Clone to `nestsClient/tests/hang-interop/.cache/moq` or
   reference via `git` Cargo source. Document rev in
   `nestsClient/tests/hang-interop/REV`.
2. Write `nestsClient/tests/hang-interop/Cargo.toml` workspace + the three binary
   crates (`hang-listen`, `hang-publish`, `udp-loss-shim`). Verify
   `cargo build --release` succeeds end-to-end.
3. Add Gradle task `interopBuildHangSidecars` (in
   `nestsClient/build.gradle.kts`) that:
   - Runs `cargo build --release` in `nestsClient/tests/hang-interop/`.
   - Exposes binary paths to JVM tests via `tasks.test { systemProperty(...) }`.
   - Caches based on `Cargo.lock` hash.
4. Write `NativeMoqRelayHarness.kt` (subprocess management, JWT mint,
   cert generation, port reservation).
5. Write `SineWaveAudioCapture.kt`, `CapturingOpusDecoder.kt`,
   `PcmAssertions.kt`.
6. Write a JVM-side Opus encoder/decoder stub if not already present
   (`audiopus`-equivalent — see existing `:nestsClient` to confirm
   what's available on JVM target; may need to add a JNI binding or
   reuse the Android one via a JVM-compatible build).
7. Wire one passing test: `HangInteropTest.amethyst_speaker_to_hang_listener_static_tone`
   (I1, A→hang).

### Phase 2 — Rust path scenarios (1 day)

8. Reverse direction: `hang_publisher_to_amethyst_listener_static_tone`.
9. I2 (late-join), I3 (mute window), I4 (stereo), I8 (SubscribeDrop),
   I11 (CSD wire-byte capture — capture the first uni stream's first
   frame and assert `payload[0..7] != "OpusHead"`).
10. I12 (Goaway): add a control message to moq-relay's admin port if
    available; otherwise simulate by sending Goaway from the speaker
    side and verifying our handler.

### Phase 3 — Hot-swap + transport robustness (1 day)

11. I5 (speaker hot-swap mid-broadcast): force JWT refresh via
    `connectReconnectingNestsSpeaker(tokenRefreshAfterMs = 2_500)`.
    Assert no silence window on the listener side > 200 ms.
12. I7 (publisher hot-swap on the Rust side): kill + restart
    `hang-publish` process; expect listener to see Ended → Active
    + decoder reset (T13).
13. I9 (1% packet loss): wrap Kotlin speaker's transport with
    `udp-loss-shim` between client and relay. Assert sample count
    ≥ 80% expected, FFT peak still at 440 Hz, no contiguous silence
    > 200 ms.
14. I10 (60 s long broadcast): straightforward extension of I1
    with a long duration; mostly a test-runtime-budget check.

### Phase 4 — Browser harness (1.5 days)

15. Bootstrap `nestsClient/tests/browser-interop/`: bun init, install
    `@moq/lite` `@moq/watch` `@moq/publish` `@moq/hang` at pinned
    versions matching `nostrnests/nests` `NestsUI-v2`. Document rev.
16. Write `listen.ts` + `pcm-tap-worklet.ts` + `listen.html`. Mirror
    NostrNests' `transport/moq-transport.ts` Watch.Broadcast config.
17. Write `publish.ts` + `publish.html` (sine source via Oscillator
    → MediaStreamAudioDestinationNode).
18. Write `server.ts` (bun static + WebSocket back-channel, writes
    PCM to a file on disk).
19. Write Gradle task `interopBuildBrowserHarness` that runs
    `bun install && bun build`.
20. Write `PlaywrightDriver.kt` shelling out to `npx playwright test`
    or using `playwright-java` (Maven Central). Cert-pin via
    `--ignore-certificate-errors-spki-list` if feasible, else
    `--ignore-certificate-errors`. Expose `openListenPage` and
    `openPublishPage`.
21. Wire I1, I2, I3, I4 through `BrowserInteropTest.kt`.

### Phase 5 — Browser-only scenarios (0.5 day)

22. I13 (`framesPerGroup=50` against actual `Container.Consumer`):
    long broadcast, assert no eviction-driven silence beyond expected
    bounds.
23. I14 (WebCodecs warmup × CSD skip interaction): assert that with
    T8's CODEC_CONFIG filter active, the browser receives a normal
    decode after the standard 3-frame warmup (no extra warmup penalty).
24. I15 (`WT-Available-Protocols` negotiation): Playwright's
    `browser.newContext()` exposes the response headers; assert
    `WT-Protocol` matches `moq-lite-03` (or whatever we advertised).

## CI integration

Update `.github/workflows/<existing-workflow>.yml`:

```yaml
jobs:
  default:
    steps:
      - run: ./gradlew :nestsClient:jvmTest

  hang-interop:
    needs: default
    steps:
      - uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
      - uses: actions/cache@v3
        with:
          path: |
            ~/.cargo/registry
            ~/.cargo/git
            nestsClient/tests/hang-interop/target
          key: ${{ runner.os }}-cargo-${{ hashFiles('nestsClient/tests/hang-interop/Cargo.lock') }}
      - run: ./gradlew :nestsClient:jvmTest -DnestsHangInterop=true

  browser-interop:
    needs: default
    steps:
      - uses: actions-rs/toolchain@v1
      - uses: oven-sh/setup-bun@v1
      - run: npx playwright install --with-deps chromium
      - uses: actions/cache@v3
        with:
          path: |
            ~/.cargo/registry
            ~/.cache/ms-playwright
            nestsClient/tests/browser-interop/node_modules
          key: ${{ runner.os }}-browser-${{ hashFiles('nestsClient/tests/browser-interop/bun.lockb', 'nestsClient/tests/hang-interop/Cargo.lock') }}
      - run: ./gradlew :nestsClient:jvmTest -DnestsBrowserInterop=true
```

PR-level: all three. Browser-interop can run in parallel with
hang-interop.

## Test runtime budget

| Tier | Cold | Warm |
|---|---|---|
| Hang path full (12 scenarios × ~7 s) | +30 s cargo build first time | ~85 s |
| Browser path (8 scenarios × ~12 s) | +60 s bun install + Chromium boot | ~95 s |
| Combined warm | n/a | ~3 minutes |

Acceptable for PR-level CI.

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| `kixelated/moq` HEAD breaks pin | Pin git rev in `nestsClient/tests/hang-interop/Cargo.toml` + REV file. Bump deliberately. |
| moq-relay config schema changes between revs | Pin rev. Document `relay.toml` fields used. Smoke test: boot relay, assert known broadcast lookup works, in `@BeforeAll`. |
| Self-signed cert + Chromium WebTransport rejection | Use `--ignore-certificate-errors-spki-list` (preferred) or `--ignore-certificate-errors` for test-only Chromium instance. |
| JVM Opus encoder/decoder availability | If `:nestsClient` JVM target lacks Opus, vendor `audiopus` JNI or write a small wrapper. Reuse `MediaCodecOpusEncoder`/`Decoder` if a JVM `MediaCodec` polyfill exists; otherwise pure-Java `concentus` library is a fallback. Decide at Phase 1. |
| Port conflicts on CI | Use `ServerSocket(0).localPort` for relay + bun + WebSocket back-channel. |
| Cargo build cost in cold CI | Cache `target/` keyed on `Cargo.lock` hash. |
| Loss shim platform portability | Pure-Rust tokio shim works linux/mac/win. |
| Playwright flakiness in headless audio | Always pass `--enable-features=AutoplayPolicy=NoUserGestureRequired` + use AudioContext.resume() explicitly in the harness. |
| Audio comparison flake under loss | Use loose tolerance bounds + signal-domain assertions inherently noise-tolerant. |
| Sine wave vs real speech encoded differently | Document explicitly: sine catches wire-format + decoder correctness. Real-speech parity is a separate field-test concern, out of scope. |

## Definition of done

1. Hang path: I1–I4, I8, I10, I11, I12 green on a fresh CI run with
   no flake. **P0**.
2. Hang path: I5, I7, I9 green. **P1**.
3. Browser path: I1–I4, I13, I14 green on a fresh CI run with no
   flake. **P0**.
4. Browser path: I5, I7, I9, I10, I15 green. **P1**.
5. Audit-branch fixes T1–T14 each have ≥ 1 hang-tier AND/OR
   browser-tier scenario asserting their wire output. Gap matrix
   committed at `nestsClient/plans/2026-05-06-cross-stack-interop-test-gap-matrix.md`.
6. Both `-DnestsHangInterop=true` and `-DnestsBrowserInterop=true`
   in the default PR-level GitHub Actions config.
7. `nestsClient/tests/hang-interop/REV` and `nestsClient/tests/browser-interop/REV`
   document the pinned upstream revs.
8. New plan filed at
   `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
   (or similar) summarising what landed, any deviations from this
   plan, and any follow-ups discovered.

## Out of scope (intentionally)

- **moq-auth NIP-98 path**: existing `NostrNestsAuthInteropTest`
  covers it via Docker'd auth sidecar. Don't conflate.
- **Real microphone speech audio**: sine wave is sufficient for
  wire-format and decoder correctness. Real-speech parity is a
  separate field-test concern.
- **Real cellular network conditions**: I9's loss shim approximates;
  real cellular has bursty + asymmetric loss + variable RTT.
- **Real device speaker DAC + hardware**: PCM is captured pre-DAC;
  hardware playback is outside protocol scope.
- **Multi-rendition catalog**: nests audio rooms ship one rendition.
  Defer multi-bitrate to a separate task if/when needed.
- **Video tracks**: nests is audio-only.
- **iOS Kotlin/Native interop**: `:nestsClient` iOS target builds but
  the test runner is JVM. iOS-specific bugs are caught by the
  shared-`commonMain` code paths the JVM tests already exercise.
- **`moq-lite-04` ALPN bump**: separate task, requires Lite-04 codec
  diff (Announce.hops as OriginList, AnnounceInterest.exclude_hop,
  Probe.rtt). Don't roll into this plan.

## When picking up

This plan is self-contained. The agent should:

1. Read the audit branch's audio-path commits to understand which
   wire shapes each scenario must validate
   (`git log main..origin/claude/debug-audio-dropout-n0g6Z --oneline | grep nests`).
2. Re-clone `kixelated/moq` to `/tmp/moq` if not present:
   `git clone --depth=1 https://github.com/kixelated/moq.git /tmp/moq`.
   Confirm `/tmp/moq/rs/moq-relay`, `/tmp/moq/rs/hang`, `/tmp/moq/rs/moq-lite`,
   `/tmp/moq/js/lite`, `/tmp/moq/js/watch`, `/tmp/moq/js/publish`,
   `/tmp/moq/js/hang` are all present (sparse-checkout if needed).
3. Implement Phases 1–2 + 4 (the P0 deliverable). Land Phase 3 + 5 in
   a follow-up if budget runs short.
4. After every phase: run the relevant Gradle task and confirm
   green. Don't proceed to the next phase until the prior is clean.
5. Each P0 scenario commits separately. Phase 4 may bundle multiple
   commits since the harness setup is one logical chunk.

For protocol reference:

- `kixelated/moq/rs/hang/src/catalog/`: catalog Root + Audio + Container schema.
- `kixelated/moq/rs/hang/src/container/legacy.rs`: legacy varint+payload format.
- `kixelated/moq/rs/moq-lite/src/lite/{publisher,subscribe,announce,group,version,probe}.rs`: moq-lite wire.
- `kixelated/moq/js/watch/src/{broadcast.ts,audio/{decoder,source}.ts}`: browser listener flow.
- `kixelated/moq/js/publish/src/{broadcast.ts,audio/encoder.ts}`: browser publisher flow.
- Existing audit findings for which wire shapes each fix targets:
  this repo's `nestsClient/plans/2026-04-26-moq-lite-gap.md`.

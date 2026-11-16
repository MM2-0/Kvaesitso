# Plan: I4 stereo cross-stack interop scenario

> **Status:** shipped — production per-stream channel-count change merged (PR #2755); forward + reverse + browser stereo scenarios landed.
> _Audited 2026-06-30._

**Status:** ✅ Landed. Production-side change merged to main as
PR #2755 (`refactor(nests): per-stream channel count +
AudioBroadcastConfig`). Test scenarios merged into
`claude/cross-stack-interop-test-XAbYB`:
- `HangInteropTest.amethyst_speaker_to_hang_listener_stereo_440_660`
  (forward)
- `HangInteropTest.rust_hang_publish_stereo_to_kotlin_listener_440_660`
  (reverse)
- `BrowserInteropTest.chromium_listener_stereo_440_660`
  (forward, browser-tier)

The spec text below is preserved for archaeology.

**Origin:** `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`
table row I4: "Stereo Opus (`numberOfChannels=2`); freq differs L/R
(440 / 660)". Both forward (Amethyst speaker → hang-listen) and
reverse (hang-publish → Amethyst listener) are P0.

**Branch convention:** new branch — don't fold into the cross-
stack-test branch. Suggested name `feat/nests-stereo-broadcast`.

## Goal

End-to-end verify that an Amethyst Kotlin speaker broadcasting a
stereo Opus stream is intelligible to the reference
`hang-listen` Rust binary, and vice versa, with a different
frequency on each channel asserted independently in the decoded
PCM. The scenario lands as two new tests on the existing
`HangInteropTest` suite gated by `-DnestsHangInterop=true`.

## What's blocking I4 today

Three pieces of `:nestsClient` production code hard-code mono:

1. **Catalog rendition** — `MoqLiteHangCatalog.opusMono48k(...)` is
   the only catalog factory and it pins
   `numberOfChannels = 1`. The cached
   `OPUS_MONO_48K_AUDIO_DATA_JSON_BYTES` is the only catalog payload
   the speaker ships (referenced in `MoqLiteNestsSpeaker.kt:160`
   and `ReconnectingNestsSpeaker.kt:589`). A stereo broadcast
   needs a new catalog factory + a new cached payload.

2. **Audio format** — `AudioFormat.CHANNELS = 1` is a top-level
   constant in `nestsClient/src/commonMain/.../audio/Audio.kt`.
   Used by `MediaCodecOpusEncoder` (encoder configuration) and
   `MediaCodecOpusDecoder` (decoder configuration). A stereo
   broadcast can't simply override this without breaking every
   mono call site.

3. **Listener decoder** — `MoqLiteNestsListener` constructs the
   `OpusDecoder` with `channelCount = 1` (search the file). For
   listener-side stereo support it needs to read the catalog's
   `numberOfChannels` and pass that to the decoder factory.

The encoder itself (`MediaCodecOpusEncoder`) already accepts a
`channels: Int` constructor parameter on Android — but it's never
called with `2`. Same for `JvmOpusEncoder` (test-side, already
supports stereo via `channelCount`).

## Production-side change set

### 1. Make `AudioFormat.CHANNELS` a per-stream concern, not a global

Rename / repurpose:

```kotlin
object AudioFormat {
    const val SAMPLE_RATE_HZ: Int = 48_000
    /** Default mono — most call sites don't override. */
    const val DEFAULT_CHANNELS: Int = 1
    const val FRAME_SIZE_SAMPLES: Int = 960
    const val FRAME_DURATION_US: Long = 20_000
    const val BYTES_PER_SAMPLE: Int = 2
}
```

Audit every reference to `AudioFormat.CHANNELS`. Each call site
that's actually mono-fixed (e.g. mic capture defaults) should
inline `1`; everything else should take a `channelCount` parameter.

This is the largest part of the change — concrete files to touch:

- `MediaCodecOpusEncoder.kt` (Android): already has
  `channels: Int = 1` constructor parameter, no change needed.
- `MediaCodecOpusDecoder.kt` (Android): same.
- `JvmOpusEncoder.kt` (jvmTest): no change.
- `JvmOpusDecoder.kt` (jvmTest): no change.
- `AudioRecordCapture` (Android microphone source): keep mono
  hard-coded — the microphone is a single-channel device.
- `NestPlayer` / `AudioPlayer`: needs to know the rendition's
  channel count to size its mixer / `AudioTrack` config. Add
  a `channelCount` parameter to `AudioPlayer.start()` (or a
  per-stream `AudioPlayerFactory(channelCount)` interface).
- `NestMoqLiteBroadcaster.peakAmplitude(pcm: ShortArray)`:
  currently treats the array as planar mono. Stereo PCM is
  interleaved L/R; the function must compute peak across both
  channels. Either iterate stride-2 explicitly or pass
  `channelCount` and skip the level callback for stereo.

### 2. Catalog factory + cache

Drop `MoqLiteHangCatalog.OPUS_MONO_48K_AUDIO_DATA_JSON_BYTES`'s
status as the only fast-path catalog. Either:

A) Add a parallel `OPUS_STEREO_48K_AUDIO_DATA_JSON_BYTES` constant
   built from `opusMono48k(...).copy(audio.renditions[X].copy(numberOfChannels=2))`
   shape, OR

B) Generalise to `opus48k(audioTrackName, numberOfChannels)` and
   memoise both shapes via a small map keyed on
   `(trackName, numberOfChannels)`.

(B) is cleaner — it generalises to future `(48k, 2 channels, 64
kbit/s)` etc. variants without exploding the constant count.
The wire shape stays byte-stable per-shape because
`encodeJsonBytes()` already pins `encodeDefaults = false` +
`explicitNulls = false` + a deterministic field order.

Wire `MoqLiteNestsSpeaker.startBroadcasting` and
`ReconnectingNestsSpeaker` to read the channel count from a
`broadcastConfig: AudioBroadcastConfig` parameter (new) — pass
it through to the catalog factory. Default
`AudioBroadcastConfig(channelCount = 1)` so existing callers are
unaffected.

### 3. Listener decoder discovery

`MoqLiteNestsListener.subscribeSpeaker` today builds the decoder
with mono. It already subscribes to the catalog track in
parallel — the patch is to **block on the first catalog message**,
read `audio.renditions[<audioTrack>].numberOfChannels`, and
construct the `OpusDecoder` with that count.

Concrete change: make `subscribeSpeaker` `suspend`-await
`hang::CatalogConsumer::next()` once before opening the audio
subscription, plumb the discovered channel count into the
`OpusDecoder` factory call.

Edge case: if the catalog's `numberOfChannels` field is missing
(older publishers), default to 1 (matches the kdoc on
`MoqLiteHangCatalog.AudioRendition.numberOfChannels`).

## Test side

### Stereo `SineWaveAudioCapture`

Extend the existing test fixture to support stereo:

```kotlin
class SineWaveAudioCapture(
    private val freqHz: Int = 440,
    /**
     * Per-channel frequency overrides. If null, every channel
     * runs at [freqHz] (matches mono-broadcast-of-a-stereo).
     * If non-null, must have [channelCount] entries — useful
     * for I4 where left = 440 and right = 660 lets the FFT
     * assertion bisect each channel cleanly.
     */
    private val freqHzPerChannel: IntArray? = null,
    private val channelCount: Int = 1,
    private val amplitude: Short = 16_383,
) : AudioCapture {
    override suspend fun readFrame(): ShortArray? {
        val out = ShortArray(FRAME_SIZE_SAMPLES * channelCount)
        for (i in 0 until FRAME_SIZE_SAMPLES) {
            for (ch in 0 until channelCount) {
                val freq = freqHzPerChannel?.get(ch) ?: freqHz
                val v = (amplitude * sin(2.0 * PI * freq * (sampleIdx + i) / SAMPLE_RATE_HZ)).toInt()
                out[i * channelCount + ch] = v.toShort()
            }
        }
        // … existing pacing + sampleIdx update …
    }
}
```

Pacing logic stays as-is (one `delay(FRAME_NANOS / 1M)` per frame).

### `PcmAssertions.assertFftPeak` per-channel

The existing helper assumes mono (interprets the array as
planar samples). For stereo, add an overload:

```kotlin
fun assertFftPeakStereo(
    interleaved: FloatArray,
    expectedHzL: Double,
    expectedHzR: Double,
    halfWindowHz: Double = 5.0,
    sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
)
```

That deinterleaves into two `FloatArray`s and runs the existing
FFT assertion on each. ZCR can be done the same way.

### `hang-listen` already supports stereo

Verify by reading the existing
`nestsClient/tests/hang-interop/hang-listen/src/main.rs`: it already passes
`audio_cfg.channel_count` into `opus::Decoder::new(...)` and
allocates a stereo PCM buffer if the catalog says so. No Rust
change needed.

### `hang-publish` already supports stereo

Verify by reading `nestsClient/tests/hang-interop/hang-publish/src/main.rs`:
the `--channels <1|2>` flag plumbs through to the catalog, the
opus encoder, and the sine generator. No Rust change needed.
*(Note: the current sine generator uses the same frequency on
both channels. For I4 reverse-direction we'd want
`--freq-hz-l 440 --freq-hz-r 660` to generate a true L/R
asymmetric tone. Small Rust addition.)*

### Two new HangInteropTest scenarios

```kotlin
/** I4 forward — Kotlin speaker broadcasts L=440 R=660 stereo. */
@Test
fun amethyst_speaker_to_hang_listener_stereo_440_660() = runBlocking {
    val out = runSpeakerToHangListen(
        speakerSeconds = 5,
        captureFirstFrame = false,
        captureFactoryOverride = {
            SineWaveAudioCapture(
                channelCount = 2,
                freqHzPerChannel = intArrayOf(440, 660),
            )
        },
        encoderFactoryOverride = { JvmOpusEncoder(channelCount = 2) },
        broadcastConfig = AudioBroadcastConfig(channelCount = 2),
    )
    val pcm = readFloat32Pcm(out.pcmFile)
    val warmup = AudioFormat.SAMPLE_RATE_HZ / 25 * 2  // stereo: skip 2x
    val analysed = pcm.copyOfRange(warmup, pcm.size)
    PcmAssertions.assertFftPeakStereo(analysed, 440.0, 660.0)
}

/** I4 reverse — hang-publish R/L stereo → Kotlin listener. */
@Test
fun rust_hang_publish_stereo_to_kotlin_listener_440_660() = runBlocking {
    // … hang-publish with --channels 2 --freq-hz-l 440 --freq-hz-r 660 …
    // … Kotlin listener via connectNestsListener; decoder discovers
    //   numberOfChannels = 2 from the catalog and builds a stereo
    //   JvmOpusDecoder …
    // … assert FFT peaks per channel as above …
}
```

`runSpeakerToHangListen` needs three new optional parameters
(`captureFactoryOverride`, `encoderFactoryOverride`,
`broadcastConfig`); existing callers pass nothing and keep mono
behavior.

## Phases

Total: ~1.5 days.

**Phase 1 — production-side prep (~5 hr).**
1. Refactor `AudioFormat.CHANNELS` → audit + per-stream parameterisation.
2. Generalise `MoqLiteHangCatalog.opusMono48k` to `opus48k(name, channels)`
   + memoise the JSON bytes per shape.
3. Plumb `AudioBroadcastConfig(channelCount)` through
   `connectNestsSpeaker` / `MoqLiteNestsSpeaker` / `ReconnectingNestsSpeaker`.
4. Plumb catalog-discovered channel count through
   `connectNestsListener` / `MoqLiteNestsListener`.

Verify Android + Desktop builds compile; existing mono Kotlin↔Kotlin
tests stay green.

**Phase 2 — test-side fixtures (~2 hr).**
5. Extend `SineWaveAudioCapture` with `channelCount` /
   `freqHzPerChannel`.
6. Add `PcmAssertions.assertFftPeakStereo` /
   `assertZeroCrossingRateStereo` (deinterleave + run mono helpers).
7. Extend `runSpeakerToHangListen` with capture/encoder/config
   overrides.

**Phase 3 — Rust publisher tweak (~30 min).**
8. Add `--freq-hz-l` / `--freq-hz-r` to `hang-publish` so the
   reverse direction has a per-channel asymmetric tone. Default
   to `--freq-hz` value for both when unset (back-compat).
   Rebuild via `cargo build --release -p hang-publish`.

**Phase 4 — wire I4 forward + reverse (~3 hr).**
9. Land `amethyst_speaker_to_hang_listener_stereo_440_660` in
   `HangInteropTest`.
10. Land `rust_hang_publish_stereo_to_kotlin_listener_440_660`
    in `HangInteropTest` (or a new `HangInteropReverseTest` if
    the file gets too long).
11. Verify both green with 3 sequential
    `./gradlew :nestsClient:jvmTest -DnestsHangInterop=true
    --rerun-tasks` runs.

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| `AudioFormat.CHANNELS = 1` audit misses a call site | Grep across the entire repo (`amethyst/`, `commons/`, `desktopApp/`, `nestsClient/`) for `AudioFormat.CHANNELS` and `CHANNELS = 1`; change each by hand. |
| Android `AudioTrack` channel-mask change breaks mono playback | The default constant stays `DEFAULT_CHANNELS = 1`; existing call sites that omit `channelCount` keep mono behavior. Mono regression test (`NestPlayerTest`) catches drift. |
| Listener-side catalog-await blocks subscription forever if the publisher never emits the catalog | Use `withTimeoutOrNull(2_000)` around the catalog read; default to mono on timeout (with a warning log) to preserve the failure-tolerant existing behavior. |
| Stereo Opus interleaved PCM byte-order surprises (LE vs BE on the wire) | Opus is endianness-neutral on the wire; PCM in the codec API is always native-endian short[]. Deinterleave in software. |
| `opusMono48k` callers in `:commons` (the parser) aren't tested in this plan | The parser is deserialise-only and accepts any rendition map. Verify by adding one unit test in `:commons` that round-trips a stereo catalog. |
| The catalog hook race (Phase 2 results doc) shows up worse for stereo because of the larger initial frame | Same fix as I1: keep `framesPerGroup = 5` and have the test sequence speaker.startBroadcasting → delay 150 ms → spawn hang-listen. The race is a pre-existing condition, not stereo-specific. |

## Definition of done

1. `HangInteropTest.amethyst_speaker_to_hang_listener_stereo_440_660`
   green, 3 sequential `--rerun-tasks` runs no flake.
2. `HangInteropTest.rust_hang_publish_stereo_to_kotlin_listener_440_660`
   green, same stability.
3. Existing mono tests (`amethyst_speaker_to_hang_listener_static_tone_440`,
   `late_join_listener_still_decodes_tail`,
   `mid_broadcast_mute_shortens_decoded_pcm`,
   `subscribe_drop_for_unknown_track`,
   `long_broadcast_60s_tone_round_trips`,
   `rust_hang_publish_to_rust_hang_listener_round_trip_440`) stay
   green.
4. The `KotlinSpeakerKotlinListenerThroughNativeRelayTest`
   diagnostic still passes when run with
   `-DnestsHangInteropDiagnostic=true`.
5. Android instrumented tests on a real device: at least one
   stereo broadcast end-to-end through the `nostrnests/nests`
   Docker harness (gated by `-DnestsInterop=true`). The
   production flow has to work, not just the cross-stack
   `:nestsClient` sub-suite.
6. Results filed at
   `nestsClient/plans/2026-05-06-i4-stereo-cross-stack-scenario-results.md`
   summarising what landed, any deviations from this plan, and
   any production code follow-ups discovered during the audit.

## Out of scope (intentionally)

- **Multi-bitrate per channel.** Stereo as one 64 kbit/s rendition
  is enough; per-channel rate-tuning is a renderer concern.
- **5.1 / spatial audio.** Catalog field is `numberOfChannels`,
  but the audio pipeline assumes interleaved planar — beyond
  stereo would need a separate plan.
- **Browser side I4.** `nestsClient/tests/browser-interop/` doesn't
  exist yet (Phase 4 of the parent plan); when it lands the
  same I4 shape ports straight to a `BrowserInteropTest`.
- **Per-channel mute.** The existing `setMuted(true)` mutes the
  whole broadcast; a per-channel mute is a UI concern out of
  scope here.

## When picking up

This plan is self-contained. The agent should:

1. Read `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`
   (the parent plan) and
   `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
   (Phase 1 + 2 status) to understand the harness.
2. Skim `nestsClient/src/jvmTest/.../interop/native/HangInteropTest.kt`
   for the existing scenario shape — the stereo test reuses the
   same `runSpeakerToHangListen` helper.
3. Implement Phase 1 (production prep) FIRST, with the existing
   mono tests as the regression net. Don't mix production
   refactors with the I4 test wiring — keep two clean commits.
4. After every phase: run `./gradlew :nestsClient:jvmTest
   -DnestsHangInterop=true` and confirm green. Don't proceed to
   the next phase until the prior is clean.
5. Each scenario commits separately. The production-side
   refactor (Phase 1) commits as a single unit.

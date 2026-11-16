# Brief: upgrade LightCompressor-enhanced from 2.2.3 to 2.3.0, and tell the user what happens to HDR video

Status: implemented 2026-10-29 on branch `feat/lightcompressor-2.3.0-hdr`, first against the local
`2.3.0-SNAPSHOT`, then pinned to the released `2.3.0` from JitPack the same evening (step 6 done);
see [the report](2026-10-28-lightcompressor-2.3.0-upgrade-report.md). Device check (steps 4
and 5) passed on the Pixel 9a the same day; draft PR
[#4684](https://github.com/vitorpamplona/amethyst/pull/4684).
The `CompressionHandle` from LightCompressor PR #53 is adopted (section C, step 2; commit
`28023020fa`) and the step 4 cancel case passed on the Pixel 9a the same day; it also surfaced a
library gap (a cancel landing after the last frame leaves the finished output; see the report's
Findings), fixed in the library as bead 075 and re-verified here against the 16:47 snapshot: four
runs, `files/` empty every time. (Written 2026-10-28 from the
LightCompressor session, HDR hint added 2026-10-29.)

Amethyst depends on `com.github.davotoula:LightCompressor-enhanced:2.2.3`
(`gradle/libs.versions.toml`, key `lightcompressor-enhanced`). 2.3.0 is not tagged yet. The branch
of LightCompressor PR #52 (`feat/hdr-probe` at `1575e14`, which is master `c17e65d` plus the HDR
probe) is published to the local Maven repository as `2.3.0-SNAPSHOT`:

```
~/.m2/repository/com/github/davotoula/LightCompressor-enhanced/2.3.0-SNAPSHOT/
```

`settings.gradle.kts` already lists `mavenLocal()` first, so changing the version is enough to
build against it. The curated release notes draft is at
`/Users/david/StudioProjects/LightCompressor-fork/LightCompressor/docs/releases/2.3.0.md`; the
library README's "HDR video" (including "Checking a video before compression") and "Storage
Options" sections describe the new behaviour.

## How Amethyst uses the library (checked 2026-10-28)

- `service/uploads/VideoCompressionHelper.compressVideo`: one `VideoCompressor.start` call per
  video with `isStreamable = true`, `AppSpecificStorageConfiguration()`, and a `Configuration`
  with `videoBitrateInBps`, a `VideoResizer.limitShortSide` (wrapped in `SquarePixelsResizer`
  for anamorphic sources), `videoNames = listOf(UUID)`, `isMinBitrateCheckEnabled = false` and
  `videoCodec`. It is wrapped in `withTimeoutOrNull(60 s)` around a
  `suspendCancellableCoroutine`, and it uses the `path` that `onSuccess` reports.
- The codec comes from the `useH265` boolean that every composer threads from its dialog's
  "Use H.265/HEVC Codec" switch (`SettingSwitchItem` in `ImageVideoDescription` and
  `NewMediaView`, shown only when `VideoUploadControls.videoWillBeCompressed`) down through
  `MediaUploader.upload` / `compressIfNeeded`, `MediaCompressor.compress` and
  `VideoCompressionHelper`. `useH265` appears in about 40 files across `commons`, `commonsUI`,
  `amethyst` and `desktopApp`. The chat file dialog has no codec switch.
- HLS: `HlsUploadHelper.run`, `HlsConfig(codec, ladder)` and `SimpleHlsListener` in
  `ui/screen/loggedIn/video/hls/HlsPublishOrchestrator`. The HLS public API has not changed
  since 2.2.3.
- Not used anywhere: `VideoCompressor.cancel()`, a custom `StorageConfiguration`,
  `CacheStorageConfiguration`, `Compressor.isRunning`, `Compressor.compressVideo`,
  `CompressorUtils.setOutputFileParameters`, the string `_temp`.
- `MediaCompressorTest` mocks `VideoCompressor.start(any() × 6)`. That signature is unchanged.
- The platform abstraction is `commons/.../service/uploads/MediaUploader.kt` (interface with
  defaults; `UnavailableMediaUploader` for front ends without a pipeline), implemented by
  `AndroidMediaUploader(appContext)` and `DesktopMediaUploader`. On Android `MediaUri` is a
  typealias for `android.net.Uri`. The interface already has an optional
  `suspend fun probeVideo(uri): VideoInfo?` that `MediaQualitySlider` calls through
  `produceState` to label the slider.

## Breaking changes in 2.3.0 and what each means here

| Change | Effect on Amethyst |
|---|---|
| `Configuration` gained `hdrMode`; source compatible, not binary compatible for Kotlin callers compiled against 2.2.x | None beyond the rebuild a version bump always causes. |
| Consumers need Kotlin 2.3 or newer (AAR carries Kotlin 2.4 metadata) | None; Amethyst is on Kotlin 2.4.21. |
| `Compressor.isRunning` removed; `CompressorUtils.setOutputFileParameters` internal; `Compressor.compressVideo(streamable: Boolean)` | None; not used. |
| Streamable outputs are named `<name>.mp4`, not `<name>_temp.mp4` | Transparent: Amethyst reads the path from `onSuccess`. The file is now `filesDir/<uuid>.mp4`. Grep once for any cleanup that lists `filesDir` by pattern; none was found on 2026-10-28. |
| `StorageConfiguration.createFileToSave` is called twice per video, never with the `_temp` name | None; no custom configuration. |

So the compile-level upgrade is a one-line version change. The work is in the behaviour changes
below and in the HDR hint (section F).

## Behaviour changes that need a decision

### A. HDR input is now handled, and the default keeps it

`Configuration.hdrMode` defaults to `HdrMode.KEEP_IF_SUPPORTED`:

- HLG or PQ input, `VideoCodec.H265`, and a device whose HEVC encoder supports HDR editing
  (Android 13+): output stays 10-bit HDR (HEVC Main 10, HLG stays HLG, PQ becomes HDR10).
- Everything else, including every `VideoCodec.H264` job: tone mapped to 8-bit BT.709 SDR.
- HLS renditions always tone map.

2.2.3 produced 8-bit Main with HLG tags (see the 2026-10-24 brief in the Amethyst scratchpad,
`hdr-primal/BRIEF-lightcompressor-hdr.md`: pixels correct, container unusual, banding risk).
Both new outcomes are better than that, and neither produces the cyan-blotch failure seen on
Primal uploads (8-bit BT.709 tags without tone mapping).

Decide one of:

1. **Keep the default** (recommended). HDR uploads from capable devices stay HDR when the user
   chose H.265; everything else is proper SDR. Section F then tells the user which of the two
   they are getting.
2. **Pass `hdrMode = HdrMode.TONE_MAP_TO_SDR`** for every upload, for maximum player
   compatibility and predictable bitrate. Section F shrinks to one line ("HDR video is converted
   to SDR").

One known cost of option 1: on the Pixel 9a's Exynos HEVC encoder, a keep-HDR encode overshoots
the requested bitrate by about 1.9x (LightCompressor bead 6k1, open). Amethyst's
`VideoCompressionRules` bitrates will be exceeded for those uploads until that is fixed. If that
matters more than HDR fidelity, pick option 2 now and revisit.

### B. A stalled encoder now fails fast

The library's encoder watchdog fails a job whose encoder stops producing output for 10 s, with
`onFailure("The device's video encoder stopped responding. Try again; closing other apps that
play or record video may help.")`. On 2.2.3 such a job hung until Amethyst's 60 s timeout and
left the encoder running. No code change needed; `notifyUser` already shows the message. Note
the message is English only, like the library's other failure messages.

### C. Cancellation is per `start()` call, which makes cancelling on timeout possible

On 2.2.3 Amethyst never calls `VideoCompressor.cancel()`. When the 60 s timeout fires, or the
caller's coroutine is cancelled, the library keeps encoding to the end and the finished file
stays in `filesDir` with nobody holding its path.

2.3.0 guarantees `cancel()` only affects batches that are running, never a `start()` made later,
and that every video gets exactly one terminal callback (`onCancelled` for cancelled ones).
That makes this safe:

```kotlin
suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { VideoCompressor.cancel() }
    VideoCompressor.start(...)
}
```

Caveat: `cancel()` cancels **every** running batch. If two video compressions can run at the
same time (two attachments compressing concurrently, or the composer preview re-compressing
while an upload compresses), cancelling one cancels both. Check `MediaCompressor` and its
callers for concurrency before adopting; if compressions are serialised, adopt it; if not,
record a library request for a per-`start()` handle instead and leave the behaviour as is.

Either way, an orphaned output on timeout is pre-existing. If cancel is not adopted, consider
deleting the file in `onSuccess` when `continuation.isActive` is false.

**Decided 2026-10-29: not adopted.** `MultiOrchestrator.upload` launches one job per attachment,
so two videos do compress at the same time and `cancel()` would stop the sibling upload. The
orphan is deleted in `onSuccess` instead. **Library request** (LightCompressor-enhanced): a
per-`start()` handle (`start(...)` returning something with `cancel()`, or `cancel(batchId)`).

**Library request done (2026-10-29, LightCompressor PR #53, bead 7og).** `VideoCompressor.start()`
now returns a `CompressionHandle`; its `cancel()` cancels that one `start()` call's videos and
leaves other batches running, and calling it after the batch finished does nothing. The local
Maven `2.3.0-SNAPSHOT` has been republished from that branch (AAR of 2026-10-29 13:29 contains
`CompressionHandle`), so this branch can adopt it before the tag. Change in
`VideoCompressionHelper.compressVideo`, inside the existing `suspendCancellableCoroutine` block:

```kotlin
suspendCancellableCoroutine { continuation ->
    val handle = VideoCompressor.start(/* unchanged arguments */)
    continuation.invokeOnCancellation { handle.cancel() }
}
```

Registering after `start()` returns is fine: if the continuation was already cancelled when
`invokeOnCancellation` is called, the handler runs at once. The timeout and a cancelled post then
stop the encode within a frame (or within 1 s through the library's watchdog) instead of letting
it run to the end. The library sends `onCancelled` for the cancelled video, which the existing
`onCancelled` override handles (`resume(null)` guarded by `isActive`, already false).

Keep the `!continuation.isActive` deletion branch in `onSuccess`: a video that had already
returned its result when the cancel landed still reports `onSuccess`, and that file still needs
deleting. The handle saves the wasted encode, not that code. No new test is possible on the JVM
(needs the real library); the device check in step 4 gains one case: cancel the post (or let a
long clip hit the 60 s timeout) and confirm `logcat` shows the compression ending with
"Video compression cancelled" rather than running on, and that `files/` is empty afterwards.

### D. Inputs without a MediaStore path are no longer copied

2.2.3 copied `file://` and document Uris into `dataDir/<millis>` before compressing and never
deleted the copy. Amethyst passes photo-picker content Uris, which have a MediaStore path, so it
was most likely never affected. If any caller passes a `Uri.fromFile(...)` to
`compressVideo`, old installs may hold leaked copies under the app's data dir. No action unless
such a caller exists.

### E. Only good news

Dolby Vision 8.x input (iPhone HDR recordings) compresses from its HEVC base layer instead of
failing. Older OMX codecs' `INFO_OUTPUT_BUFFERS_CHANGED` no longer aborts a job.

## F. Tell the user what happens to an HDR video (new API: `VideoCompressor.probeHdr`)

2.3.0 adds `VideoCompressor.probeHdr(context, uri): HdrProbe` (synchronous, `@WorkerThread`,
never throws). `HdrProbe.input` is `HdrInput.SDR`, `HLG` or `PQ` (SDR, untagged and unreadable
video all report `SDR`; Dolby Vision 8.x reports its base layer's transfer), and
`HdrProbe.canKeepHdr` is true when compressing that video as H.265 with `KEEP_IF_SUPPORTED`
would keep its HDR on this device. It is answered from the same policy call compression makes,
so what it says and what compression does can't drift.

### What to build

With option A.1 the codec switch already decides HDR: H.265 keeps it where the device can,
H.264 converts it. So the dialog needs no new control, only a line that says which of the two
the picked video gets, and that appears only when at least one picked video is HDR. Three
outcomes:

| Picked videos | Switch | Line under the H.265 switch |
|---|---|---|
| none HDR, or not probed yet | any | nothing (today's UI) |
| at least one HDR | H.265 on, and that video can be kept | "Your HDR video stays HDR. Some apps and browsers show HDR video washed out or too dark." |
| at least one HDR | H.264 | "Your HDR video is converted to standard (SDR) video. Turn on H.265 to keep HDR." |
| at least one HDR | H.265 on, none can be kept | "Your HDR video is converted to standard (SDR) video; this device can't keep HDR when compressing." |

An explicit "Keep HDR" toggle (H.265 *and* SDR output) would need a new boolean threaded next
to `useH265` through the roughly 40 files listed above, for a combination few people want. Not
in this brief; if it is wanted later, the probe result and the hint logic below are what it
would gate on, as the LightCompressor sample app does (`MainUiState.keepHdrBlocker`).

### Design

**`commons` (common code, platform neutral):**

- `data class VideoHdrInfo(val isHdr: Boolean, val canKeepHdr: Boolean)` in
  `commons/src/commonMain/.../service/uploads/video/`, next to `VideoInfo`. Desktop (ffmpeg)
  can implement it later from `ffprobe`'s `color_transfer`; nothing here is Android-specific.
- `MediaUploader`: `suspend fun probeHdr(uri: MediaUri): VideoHdrInfo? = null` beside
  `probeVideo`, with the same "null when unknown" contract. `UnavailableMediaUploader` and the
  test fakes (`object : MediaUploader by UnavailableMediaUploader`) inherit the default and
  show no line.
- The decision as a pure function so it is testable without Compose, in the style of
  `VideoUploadControls`:

  ```kotlin
  enum class HdrUploadOutcome { KEPT, CONVERTED_BY_CODEC, CONVERTED_BY_DEVICE }

  /** Null when no picked video is HDR, or none has been probed yet. */
  fun hdrUploadOutcome(probes: List<VideoHdrInfo>, useH265: Boolean): HdrUploadOutcome? =
      when {
          probes.none { it.isHdr } -> null
          !useH265 -> HdrUploadOutcome.CONVERTED_BY_CODEC
          probes.any { it.canKeepHdr } -> HdrUploadOutcome.KEPT
          else -> HdrUploadOutcome.CONVERTED_BY_DEVICE
      }
  ```

  Unit test in `commons/src/jvmTest/.../service/uploads/` next to `VideoUploadControlsTest`:
  empty list, all SDR, HDR + H.264, HDR + H.265 + keepable, HDR + H.265 + not keepable, and a
  mixed list where only the second video is HDR.

**`amethyst` (Android):**

- `AndroidMediaUploader.probeHdr`:

  ```kotlin
  override suspend fun probeHdr(uri: MediaUri): VideoHdrInfo =
      withContext(Dispatchers.IO) {
          VideoCompressor.probeHdr(appContext, uri).let { VideoHdrInfo(it.input != HdrInput.SDR, it.canKeepHdr) }
      }
  ```

  `probeHdr` opens the file with a `MediaExtractor`; one open per picked video, negligible next
  to the compression that follows. A slow cloud-backed Uri can block the call (same exposure as
  compression itself); the dialog just shows no line until it returns.

**`commonsUI`:**

- A composable `VideoHdrHint(outcome: HdrUploadOutcome?)` in
  `ui/note/creators/uploads/MediaQualitySlider.kt` next to `VideoCompressionNeedsFfmpegHint`,
  same `bodySmall`/grey style, rendering nothing for null.
- In `ImageVideoDescription` and `NewMediaView`, where the H.265 `SettingSwitchItem` is shown
  (`controls.videoWillBeCompressed`): probe every picked video, not just `firstVideo()`, so a
  post with an SDR clip first and an HDR clip second still gets the line:

  ```kotlin
  val videos = remember(uris) { (0 until uris.size()).map { uris.get(it).media }.filter { it.isVideo() == true } }
  val probes by produceState(emptyList<VideoHdrInfo>(), videos) {
      value = videos.mapNotNull { uploader.probeHdr(it.uri) }
  }
  VideoHdrHint(hdrUploadOutcome(probes, useH265Codec))
  ```

  In `NewMediaView` the switch state is `postViewModel.useH265Codec`. Removing a video changes
  the same orchestrator instance (see the comment in `MediaQualitySlider`), so key the probe on
  the list of media, not on the orchestrator.
- The chat file dialog (`ChatFileUploadDialog`) has no codec switch and `ChatFileUploadState`
  has no codec field, so its upload call passes a constant `useH265`; find it. If it is false, show the
  `CONVERTED_BY_CODEC` line there too but without the "Turn on H.265" sentence (a fourth
  string), or leave the chat dialog alone. Either is fine; say which in the report.
- Strings in `commonsUI/src/commonMain/composeResources/values/strings.xml`, next to
  `video_codec_h265_description` (line ~5957): `video_hdr_kept`, `video_hdr_converted_codec`,
  `video_hdr_converted_device`, with the texts from the table above. English only: Crowdin
  manages the translations (`AGENTS.md`), and the file is the `commonsUI` one even though the
  probe is Android-only.

### Not in scope

- Changing the default `hdrMode` (that is decision A).
- A "Keep HDR" toggle (see above).
- Probing HDR on desktop. The default `null` keeps desktop's UI as it is.

## G. Give the Android quality slider the picked video's real standard (`probeVideo`)

Not a 2.3.0 change, but the same interface and the same files, so it rides along.

`MediaUploader.probeVideo(uri): VideoInfo?` is what `MediaQualitySlider` calls (through
`produceState`, on the first picked video) to label the current stop with the picked video's own
standard and target, for example "4K video → 2160p, 8 Mbps"
(`media_compression_video_target`). When it returns null the slider prints the generic
example "1080p video → 1080p, 4 Mbps" (`media_compression_video_target_example`) whatever the
clip is. `DesktopMediaUploader` overrides it (ffprobe); `AndroidMediaUploader` doesn't, so on
phones the label is always the example.

Android already computes exactly this `VideoInfo` for compression:
`VideoCompressionHelper.getVideoInfo(uri, context, pixelAspectRatio)` (private, line ~231) reads
width, height, rotation and capture frame rate with `MediaMetadataRetriever`, corrects an
anamorphic width with the pixel aspect ratio from `videoPixelAspectRatio(context, uri)`
(`VideoPixelAspectRatio.kt`, public), and returns null on any failure.

Change:

- `VideoCompressionHelper`: add `fun probe(context: Context, uri: Uri): VideoInfo? =
  getVideoInfo(uri, context, videoPixelAspectRatio(context, uri))`. `getVideoInfo` stays
  private; `compressVideo` keeps calling it as it does.
- `AndroidMediaUploader`:

  ```kotlin
  override suspend fun probeVideo(uri: MediaUri): VideoInfo? =
      withContext(Dispatchers.IO) { VideoCompressionHelper.probe(appContext, uri) }
  ```

  Two file opens per probe (`MediaExtractor` for the aspect ratio, `MediaMetadataRetriever`
  for the rest), once per picked video, off the main thread. Null stays "unknown" and keeps the
  example label, as on desktop.

No UI change: the slider already handles both cases, and `MediaQualitySliderUiTest` (desktop)
is unaffected. `getVideoInfo` needs a real `MediaMetadataRetriever`, so there is no JVM unit
test; the device check in step 4 covers it.

## Steps

1. `gradle/libs.versions.toml`: `lightcompressor-enhanced = "2.3.0-SNAPSHOT"`. Build
   `:amethyst:assembleDebug`, run `:amethyst:testDebugUnitTest` for `MediaCompressorTest`,
   `SquarePixelsResizerTest`, `HlsVideoEventBuilderTest`, `HlsPublishOrchestratorTest`, then the
   whole module's unit tests. `spotlessApply` runs from the Stop hook.
2. Decide A and C; implement C if adopted. **Added 2026-10-29:** C's library request is done
   (section C, "Library request done"); add the `invokeOnCancellation { handle.cancel() }` line
   against the republished snapshot.
3. Section F: `VideoHdrInfo` + `hdrUploadOutcome` + test (`commons`), `MediaUploader.probeHdr`
   + Android override, `VideoHdrHint` + wiring in the two dialogs (+ chat, if chosen), strings.
   Run `:commons:jvmTest`, `:commonsUI:jvmTest`, `:amethyst:testDebugUnitTest` and the desktop
   `MediaQualitySliderUiTest`, which must stay unaffected.
   Section G: `VideoCompressionHelper.probe` and the `AndroidMediaUploader.probeVideo` override.
4. Device check on the Pixel 9a with a debug build, compression on, upload to a Blossom server.
   Test clips: `orig2.mp4` (HLG) and `jf_sdr_hevc10_1080p_3M.mp4` (10-bit SDR) are in the
   LightCompressor test app's external files dir and in the hdr-primal scratchpad.
   - the slider label names the picked clip's own standard (for the 1080p SDR clip, "1080p video
     → …" from `media_compression_video_target`, not the "example" string; a 4K or portrait
     phone recording is the better check, since its label differs from the example);
   - an SDR clip: no HDR line; the compressed file is `filesDir/<uuid>.mp4`, it uploads, the
     post plays;
   - the HLG clip with the H.265 switch off: the "converted … Turn on H.265" line; switch on:
     the "stays HDR" line; the line disappears when the HLG clip is removed from a mixed pick;
   - the HLG clip with H.265: `ffprobe -show_streams` on the downloaded upload shows `hevc`,
     `Main 10`, `yuv420p10le`, `color_transfer=arib-std-b67`;
   - the same clip with H.264: `h264`, `yuv420p`, `bt709`, no cyan on skin or highlights;
   - a post containing each plays in Amethyst and in a desktop browser;
   - (added 2026-10-29, after the handle) cancel a post while its video compresses: `logcat`
     shows "Video compression cancelled" within a second or two, not a compression that runs
     to the end, and `files/` is empty afterwards.
5. After each run, `run-as com.vitorpamplona.amethyst.debug ls files/` shows no `_temp` or
   `transcode_*` leftovers (adjust the package id to the debug flavour in use).
6. Do not merge with `-SNAPSHOT`: JitPack has no snapshot builds. Once 2.3.0 is tagged and
   `https://jitpack.io/com/github/davotoula/LightCompressor-enhanced/2.3.0/LightCompressor-enhanced-2.3.0.pom`
   returns 200, set the version to `2.3.0`.
7. Optional cleanup, same PR: the `packaging { resources { pickFirsts... } }` block in
   `amethyst/build.gradle.kts` carries a TODO about LightCompressor's two MP4 parsers. The
   library has used only Android's `MediaMuxer` since before 2.2.3 and declares no mp4parser
   dependency. Run `./gradlew :amethyst:dependencies | grep -i -E 'mp4parser|isoparser'`; if
   nothing else brings `builddef.lst`, drop that line and the TODO.
8. Release notes live in `docs/changelog/` (one file per version, see its `README.md` and
   `TEMPLATE.md`): under the next version, one line naming the library version, that HDR videos
   now stay HDR when compressed with H.265 on capable devices (if option A.1 was kept), that
   the upload dialog says what happens to an HDR video, and that the quality slider on Android
   now names the picked video's resolution.

# Report: LightCompressor-enhanced 2.3.0 upgrade and HDR hint

Implements [2026-10-28-lightcompressor-2.3.0-upgrade.md](2026-10-28-lightcompressor-2.3.0-upgrade.md)
on branch `feat/lightcompressor-2.3.0-hdr`, created from `main` at `72fbcdb6ad`. Written 2026-10-29.

## Outcome

- Amethyst builds against LightCompressor-enhanced `2.3.0` from JitPack (tag `2.3.0`, release
  commit `8b05065`, which includes PR #53 and the bead 075 fix). The branch was developed against
  the local `2.3.0-SNAPSHOT` and pinned to the release on 2026-10-29 once JitPack served the tag.
  Compressing an HLG or PQ video with H.265 on a device whose HEVC encoder can keep HDR now
  produces 10-bit HDR; every other case tone maps to BT.709 SDR (library default `hdrMode`).
- The post composer and the media post show a line under the H.265 switch when at least one
  picked video is HDR, saying whether it stays HDR or is converted and why. The chat file dialog,
  which always compresses as H.264, shows a codec-less "converted" line.
- On Android the quality slider labels the stop with the picked video's own standard ("4K video →
  2160p, 8 Mbps") instead of the 1080p example.
- The 60 s timeout and a cancelled post stop the encode: `compressVideo` registers
  `invokeOnCancellation { handle.cancel() }` on the `CompressionHandle` that 2.3.0's `start()`
  returns (library PR #53). A compression that still finishes after the cancel deletes its output
  instead of leaving it in `filesDir`.
- `builddef.lst` is gone from the APK packaging block: no mp4parser or isoparser in the tree.

Commits (oldest first):

| Hash | Message |
|---|---|
| `f4ce410df9` | feat(upload): LightCompressor-enhanced 2.3.0, HDR hint and real slider labels on Android |
| `23797a2a7f` | refactor(upload): share the orchestrator's video list and the dialogs' hint line |
| `bc5915f0f5` | fix(upload): close the cancel race on the compressed file and guard the HDR probe |
| `e805fec7a2` | docs(plans): LightCompressor 2.3.0 implementation report |
| `93b9cb72b4` | docs(plans): record the Pixel 9a device check for LightCompressor 2.3.0 |
| `28023020fa` | feat(upload): stop the encode when a post is cancelled |
| `07b8cc35eb` | build(deps): pin LightCompressor-enhanced 2.3.0 |
| `29b7d9d017` | refactor(upload): one cleanup path for a compressed file after cancel; cheaper probes |
| `5c85fd03c5` | fix(upload): no 'larger than original' toast for a cancelled post |

Docs-only commits between those (`b95d01ae7f`, `b920346a02`, `1c8290d58e`) record the device
checks and the library brief. Not done, and why: the "plays in a desktop browser" check could not
be observed (see "Tests").

## Decisions

| Decision | Taken as | Notes |
|---|---|---|
| A. `hdrMode` | **Option 1, keep the default** (recommended) | No code; the hint tells the user which outcome they get. The Pixel 9a bitrate overshoot on keep-HDR encodes (library bead 6k1) is accepted for now. |
| C. `cancel()` on timeout | **Global `cancel()` not adopted; the per-`start()` handle adopted the same day** | `MultiOrchestrator.upload` and `uploadEncrypted` `launch` one job per attachment, so two videos compress at the same time and the global `cancel()` would stop the sibling. The library request went out as LightCompressor PR #53 (`start()` returns a `CompressionHandle`, bead 7og); the brief's "Library request done" addition is implemented in `28023020fa` against the republished `2.3.0-SNAPSHOT` (AAR of 2026-10-29 13:29). The `!isActive` deletion in `onSuccess` stays for the encode that finishes as the cancel lands. |
| F. Chat dialog | **Shows the line, without "Turn on H.265"** | Fourth string `video_hdr_converted`. The chat uploaders pass no `useH265`, so `MultiOrchestrator.upload`'s default `false` applies; the hint is called with `useH265 = false, offersH265 = false`. |
| F. Where the probe runs | In the composable, not at each call site | `VideoHdrHint(media, uploader, useH265)` probes inside, the way `MediaQualitySlider(media, uploader)` already does, instead of copying the `produceState` block into three dialogs (divergence 1). |
| Step 7 cleanup | Done | `./gradlew :amethyst:dependencies` has no `mp4parser`/`isoparser` line; the `builddef.lst` pick-first and its TODO are removed, the two `META-INF/LICENSE*` pick-firsts stay. |
| Step 8 release notes | New `docs/changelog/v1.21.00.md` | v1.20.0 is tagged and released (2026-10-24), so the next version gets a new file, linked from the changelog README. |

## Changes

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | `lightcompressor-enhanced = "2.3.0"` (`2.3.0-SNAPSHOT` from the local Maven repo until the tag; pinned in `07b8cc35eb`). |
| `amethyst/build.gradle.kts` | Drops `pickFirsts.add("builddef.lst")` and the TODO above the packaging block. |
| `commons/.../service/uploads/video/VideoHdrInfo.kt` | New `data class VideoHdrInfo(isHdr, canKeepHdr)`. |
| `commons/.../service/uploads/HdrUploadOutcome.kt` | New `enum HdrUploadOutcome { KEPT, CONVERTED_BY_CODEC, CONVERTED_BY_DEVICE }` and `hdrUploadOutcome(probes, useH265)`. |
| `commons/.../service/uploads/MediaUploader.kt` | `suspend fun probeHdr(uri): VideoHdrInfo? = null` beside `probeVideo`. `UnavailableMediaUploader` and the test fakes inherit it. |
| `commons/.../service/uploads/MultiOrchestrator.kt` | `videos(): List<SelectedMedia>`; `firstVideo()` derives from it. |
| `commons/src/jvmTest/.../service/uploads/HdrUploadOutcomeTest.kt` | Six tests: nothing probed, all SDR, HDR + H.264, HDR + H.265 keepable, HDR + H.265 not keepable, SDR then HDR. |
| `commonsUI/.../ui/note/creators/uploads/MediaQualitySlider.kt` | `VideoHdrHint(media, uploader, useH265, offersH265)` and a private `UploadHintLine` shared with `VideoCompressionNeedsFfmpegHint`. |
| `commonsUI/.../ui/note/creators/uploads/ImageVideoDescription.kt` | Hint under the H.265 switch. |
| `commonsUI/.../ui/actions/NewMediaView.kt` | Hint under the H.265 switch, state from `postViewModel.useH265Codec`. |
| `commonsUI/.../chats/utils/ChatFileUploadDialog.kt` | Codec-less hint when `controls.videoWillBeCompressed`. |
| `commonsUI/.../composeResources/values/strings.xml` | `video_hdr_kept`, `video_hdr_converted_codec`, `video_hdr_converted`, `video_hdr_converted_device` (English only; Crowdin translates). |
| `amethyst/.../service/uploads/VideoCompressionHelper.kt` | `probe(context, uri): VideoInfo?` and `probeHdr(context, uri): VideoHdrInfo`; `start()`'s `CompressionHandle` is cancelled from `continuation.invokeOnCancellation`; `onSuccess` deletes the output when the continuation is no longer active, resumes the success value with an `onCancellation` that deletes the file, and shares one `deleteOutput()`. |
| `amethyst/src/test/.../ui/components/MediaCompressorTest.kt` | The (ignored) video test's `start()` stub returns a relaxed `CompressionHandle` mock so the test source compiles against the new signature. |
| `amethyst/.../service/uploads/AndroidMediaUploader.kt` | Overrides `probeVideo` and `probeHdr` on `Dispatchers.IO`; `probeHdr` returns null if the library probe throws. |
| `docs/changelog/v1.21.00.md`, `docs/changelog/README.md` | Release notes for the next version. |
| `amethyst/plans/2026-10-28-lightcompressor-2.3.0-upgrade.md`, `amethyst/plans/README.md` | Status line, decision C record, queued-table entry. |

## Divergences from the brief

1. **The probe lives inside `VideoHdrHint`.** The brief drew `VideoHdrHint(outcome)` with the
   `produceState` block at each call site. Three call sites would have carried the same block, so
   the composable takes the orchestrator and the uploader and probes itself (as the slider does).
2. **`VideoCompressor.probeHdr` is called from `VideoCompressionHelper`, not `AndroidMediaUploader`.**
   Keeps every LightCompressor import in the helper; the uploader only maps the call to the
   interface. The uploader also wraps the call in a catch and returns null on failure, because the
   library's "never throws" covers `ColorInfoReader` but not the codec enumeration behind it
   (review finding M2).
3. **`MultiOrchestrator.videos()`** was added instead of a UI-side index walk. The walk's video
   test (`SelectedMedia.isVideo()`, case-sensitive) also differed from the orchestrator's own
   (`ignoreCase = true`), so the slider label and the hint could have disagreed on a `VIDEO/mp4`.
4. **Release notes** went into a new `v1.21.00.md` rather than "under the next version" of an
   existing file: `v1.20.00.md` is the released version.

## Tests

### Unit (JVM), all after the final commit

| Suite | Tests | Skipped | Failures |
|---|---|---|---|
| `:commons:jvmTest` (incl. the new `HdrUploadOutcomeTest`, 6) | 4112 | 0 | 0 |
| `:commonsUI:jvmTest` | 414 | 0 | 0 |
| `:amethyst:testPlayDebugUnitTest` (incl. `MediaCompressorTest` 7/1 skipped, `SquarePixelsResizerTest` 4, `HlsVideoEventBuilderTest` 12, `HlsPublishOrchestratorTest` 11) | 1550 | 10 | 0 |
| `:desktopApp:test --tests '*MediaQualitySliderUiTest'` | 1 | 0 | 0 |

The skipped `MediaCompressorTest` case and the ten skipped Android tests are pre-existing skips,
not touched here. `:amethyst:assemblePlayDebug` builds; `spotlessCheck` is clean. The first
step-1 build raced my edits (the interface gained `probeHdr` after `commons` had compiled) and
failed with `'probeHdr' overrides nothing`; the rerun on the final tree passed.

### Against the released 2.3.0 (2026-10-29, after `07b8cc35eb`)

JitPack returned 200 for the 2.3.0 POM and AAR; `dependencyInsight` on `playDebugRuntimeClasspath`
selects `com.github.davotoula:LightCompressor-enhanced:2.3.0` (the local Maven repo holds no 2.3.0
release, only the snapshot, so nothing shadows JitPack). The cached AAR's `classes.jar` contains
`CancelledOutputKt`, `CompressionHandle` and `hdr/HdrInput`. `:amethyst:assemblePlayDebug` builds
and `:amethyst:testPlayDebugUnitTest` passes: 1550 tests, 0 failures, 10 skipped (the same
pre-existing skips).

### Device (brief steps 4 and 5): run 2026-10-29 on the Pixel 9a

Play-debug build (`com.vitorpamplona.amethyst.debug`, installed over adb Wi-Fi), signed in to the
Amethyst tester account, compression at Medium, uploads to `nostr.download` (plain Blossom, no
re-encoding). Clips copied into `Movies/hdrcheck` so the photo picker could offer them, and removed
again afterwards. Every dialog check was done in the video feed's composer (`NewMediaView`).

| Check | Result |
|---|---|
| Slider names the picked clip's own standard | **Pass.** A generated 3 s 3840×2160 clip labels the Medium stop "4K video → 1080p, 4 Mbps" (`media_compression_video_target`); the example string would have read "1080p video → 720p, 2 Mbps". |
| SDR clip, no HDR line | **Pass.** `jf_sdr_hevc10_1080p_3M.mp4` (10-bit SDR HEVC) shows no line, alone or beside the HLG clip. |
| HLG clip, H.265 off | **Pass.** "Your HDR video is converted to standard (SDR) video. Turn on H.265 to keep HDR." |
| HLG clip, H.265 on | **Pass.** "Your HDR video stays HDR. Some apps and browsers show HDR video washed out or too dark." |
| Mixed pick, HLG clip removed | **Pass.** The line disappears, the SDR clip stays. |
| Compressed file name | **Pass.** `files/<uuid>.mp4` during the upload (`65ea35f0-….mp4`, 926,783 bytes for test 1); no `_temp`. |
| `files/` after each upload | **Pass.** No `.mp4`, `_temp` or `transcode_*` entry after any of the three uploads. |
| Posts play in Amethyst | **Pass.** All three play in the video feed; the kept-HDR one renders with correct colours. |
| Posts play in a desktop browser | **Not verified.** The Chrome extension was not connected and `screencapture` had no screen-recording permission; the two HLG uploads were opened in Chrome on the Mac (tabs left open) but not observed. |

Uploads, probed with `ffprobe -show_streams` on the downloaded blobs:

| Test | Source | Codec switch | Upload | Result |
|---|---|---|---|---|
| 1 | `jf_sdr_hevc10_1080p_3M.mp4`, HEVC Main 10, 1920×1080, BT.709 | H.265 | kind 21, `05c98dd8…mp4` | `hevc` **Main** 8-bit, 1280×720, `yuv420p`, bt709/bt709/bt709, 1,477,681 b/s, 60 fps; 0.93 MB, -52 % |
| 2 | `orig2.mp4`, HEVC Main 10, 1920×1080 (portrait), BT.2020 HLG, 20.5 Mb/s | H.265 | kind 22, `9cb8d088…mp4` | `hevc` **Main 10**, 720×1280, `yuv420p10le`, `bt2020nc`, `color_transfer=arib-std-b67`, `bt2020`, tv range, 1,492,157 b/s |
| 3 | `orig2.mp4` | H.264 | kind 22, `d1b90b01…mp4` | `h264` High, 720×1280, `yuv420p`, bt709/bt709/bt709, tv range, 2,009,137 b/s; a frame at 2 s shows the lamp's highlights and the wall without any cyan |

Both HLG encodes sat at or under the 2 Mbps Medium target; the 1.9× keep-HDR overshoot (library
bead 6k1) did not show on this clip. Test 2 went out without a caption: the shell quoting of my
`adb shell input text` dropped the text, the upload itself was unaffected.

### Device, cancel case (brief step 4, added 2026-10-29): run after `28023020fa` on the Pixel 9a

Same build setup, reinstalled with the handle commit. Generated 3840×2160 `testsrc2` clips at
Medium (4K → 1080p, 4 Mbps). "Cancel" here means leaving the Videos feed: the dialog's own X only
clears the model (`cancelModel()`), the upload job lives in the feed screen's `viewModelScope`
(`rememberViewModel` in `NewVideoFeedButton`), so the encode is cancelled when that screen leaves
the back stack.

| Run | Clip | Action | Result |
|---|---|---|---|
| 1 | 40 s, 20 Mb/s (101 MB) | Post, X at +10 s, back at +21 s | **Edge case.** Encode ran at ~2× real time and had just finished: `transcode_…mp4` was renamed to `e6fa7d5a-….mp4` (20,021,467 bytes, 40.0 s, `ftyp moov free mdat`) the same second the cancel landed. Log: "Video compression cancelled" at +22.7 s, 1.5 s after back. The finished file stayed in `files/` (see Findings); deleted by hand. |
| 2 | 240 s, 6 Mb/s (182 MB) | Post, X at +10 s, back at +11.7 s | **Pass.** `transcode_2390505448720619506.mp4` (9.1 MB) present at +10 s; "Video compression cancelled" 0.6 s after back; `files/` empty 4 s later. |
| 3 | 240 s clip | Post, no cancel | **Pass.** "Video compression cancelled" 60.1 s after the library's bitrate log (the `withTimeoutOrNull(60_000)` path); `files/` empty at +64 s. Backed out 6 s later to stop the fallback upload of the 182 MB original; `nak req` on the tester key shows nothing published. |

The clips were deleted from `Movies/hdrcheck` and MediaStore afterwards; `files/` holds no `.mp4`,
`_temp` or `transcode_*` entry.

### Device, cancel case rerun against the fixed library (2026-10-29, Pixel 9a)

The library session fixed the leftover (LightCompressor bead 075, commits `45ce24a`..`95c2d3b` on
its `fix/cancel-finalized-output` branch) and republished `2.3.0-SNAPSHOT` at 16:47; its
`classes.jar` and the rebuilt APK both contain `CancelledOutputKt`. Amethyst was reinstalled
(16:51) with no app change: the last library commit makes `Compressor` `internal`, and Amethyst
only ever imported `VideoCompressor`. Same 40 s, 20 Mb/s 4K `testsrc2` clip at Medium, same
"X then back out of the Videos feed" cancel, `files/` polled every 0.8 s from the Post tap.

| Run | Back at | What `files/` showed | Result |
|---|---|---|---|
| 1 | +21.5 s | +22.4 s: `transcode_…mp4` **and** `81e66997-….mp4`, both 19,656,382 bytes (the fast-start copy in progress); +23.2 s: empty | **Pass, the exact race.** "Video compression cancelled" (`onCancelled`) at +22.9 s. The library deleted the finished output; Amethyst's success path never ran. |
| 2 | +22.5 s | +22.5 s: both files; +23.4 s: empty | **Pass.** No `onCancelled` line: the encode completed first, `onSuccess` found the continuation cancelled and Amethyst's own guard deleted the file. |
| 3 | +22.7 s | +22.7 s: only `0c66a797-….mp4` (finalize done); +23.7 s: empty | **Pass.** Same success-path cleanup as run 2. |
| 4 | +12.5 s | +12.5 s: `transcode_…mp4` at 10.9 MB; +13.4 s: empty | **Pass, mid-encode regression check.** `onCancelled` 1.1 s after back. |

| 5 | +22.8 s | +22.1 s: finalized output only; +23.8 s: empty | **Pass**, on the refactored build (`29b7d9d017`, installed 19:07): the success path with the early `isActive` guard gone. |

Run 5 landed after the encode had completed, so the upload pipeline's own cleanup removed the
file; the ms-wide window where `onSuccess` arrives after the continuation is already cancelled
(now handled only by the `resume` `onCancellation` handler) cannot be hit by hand, and rests on the
documented contract that a resume on a cancelled continuation runs that handler.

Runs 1 to 3 bracket the finalize window from both sides, so both owners of the file are covered:
the library when the cancel lands during the encode or the copy, Amethyst when it lands after
`onSuccess` was already on its way. `nak req` on the tester key over nostr.download, damus, nos.lol
and primal shows nothing published during the runs. The clip was removed from `Movies/hdrcheck`
and MediaStore; `files/` and `cache/` hold no `.mp4`, `_temp` or `transcode_*` entry.

## Findings

- **Uploads compress concurrently.** `MultiOrchestrator` launches every attachment at once, which
  is what ruled out the global `VideoCompressor.cancel()`; the per-`start()` `CompressionHandle`
  (library PR #53) is what the branch now cancels.
- **Library gap: a cancel that lands after the last frame leaves the finished file.** Device run 1
  above. `Compressor.compressVideo` runs the transcode inside `withContext(Dispatchers.Default)`
  and the `Transcoder` polls `isCancelled` once per decoded frame. After the last frame,
  `finalizeOutput` writes `files/<uuid>.mp4` and the attempt returns success, but a `withContext`
  whose job was cancelled meanwhile throws `CancellationException` on the way out: the `Result` is
  dropped, `CompressionBatches` reports `onCancelled`, and `attempt`'s `finally` deletes only the
  `transcode_*` muxer file, not the destination. `onCancelled(index)` carries no path, so Amethyst
  cannot clean up from the callback. The window is the fast-start copy (hundreds of ms for a 20 MB
  output). Decided 2026-10-29: fixed in the library, not here (an Amethyst-side delete of
  `File(filesDir, "$uuid.mp4")` in `onCancelled` would duplicate the library's
  `AppSpecificStorageConfiguration` path rule). Brief for the library session:
  `LightCompressor/docs/briefs/2026-10-29-075-cancel-after-last-frame-leftover.md` (delete `desFile`
  when the video coroutine ends cancelled, keep `onCancelled`, JVM test on a `File` helper).
  **Fixed in the library the same day** (bead LightCompressor-075, `VideoCompressor` wraps the
  encode in `File.deletedIfCancelled`, snapshot republished 16:47) and verified here: see "Device,
  cancel case rerun". The library session also found and fixed the second symptom (a mid-encode
  cancel of a non-streamable encode left its partial destination) and filed LightCompressor-lrm for
  the failure path.
- **The dialog's X does not cancel a running upload.** `NewMediaView`'s cancel button calls
  `cancelModel()`, which only nulls the orchestrator; the upload coroutine is in the
  `viewModelScope` of a model scoped to the feed screen, so compression and upload continue until
  that screen leaves the back stack. Pre-existing, outside the brief; worth its own bead.
- **`SelectedMedia` has identity equality.** `produceState(…, videos)` only re-probes when the
  orchestrator's list really changes because the orchestrator keeps the same instances; `videos()`
  says so in its KDoc.
- **Desktop already knows whether a source is HDR.** `FfprobeOutputParser` reads `color_transfer`
  into `ProbeResult.isHdr`, which `FfmpegVideoCompressor` uses for the post-upload
  `UploadNotice.HdrSource`. A `DesktopMediaUploader.probeHdr` override returning
  `VideoHdrInfo(isHdr, canKeepHdr = false)` would show the same pre-upload hint there and make
  the post-hoc notice redundant. Out of scope per the brief; a small follow-up.
- **Pre-existing:** the first video is opened three times in the dialog on Android (`MediaExtractor`
  for the pixel aspect ratio, `MediaMetadataRetriever` for the size, another `MediaExtractor` for the
  colour info) and again at compression time. All on `Dispatchers.IO`, once per picked set; not
  worth merging.
- **`desktopApp` is a plain JVM module**: its test task is `test`, not `jvmTest`.
- **An `am start … --grant-read-uri-permission` share from adb does not give the app a readable
  content URI**: the composer opened with the clip, but `contentResolver.getType` came back null,
  so no video controls appeared and the preview said "Can't play this video
  (ERROR_CODE_FAILED_RUNTIME_CHECK)". The photo picker path is the one to drive; the same shell
  cannot pass several URIs either (`--eual` is unknown to this `am`). Not an app bug.
- **A portrait 1080×1920 clip posts as kind 22** (short video) from the video feed's composer; the
  landscape one posted as kind 21. Pre-existing routing, noted for the event table above.

## Review findings not acted on

Simplify pass (four reviewers; fixed: the `onSuccess` restructure and shared `deleteOutput`,
`MultiOrchestrator.videos()`, the shared `UploadHintLine`, the decision-C record):

- *Tri-state `useH265`/`offersH265` or a three-value `VideoHdrInfo` enum.* Would move UI wording
  ("turn on H.265") into a `commons` enum, or stop mirroring the library's `HdrProbe` one to one.
  Two booleans with one meaningless combination is the cheaper trade.
- *Self-gating `VideoHdrHint(controls, …)` with an `offersCodecSwitch` on `VideoUploadControls`.*
  The two post dialogs must place the line under the switch inside that block anyway, so
  self-gating saves only the chat dialog's one-line `if`, at the cost of `VideoUploadControls`
  keeping `media` and describing the chat layout.
- *Desktop `probeHdr` from ffprobe.* Out of the brief's scope ("Probing HDR on desktop"); listed
  above as a follow-up.
- *Parallel probes, a `derivedStateOf` for the video list, reusing the dialog's probe at
  compression time.* The efficiency reviewer judged each negligible for one-shot IO in an upload
  dialog; agreed.

Second `/simplify` pass (2026-10-29, after the 2.3.0 pin; four agents: reuse, simplification,
efficiency, altitude). Applied in `29b7d9d017`:

- *One delete path after a cancel.* The early `!continuation.isActive` guard in `onSuccess` and
  the `resume(…) { deleteOutput }` handler both deleted the file. A resume on a cancelled
  continuation is a no-op that runs the `onCancellation` handler (the documented contract, and
  what the earlier M1 fix already relied on), so the handler is now the only delete and
  `isActive` only mutes the "Video compressed" toast. The `isActive` guards in `onFailure` and
  `onCancelled` went for the same reason; the comment at `invokeOnCancellation` says why.
- *Reuse.* `deleteOutput` calls `File.deleteOrWarn` (commons `util/FileDeletion.kt`), which does
  not warn for a file already gone; `Uri.fromFile(File(path))` became `File(path).toUri()`.
- *Probe contract.* `VideoCompressionHelper.probeHdr` catches and returns null like `probe`, so
  the Android port is a one-liner like `probeVideo` and any other caller gets the same contract.
- *Efficiency.* The HDR hint probes a multi-video post concurrently (`async`/`awaitAll`);
  `firstVideo()` is back to `firstOrNull { isVideo() }` instead of filtering and mapping two lists
  per recomposition.
- *A comment* in `ChatFileUploadDialog` that restated `useH265 = false, offersH265 = false`.

Not applied:

- *One probe per video* (altitude, efficiency): fold the HDR answer into `VideoInfo`, keep a
  single `MediaUploader.probeVideo` and one `produceState`, so the first video is not opened three
  times (`MediaExtractor` for the aspect ratio, `MediaMetadataRetriever` for the info, the
  library's extractor for the colour tags). A real saving, but it reshapes the brief's API across
  commons, commonsUI, amethyst, desktop and the tests, and removing the duplicate open fully needs
  a library overload taking a `MediaFormat`. Worth its own bead.
- *`useH265: Boolean?` instead of two flags on `VideoHdrHint`*, read from a `ChatFileUploadState`
  property that `MarmotFileUploader` would also pass (altitude). Right idea, but it adds state to
  the chat dialog for a switch it does not have; a bead if chat ever gets the codec switch.
- *Remembering per-video HDR probes across list edits* (efficiency): multi-video posts are rare
  and the probe is cheap.

Second Kotlin review (2026-10-29, on the whole branch after the simplify pass; PASS: 0 critical,
0 high, 1 medium, 3 low). It confirmed the cancellation handling in `compressVideo` (handle
registered after `start()`, unguarded resumes safe, the `onCancellation` handler as the single
delete), that nothing swallows `CancellationException`, the IO dispatchers, and the module
placement. Fixed in `5c85fd03c5`: the "larger than original" warning toast could still show for
a cancelled post (L3). Not acted on:

- *M, a stale HDR line while a re-probe is in flight.* The same point as M4 below; resetting
  the value first would flicker the line when a video is added, and probes take tens of
  milliseconds. The reviewer offered "skip the reset" as the alternative.
- *L, `probeHdr` catches `Exception`, not `Throwable`.* An `Error` from the codec stack would
  propagate; catching `Throwable` would also hide genuine JVM errors, and the library only
  throws exceptions.
- *L, two probes per video per dialog.* Keyed and parallel; revisit with the one-probe bead
  above if it ever matters.

First Kotlin review (WARNING: 0 critical, 0 high, 4 medium, 3 low; fixed: M1 `onCancellation` on
the success resume, M2 catch around the library probe, L1 KDoc on `videos()`):

- *M3, `2.3.0-SNAPSHOT` in the catalog.* Done later the same day in `07b8cc35eb`, once JitPack
  served the 2.3.0 tag.
- *M4, a stale hint for the moment between a pick change and the new probes.* `produceState`
  keeps the old value until the restarted producer finishes; tens of milliseconds, and resetting
  the value first would flicker the line when a video is added.
- *L2, `videos()` allocates two lists and `firstVideo()` builds the whole list.* Lists of one to
  a handful of items in a dialog recomposition.
- *L3, a hypothetical second `onSuccess`.* Ruled out by the library's one-terminal-callback
  guarantee, now stated in the comment at the check.

## Release notes, tracker updates and status

- Release notes: `docs/changelog/v1.21.00.md` (new) and its link in `docs/changelog/README.md`.
- Plan status: the brief's status line points here; `amethyst/plans/README.md` keeps it under
  Queued until it ships.
- Next steps, in order:
  1. Optional: open the two HLG uploads in a desktop browser (see the device table).
  2. Pushed to the fork and opened as draft PR
     [vitorpamplona/amethyst#4684](https://github.com/vitorpamplona/amethyst/pull/4684) on
     2026-10-29 (upstream `main` was 45 commits ahead; `merge-tree` reports a clean merge).
     Mark it ready and merge once its CI is green.
     `PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push` was run by hand after `07b8cc35eb`:
     exit 0, "All test passed", the shared-code metadata compile and `:amethyst:lintPlayDebug`
     clean (only pre-existing warnings, e.g. the iOS `LocalClock` redundant conversion). Rerun
     after the second simplify pass (`29b7d9d017`) with the same result.

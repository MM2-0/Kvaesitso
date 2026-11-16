# Report: dsr, delegate the video/AAC privacy strip to `Mp4Sanitizer`

Brief: `amethyst/plans/2026-11-09-dsr-sanitizer-delegation.md`. Bead dkamy-dsr.
Branch `feat/mp4-sanitizer` from `e3f14a9c8f` (= `upstream/main`), worktree
`/Users/david/StudioProjects/amethyst-wt1`. Not pushed. Library: `LightCompressor-enhanced:2.4.0-SNAPSHOT`
from mavenLocal, last built and tested against fork commit `1e4c3bd`. The device runs used `a697ec2`;
see Findings for what changed since.

## Outcome

Android's metadata strip for video and MP4 audio no longer re-muxes through an in-app `MediaMuxer`
loop. `MetadataStripper` hands the file to the library's `Mp4Sanitizer`, which copies only the audio
and video tracks into a fresh MPEG-4 file and fast-starts it. That delivers rows 2 and 4 of the
brief's table: UNCOMPRESSED with strip on, and a compression fallback with strip on, now upload
`ftyp, moov, mdat`. A successful compression that is not fast-start is logged (row 3, "never
silent"). Since the library can now be interrupted, a cancelled upload stops the remux instead of
running it to the end. On the Pixel 9a, an UNCOMPRESSED upload with strip on came out fast-start with
the location, the `mett` track, the C2PA `uuid` box and the capture tags gone (row 2). Row 4 was not
reachable from the main media dialog, which turned strip off for any compressed video; 8i5 (below)
fixed that, so a fallback from that dialog is now stripped too.

| Commit | |
|---|---|
| `44869a7da3` | fix(uploads): strip video and m4a metadata through Mp4Sanitizer so uploads are fast-start |
| `f466c08879` | refactor(uploads): one tag and delete helper in MetadataStripper, temp-file prefix from the media kind (`/simplify`) |
| `f4c566608e` | fix(uploads): the compressed-output fast-start check cannot stall the upload (Kotlin review) |
| `849a7cc650` | fix(uploads): a cancelled upload stops the video/m4a remux (library 2035bde's interrupt support) |
| `d6da13f525` | docs(uploads): list Mp4Sanitizer's new refusals in the MetadataStripper KDoc (library a697ec2) |

## Decisions

1. **Keep `StrippingResult`'s boolean contract.** Taken as recommended. `fastStart = false` is logged
   (`Sanitized <kind> is not fast-start: <file>`), the file is still returned as stripped, and the
   dialog behind `onStrippingFailed` is unchanged.
2. **Test 6 (`VideoCompressionHelper.onSuccess`).** Skipped, per the recommendation's "if it would need
   a new harness" branch. `MediaCompressorTest` does mock `VideoCompressor.start`, but its only video
   test is `@Ignore("Waits forever for some reason")` and never drives the listener. Test 6 would need
   a captured `CompressionListener`, mocked `Uri.fromFile`, `Handler` and the content-resolver size
   query, and the line under test is a log statement no test can observe (no Quartz `Log` capture).
3. **Temp-file names.** Kept: `stripped_video_*.mp4`, `stripped_audio_*.m4a` (now built as
   `"stripped_${kind}_"`).
4. **Snapshot timing.** No stub was written. The code was written against the contract while the
   snapshot did not exist and built once the integrator reported it published (fork `1d5e5c0`), then
   rebuilt against the republished `e960f4d`, `2035bde`, `a697ec2` and `1e4c3bd`.

## Changes

- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt`
  - `stripVideoMetadata` / `stripAudioMetadata` call one private `sanitizeMp4(uri, context, kind,
    suffix)`. It creates the cache temp file, runs `Mp4Sanitizer.sanitize` inside
    `runInterruptible(Dispatchers.IO)`, logs a warning when `fastStart` is false, and returns
    `StrippingResult(tempFile.toUri(), true)`. On failure it deletes the temp file, rethrows a
    `CancellationException`, calls `ensureActive()` (see Divergences), logs `Log.w(TAG, "Failed to
    strip <kind> metadata", e)` and returns `StrippingResult(uri, false)`.
  - `strip`, `stripVideoMetadata` and `stripAudioMetadata` are now `suspend`.
  - Deleted: `remuxTracks`, `extractorToCodecFlags`, `DEFAULT_REMUX_BUFFER_SIZE`, the AAC primary-track
    pre-check, and the `MediaCodec`/`MediaExtractor`/`MediaFormat`/`MediaMetadataRetriever`/
    `MediaMuxer`/`ByteBuffer` imports. Dispatch in `strip` is unchanged (`audio/mpeg` → MP3, other
    `audio/*` → sanitizer).
  - KDoc on the object: what each media kind gets, that the MP4 path is a container rewrite by
    `Mp4Sanitizer` keeping audio and video tracks only (timed metadata such as a Pixel's
    `application/meta` and an iPhone's `mebx` dropped), the residuals (`MediaMuxer` creation time, a
    `com.android.version` meta key, bitstream SEI), when the strip fails instead of passing the file
    through (Opus, MP3, Dolby Vision, a kept track with no samples, a mid-GOP stream-copy trim), and
    that UNCOMPRESSED without strip never gets here.
  - Whole file: one `private const val TAG`, and temp-file cleanup through `deleteOrWarn` (from
    `/simplify`; previously the `"MetadataStripper"` literal and hand-written delete-and-warn blocks).
- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt`:
  in `onSuccess`, after the size check and before resuming, `Mp4Sanitizer.isFastStart(output)` inside
  `runCatching { }.getOrDefault(true)`; when false, `Log.w(LOG_TAG) { "Compressed output is not
  fast-start: $path" }`. The upload is not changed either way. The output `File` is built once.
- `gradle/libs.versions.toml`: `lightcompressor-enhanced = "2.4.0-SNAPSHOT"` (left for the integrator
  to pin).
- `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripperTest.kt`: six new
  tests (below), a `TemporaryFolder` for `cacheDir`, `mockkStatic(Uri::class)` so `File.toUri()` works
  on the JVM, and `mockkStatic(Mp4Sanitizer::class)`.
- `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifMetadataStripperPoisonedFixtureInstrumentedTest.kt`:
  its `strip` call is wrapped in `runBlocking`, since `strip` is now suspend.
- `amethyst/plans/2026-11-09-dsr-sanitizer-delegation.md`: the brief, committed with the
  implementation (it was untracked in the main checkout).
- `.claude/skills/`: no change; no skill mentions `MetadataStripper` or `MediaMuxer`.

## Divergences

- **`mockkStatic` instead of the brief's `mockkObject(Mp4Sanitizer)`.** The library's API is
  `@JvmStatic`, so Kotlin callers go through the static bridge, which `mockkObject` does not intercept:
  the stub recorded the library's internal 4-argument `sanitize` with a real `MediaExtractor`, and three
  tests failed. `mockkStatic(Mp4Sanitizer::class)` intercepts the bridge.
- **`strip` is now `suspend`, and the remux is interruptible.** Not in the brief. The integrator asked
  for it after library fork `2035bde` made `sanitize` stop on a thread interrupt
  (`InterruptedIOException`, output deleted), on condition that it stayed small. It did: both
  production callers (`AndroidMediaUploader.stripAfterCompression` and its `stripMetadata` override)
  were already suspend on `Dispatchers.IO`, so `AndroidMediaUploader` is not edited. The one blocking
  caller is an instrumented test, which now uses `runBlocking`.
- **`ensureActive()` in the failure path.** `runInterruptible` turns only `InterruptedException` into a
  cancellation; the library's `InterruptedIOException` comes through as an ordinary `IOException`.
  Without the check, a cancelled upload would return "not stripped" and the caller would show the
  strip-failed dialog. The temp file is deleted before the check, so cleanup runs on every path.
- **The fast-start check in `onSuccess` is wrapped in `runCatching`.** The brief has a bare `if`.
  `isFastStart` catches only `IOException`, so anything else thrown inside the listener would skip
  `continuation.resume`, and the upload would sit until the 60-second timeout. A throw counts as
  fast-start, which keeps the brief's "do not fail or alter the upload on it".
- **The temp-file prefix is derived from the kind** (`"stripped_${kind}_"`). The names are unchanged.

## Tests

Unit, `./gradlew :amethyst:testPlayDebugUnitTest`: **1,541 tests, 0 failures, 0 errors, 10 skipped**
(the skips predate the change: 1,540 tests and 10 skips before the cancellation test was added).
`MetadataStripperTest`: **9/9**, 3 existing AVIF tests and 6 new ones:

| Brief test | Test | Result |
|---|---|---|
| 1 | `video is sanitized into a cache file and marked stripped`: `sanitize` called once, its output under `cacheDir` named `stripped_video_*`, the result uri is that file, `stripped` | pass |
| 2 | `MP4 audio and AAC are sanitized`: `audio/mp4` and `audio/aac` both go to `sanitize` (2 calls, `stripped_audio_*`) | pass |
| 2 | `MP3 takes the ID3 path, not the sanitizer`: `audio/mpeg` makes no `sanitize` call | pass |
| 3 | `a track MP4 cannot hold returns the original unstripped and leaves no temp file`: `IOException("track 1 (audio/opus) …")` → `StrippingResult(uri, false)`, the cache folder is empty | pass |
| 4 | `a remux that is not fast-start is still stripped`: `fastStart = false` → `stripped`, the uri is the temp file | pass |
| 5 | the three AVIF tests | pass |
| (new) | `a cancelled upload interrupts the remux and does not report a failed strip`: the job is cancelled and `sanitize` throws `InterruptedIOException` → the job ends cancelled, `strip` returns nothing, the cache folder is empty | pass; fails without the `ensureActive()` line (checked by removing it) |
| 6 | `VideoCompressionHelper.onSuccess` | not written (decision 2) |

`MediaCompressorTest`: 7 tests, 0 failures, 1 skipped (its existing `@Ignore`).

The unit counts above are from the run against fork `a697ec2` (AAR published 23:21, tests run
23:22). Later counts, against `1e4c3bd`, are in the last section.

Static: `spotlessApply`, `:amethyst:compilePlayDebugKotlin`, `:amethyst:compilePlayDebugAndroidTestKotlin`
and `:amethyst:lintPlayDebug` all pass at `d6da13f525` against `a697ec2`; the pre-commit hook passed on each commit.
`agent_config_check.py` was not needed (no skill file changed).

### Device

Pixel 9a, Android 17, 2026-11-12. The user installed the worktree build at `d6da13f525` as
`installPlayBenchmark` (`com.vitorpamplona.amethyst.benchmark`), with 2.4.0-SNAPSHOT from fork
`a697ec2`; the dex carries the library's "before its edit list starts" string. Uploads went to a local
haven Blossom server on the LAN from the Amethyst tester account; the integrator session pulled the
blobs. I re-ran the atom walk, `ffprobe` tags, `shasum -a 256` and `cmp -l` on the pulled files and got
the same results.

Source: one Pixel camera clip, `PXL_20261112_131239816.mp4`: 54,592,421 bytes, 22.663 s, sha256
`a7ee07025104d791…`. Top level `ftyp 28, uuid 4980 (C2PA manifest), free 55028, mdat 54496595,
moov 35782`, so `moov` is last. Streams: HEVC 1920x1080 rotation -90, AAC, and a `mett` data track.
Tags: `location +46.5202+14.1473/`, `com.android.capture.fps`, `com.android.model`,
`com.android.manufacturer`, `creation_time 13:13:03Z`.

Three of the brief's four rows are reachable from the main dialog, because
`ImageVideoDescription.kt:446` sets `effectiveStripMetadata = !isVideoWithCompression && stripMetadata`.
MEDIUM with strip on and MEDIUM with strip off are therefore the same upload.

| # | Setting | Blob sha256 | Size | Top-level atoms | Streams | Result |
|---|---|---|---|---|---|---|
| 1 | UNCOMPRESSED, strip on | `54527498ceb1fd6b…` | 54,393,617 | `ftyp 24, moov 8883, free 3192, mdat 54381510` | HEVC 1920x1080 rotation -90, AAC | Fast-start. `mett` track, `udta` (location), `uuid` and the capture/model/manufacturer tags gone. `meta` holds only `com.android.version=17`. `creation_time` is the strip time (`MediaMuxer`'s). Duration 22.662811 s vs 22.662833 s |
| 2 | UNCOMPRESSED, strip off | `fbd0a6d125c31d5f…` | 54,592,421 | `ftyp 28, uuid 4980, free 55028, mdat 54496595, moov 35782` | HEVC, AAC, `mett` | The camera file as read, `moov` still last. It differs from the file on disk in exactly 24 bytes, the `©xyz` location payload, which is zeroed (see below). The other tags are still there, as expected with strip off |
| 3 | MEDIUM (strip forced off) | `7f7ae93e8c90affe…` | 6,155,271 | `ftyp 24, moov 8806, free 3192, mdat 6143241` | AAC, H.264 720x1280 (rotation baked in) | Fast-start, two tracks, `meta` holds only `com.android.version=17`. Duration 22.662811 s |

Row 2 does not match the brief's expectation of "the source's sha256": the blob's hash is `fbd0a6d1…`,
the camera file's is `a7ee0702…`. Android, not Amethyst, made the difference. The app lacks
`ACCESS_MEDIA_LOCATION`, so MediaStore redacts the location in the stream it hands out from the
picker, zeroing those 24 bytes before Amethyst reads it. Amethyst uploaded the bytes it was given:
same size and the same atom layout.

Logcat (the benchmark build logs at INFO, so warnings and errors show): zero `Sanitized … not
fast-start`, zero `Compressed output is not fast-start`, zero `MetadataStripper` failures.

#### Run 2 at `1e4c3bd`

Pixel 9a, 2026-11-12 19:53–19:54: the benchmark build at `45488f628a` (installed 19:43:52) with
2.4.0-SNAPSHOT from fork `1e4c3bd`; the dex carries that commit's "Sanitize interrupted" string. Same
server, tester account and source clips as run 1, plus the 8i5 fallback clip. The integrator session
ran it with the user; I re-ran the atom walk, `ffprobe` tags and `shasum` on the pulled blobs, with the
same results.

| # | Setting | Blob sha256 | Size | Top-level atoms | Tags beyond brand and `creation_time` |
|---|---|---|---|---|---|
| 1 | Camera clip, UNCOMPRESSED, strip on | `ef61d2b95f574360…` | 54,393,617 | `ftyp 24, moov 8883, free 3192, mdat 54381510` | `com.android.version=17` only; no `udta` |
| 2 | Camera clip, UNCOMPRESSED, strip off | `fbd0a6d125c31d5f…` | 54,592,421 | `ftyp 28, uuid 4980, free 55028, mdat 54496595, moov 35782` | capture fps, model, manufacturer; location zeroed by MediaStore |
| 3 | Camera clip, MEDIUM | `cd51a58185516b2d…` | 6,155,271 | `ftyp 24, moov 8806, free 3192, mdat 6143241` | `com.android.version=17` only |
| 4 | `lowbitrate_240p_5s`, HIGH (the 8i5 fallback) | `da0a663d41ad8ce3…` | 227,529 | `ftyp 24, moov 4401, free 3192, mdat 219904` | `com.android.version=17` only; no `udta` |

- Row 2 is byte-identical to run 1 (same hash), so the unstripped path is deterministic, the OS
  location redaction included.
- Rows 1, 3 and 4 have run 1's sizes and atom tables. Their hashes differ only because `MediaMuxer`
  writes the time of the strip or encode as `creation_time`.
- Rows 1 and 4 carry AAC and were stripped without a refusal, so `1e4c3bd` brought no regression on
  these clips. They do not test its priming rule, though: in both, the audio edit list is an empty
  edit followed by media time 0, so neither has audio samples before its edit list
  (`ffprobe -v trace`). Run 2b covers that case.

#### Run 2b: AAC priming before the edit list

Same build, library and setup as run 2, uploaded at UNCOMPRESSED with strip on.

- **Source** `priming_aac_5s.mp4`: a synthetic ffmpeg clip (`testsrc2` 320x240 H.264 plus a 440 Hz
  AAC tone), 5 s, 300,256 bytes, sha256 `ff294406e2bd7d41…`. Top level `ftyp 32, free 8, mdat 293234,
  moov 6982` (`moov` last). The audio edit list starts at media time 1024 and the first AAC packet
  is at -0.021333 s, the shape `a697ec2` refused. Tags: `location`, `location-eng`,
  `com.android.model`.
- **Result:** no strip-failed dialog. Blob `9c668e0453db02c0…`: 301,237 bytes, top level `ftyp 24,
  moov 4779, free 3192, mdat 293234` (fast-start). No `udta`; the only tag is
  `com.android.version=17`.
  - Audio: 236 of 236 packets, edit list still at media time 1024, the first packet still at
    -0.021333 s, duration 5.013 s.
  - Video: 150 of 150 frames, starting at pts 0. `MediaMuxer` writes its own edit list for the
    B-frame offset (media time 9018 at 90 kHz) and reports 4.967 s instead of 5.000 s. That is
    `MediaMuxer` leaving out the last frame's duration on any passthrough remux, not something the
    sanitizer does, and the old in-app remux did the same.
- So the `1e4c3bd` priming rule is verified on the device from Amethyst, in addition to the library's
  own device test.

Commands: `ffprobe -v trace <f> 2>&1 | grep -o "type:'[a-z0-9]\{4\}' parent:'root' sz: [0-9]* [0-9]*"`,
`ffprobe -v error -show_entries format_tags:stream_tags -of flat <f>`, `cmp -l source.mp4 <blob>`,
`shasum -a 256`. The raw notes and the blobs are in the integrator session's scratchpad
(`/private/tmp/claude-501/-Users-david-StudioProjects-amethyst/c4bafe9b-04c4-4b74-88e3-9e5e3b41e14d/scratchpad/matrix/`);
it is temporary.

## Findings

- **The main media dialog never stripped a compressed video (fixed by 8i5, below).**
  `ImageVideoDescription.kt:446` turned strip off whenever compression was selected, so the brief's row
  4 (the encode falls back, strip on) was
  reachable only from entry points that pass the account's strip setting through unchanged, such as
  `UploadToDefaultServer` (`stripMetadata && account.settings.stripLocationOnUpload`). I did not check
  which of those take video at a compression level. From the main dialog, a fallback uploaded the
  original with its metadata, even though the user had stripping on. Hiding the toggle was meant to
  be safe because a successful encode drops the metadata anyway, but a fallback breaks that
  assumption. Filed as dkamy-8i5 and fixed on this branch; see the 8i5 section.
- **Android redacts the location before Amethyst sees it.** Without `ACCESS_MEDIA_LOCATION`, MediaStore
  zeroes the `©xyz` payload in the stream it hands to the picker, so an UNCOMPRESSED strip-off upload
  is not byte-identical to the file on the phone (24 bytes differ). Model, manufacturer and capture
  tags are not redacted. Upload hashes cannot be compared with the on-disk file for that reason.
- **Android 17 Local Network Protection blocked the device run.** The app's connections to the LAN
  Blossom server were dropped until `pm grant … ACCESS_LOCAL_NETWORK` (filed as dkamy-0a4, not this
  bead). An earlier attempt to the user's Cloudflare-fronted Blossom server took more than 120 s for
  a 50 MB PUT, which is a transport limit, not this change.
- **Trimmed clips now get the strip-failed dialog (library `a697ec2`, narrowed in `1e4c3bd`).** A stream-copy trim, which is
  what the gallery editor produces, has an edit list that starts mid-GOP. `MediaExtractor` then reports
  the lead-in samples with negative timestamps. The old `remuxTracks` copied them, and `MediaMuxer`
  re-based the video to the lead-in keyframe. Up to one GOP of the cut-out content came back in the
  upload and the audio drifted by about 1.5 s; the integrator verified this on the Pixel 9a. That is a
  privacy gap in the strip before this change. `sanitize` now refuses those samples with
  `IOException("Track N has a sample at -X us, before its edit list starts …")`. In Amethyst that is
  the existing failure path: `StrippingResult(uri, false)`, then the "could not strip, upload anyway?"
  dialog. So a trimmed video at UNCOMPRESSED with strip on now asks the user, where it used to upload
  a wrong file silently. No code change here, only the KDoc. A trimmed clip at a compression level
  reaches the sanitizer only if the encode falls back. The library session checked the compressor
  itself (`Transcoder`): it seeks to 0 first and keeps the audio of a trimmed source. Worth a
  release-note line, and possibly a bead for a clearer dialog message, or for compressing trimmed
  clips instead of refusing them.
  - **`a697ec2` refused too much; `1e4c3bd` fixes it.** An AAC encoder puts priming samples before the
    edit list (the first frame at -21,333 µs at 48 kHz), so `a697ec2` mistook every ffmpeg-encoded
    file with AAC audio for a trim and refused it. Since `1e4c3bd` only video samples before the edit
    list are refused; audio priming is copied. A trimmed clip is still refused, on its video track,
    while ordinary ffmpeg and other-encoder files pass. The dsr and 8i5 device runs used `a697ec2` and
    were not affected. The dsr clip is a Pixel camera file. The 8i5 clip is an ffmpeg file with AAC,
    but its audio edit list is an empty edit followed by media time 0, so no audio sample comes
    before it (checked with `ffprobe -v trace`). That is why `a697ec2` stripped it.
- **Dolby Vision is refused (library `a697ec2`).** On Android 17, `MediaExtractor` lists a
  `video/dolby-vision` track plus a `video/hevc` base layer for Dolby Vision profiles 8.1 and 5, and
  `MediaMuxer.addTrack` accepts both, so the old remux probably wrote the video twice (unverified).
  `video/dolby-vision` is not on the sanitizer's MP4 allowlist, so it now throws a named `IOException`
  and the user gets the same dialog. A library bead decides between keeping the base layer and keeping
  Dolby Vision.
- **A kept track with no samples is refused (library `a697ec2`).** `MediaMuxer` silently leaves such a
  track out, so the library now treats it as a failed strip rather than a smaller file. Same dialog.
- **Library speed.** The library side measured a 60-second clip sanitized to fast-start in 4.2 s on the
  Pixel 9a.
- **Library logs the same fast-start warning.** `Mp4Sanitizer.sanitize` logs `<path> is not fast-start;
  keeping the plain remux` through `android.util.Log`, so a non-fast-start strip logs twice: the
  library's line and Amethyst's Quartz `Log` line naming the media kind. Kept because the brief asks
  for the Amethyst line and the device step counts it.
- **The compressor discards a value it already has.** `Transcoder.finalizeOutput` calls
  `finishFastStart` and ignores whether the result is fast-start, so `VideoCompressionHelper` re-reads
  the atom headers to find out. A library-side `Log.w` there (or a `fastStart` field on the success
  callback) would let every caller see it and let Amethyst drop its check. A few small reads per
  upload, so not urgent. Candidate for a library bead.
- **Fast-start costs one extra full copy.** `sanitize` writes `<output>.mux.tmp` and then rewrites it
  with `moov` first, so a strip reads and writes the clip once more than the old remux, and
  `cacheDir` briefly holds up to twice its size. Inherent to the fast-start pass, not a defect.
- **Other MP4 writers.** `VoiceAnonymizer` still writes MP4 through a bare `MediaMuxer`, so the rule
  "every MP4 Amethyst writes is fast-start" still holds writer by writer (bead dkamy-8oe). The library's
  fast-start finisher is `internal`, so routing that writer through it needs a public entry point.
- **Expected failures log at warning with a stack trace.** A source with an Opus or MP3 track in an
  MP4 container now logs `Log.w(TAG, msg, e)`. The old audio path returned quietly at debug level.
  This follows the brief's AGENTS.md style.
- **Brief line numbers drifted.** The brief cites `extractorToCodecFlags` at 68–136 and `remuxTracks`
  at 138–209; on `e3f14a9c8f` they were at 127–136 and 138–209.

## Review findings not acted on

From `/simplify`:
- *Drop the temp-file delete on failure; the library already deletes its output.* Kept: the brief asks
  for it, and it costs one call. It also covers a library that someday leaves a file behind.
- *Drop Amethyst's "not fast-start" warning (the library logs one).* Kept; see Findings.
- *Shorten the object KDoc.* Kept: the integrator asked for the "audio and video tracks only" wording
  and the residuals list.
- *Fold the not-fast-start test into the video test.* Kept separate as the brief's test 4.
- *Move the compressor's fast-start warning into the library.* Outside this repo; see Findings.

From the Kotlin review (no CRITICAL or HIGH findings):
- *Test 4's `exists()` assertion is weak.* Kept: it still catches a regression that deletes the file
  on `fastStart = false`. The log line cannot be asserted.
- *Log the expected `IOException` without the stack trace.* Not changed: the brief prescribes
  `Log.w(TAG, msg, e)`.
- *The `CancellationException` rethrow is unreachable.* It is reachable now: `sanitizeMp4` suspends in
  `runInterruptible`.
- *The MP3 routing test asserts only the non-call; the audio test reuses one slot.* As the brief
  specifies, and correct as written.

## Release notes, tracker updates and status

- Release note (draft):
  - Uncompressed videos and .m4a audio uploaded with "remove location" on now start playing straight
    away. The stripped file used to keep its index at the end, so players had to fetch the end of the
    file before the first frame.
  - Stripping now keeps only the audio and video; the phone's extra metadata track goes too.
  - A video trimmed in the gallery editor, or a Dolby Vision video, can't be stripped cleanly, so you
    are asked before it goes up unstripped. (Before, a trimmed video could come back with a moment
    of the part you cut out.)

  Recorded voice messages go through `VoiceAnonymizer`/`VoiceMessageRecorder`, not this path.
- `gradle/libs.versions.toml` stays at `2.4.0-SNAPSHOT`; the integrator session pins `2.4.0` when the
  library is released. The branch cannot be merged before that pin.
- Status: implementation, review and the device matrix are done. Next, the user reviews, pushes and
  opens the PR to upstream. Close bead dkamy-dsr when merged; dkamy-c8v is unblocked. Possible new
  beads: a clearer message in the strip-failed dialog for trimmed clips, the library-side fast-start
  log in `Transcoder.finalizeOutput`.
- `./gradlew --stop` was not run, because stopping the shared daemons kills a desktop app another
  session started with `./gradlew :desktopApp:run`. Run it when no other session is building.

## 8i5: compression fallback honours the strip

Brief: `amethyst/plans/2026-11-12-8i5-fallback-strip.md`. Bead dkamy-8i5 (P1, bug, found during dsr).
Same branch and PR, on top of `cf4ee6433c`. No version change, not pushed.

### Outcome

The media dialog (`ImageVideoDescription`) now passes the user's strip choice to the uploader for a
video that will be compressed, instead of `false`. When the encode falls back to the original (60 s
timeout, output not smaller, encoder failure, cancel), the uploader now strips it: since dsr, that
means a fast-start `Mp4Sanitizer` file. A successful encode is unchanged: the uploader still trusts it
and skips the stripper. The rule the uploader applies is now a tested commons function,
`StripDecision.shouldStrip`.

| Commit | |
|---|---|
| `1b8d07635c` | fix(uploads): a compression fallback still strips the video's metadata |
| `1e2e2de90c` | refactor(uploads): VideoUploadControls KDoc fits all three dialogs, shorter uploader comment (`/simplify`) |
| `16523953f0` | docs(uploads): say why a compression fallback reaches the stripper (Kotlin review) |

### Decisions

1. **Extract `StripDecision`.** Taken as recommended. The extraction touched only the one `if` in
   `stripAfterCompression`. I folded the `if (!stripMetadata) return compressed.uri` early exit into
   the call; the brief left that choice to me. Kept separately, the call site would always pass
   `true`. The Kotlin review checked every input, including a GIF converted to MP4 at UNCOMPRESSED
   and the `AvifMetadataNotVerifiableException` path, and found the behaviour identical.
2. **The hidden toggle keeps the last visible value.** Taken as recommended. The brief's reason, "as
   `NewMediaView` and `ChatFileUploadDialog` do", does not hold: those two dialogs never hide the strip
   toggle (see Divergences). The recommendation still fits on its own terms: it keeps a choice the user
   made moments earlier in the same dialog, and otherwise the account default
   (`stripLocationOnUpload`, which the toggle starts from).
3. **Commit split.** As recommended: one `fix` commit for the change and its tests. `/simplify` and
   the Kotlin review each produced a small follow-up commit.

### Changes

- `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/ImageVideoDescription.kt`:
  the Add button passes `stripMetadata`; the `effectiveStripMetadata` local is gone. The toggle is still
  hidden when `isVideoWithCompression`, and the comment above it says the choice is still passed
  through because the encode can fall back.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/VideoUploadControls.kt`:
  the `videoWillBeCompressed` KDoc no longer calls the strip toggle moot. It says a dialog *may* hide
  the toggle, but its value must still reach the uploader.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/StripDecision.kt`
  (new): `StripDecision.shouldStrip(stripMetadata, isVideo, compressionApplied) = stripMetadata &&
  !(isVideo && compressionApplied)`, with the brief's KDoc. Pure, no imports, nothing for `cli` to pick up.
- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/AndroidMediaUploader.kt`:
  `stripAfterCompression` computes the mime type, `isVideo` and `compressionApplied` as before, returns
  `compressed.uri` when `shouldStrip` is false, and otherwise calls `MetadataStripper.strip`. The
  `StrippingResult(compressed.uri, true)` placeholder branch is gone.
- `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/StripDecisionTest.kt`
  (new): four tests.

### Divergences

- **The sibling dialogs do not hide the toggle.** The brief says `NewMediaView.kt:259` and
  `ChatFileUploadDialog.kt:270` hide the strip toggle on `controls.videoWillBeCompressed`. Those
  lines guard the H.265 switch and the HDR hint. Both dialogs show the strip toggle unconditionally
  (`NewMediaView.kt:273`, `ChatFileUploadDialog.kt:274`). Only `ImageVideoDescription` hides it (since
  `127079ede3`). The fix is unaffected: passing the value through is what the siblings do. But the
  `VideoUploadControls` KDoc the brief asked for ("the toggle is hidden") was true for only one of its
  three callers, so `/simplify` reworded it to "a dialog may hide the strip toggle".
- **The uploader comment** is one sentence pointing at why a fallback reaches the stripper, not a
  restatement of the `StripDecision` KDoc.

### Tests

- `StripDecisionTest`: **4/4**. A re-encoded video is not stripped. A video whose encode fell back is
  stripped (the bug's case). An image is stripped whether or not it was compressed. Strip off never
  strips, over all four combinations. Together these cover all eight inputs.
- `VideoUploadControlsTest`: **5/5**, unchanged (`uncompressedKeepsTheStripToggle` included).
- `MetadataStripperTest`: **9/9**.
- `:amethyst:testPlayDebugUnitTest`: **1,541 tests, 0 failures, 0 errors, 10 skipped** (same as dsr).
- `:commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.upload*"`: **253 tests, 0
  failures**.
- Static: `spotlessApply`, `:commonsUI:compileKotlinJvm`, `:amethyst:compilePlayDebugKotlin` and
  `:amethyst:lintPlayDebug` pass at `16523953f0`.
- The dialog change itself has no test: `commonsUI` has no Compose UI test harness, and the change is
  one identifier. `stripAfterCompression` is private and needs Android types, so the wiring that feeds
  `StripDecision` (`compressionApplied` from `compressed.uri != originalUri`) has no unit test either.
- Device: passed. Pixel 9a, Android 17, 2026-11-12 19:13: the benchmark build at `16523953f0`
  (2.4.0-SNAPSHOT from fork `a697ec2`), the tester account, the local haven server. The integrator
  session ran it with the user; I re-ran the atom walk, `ffprobe` tags and `shasum` on the pulled files
  and got the same results.
  - Source: `lowbitrate_240p_5s.mp4`, cut with ffmpeg from the dsr Pixel clip so the HIGH encode
    would come out larger. H.264 136x240 at about 360 kbps plus AAC, 5.017 s, 225,608 bytes, sha256
    `7ebc79af1ea8180e…`. Top level `ftyp 32, free 8, mdat 219904, moov 5664` (`moov` last), a
    `udta` box, and the tags `location`, `location-eng`, `com.android.model`,
    `com.android.manufacturer`, `com.android.capture.fps`.
  - Posted at HIGH with strip at the account default (on); the toggle was hidden. Logcat at 19:13:37
    showed `W/VideoCompressionHelper: Compressed file larger than original. Using original.`, so the
    fallback ran. There was no `MetadataStripper` failure and no "not fast-start" warning.
  - Blob `a482d3fa58d1f3ae…`: 227,529 bytes, top level `ftyp 24, moov 4401, free 3192, mdat 219904`
    (fast-start). H.264 136x240 plus AAC, 5.002 s. `moov` holds `mvhd`, two `trak` and `meta`, with
    no `udta`. The only tags are `creation_time` (the strip time) and `com.android.version=17`;
    location, model, manufacturer and capture fps are gone. Before `1b8d07635c` this upload would
    have been the source as it was (`moov` last, tags intact).

### Findings

- **Stale compiler output in `commons`.** The first build failed with a Kotlin IR-lowering "should not
  be called" error in `UriToRoute.kt`, a file this change does not touch. `./gradlew :commons:clean`
  fixed it. It was leftover state in the worktree, not the change.
- **`CordnGroupChatScreen.kt:1077-1093` has its own copy of the rule** (out of scope). It calls
  `compressIfNeeded` and `uploader.stripMetadata` itself, because it encrypts the bytes, and strips
  whenever the toggle is on. That is safe, but it re-strips a video the compressor already
  re-encoded. A "compress and strip" call on `MediaUploader` for callers that encrypt would let it
  use `StripDecision`.
- **Long-form cover images are never stripped** (out of scope). `LongFormPostViewModel.kt:490` uploads
  the cover with `stripMetadata = false` on purpose, so a camera photo used as a cover keeps its
  location whatever `stripLocationOnUpload` says. It is the same class of gap as 8i5, for images.
- **Desktop keeps its own rule.** `DesktopMediaUploader` uses `stripMetadata && !prepared`, where
  `prepared` means "ffmpeg output", including the strip of a failed encode and a GIF converted to MP4.
  It should not share `StripDecision`: with a source MIME of `image/gif`, a converted GIF would be
  stripped twice.

### Review findings not acted on

From `/simplify`:
- *Make `StripDecision` a top-level function, like `hdrUploadOutcome`.* Kept as the brief's `object`;
  the integrator refers to it by that name.
- *Drop the extended comment in `ImageVideoDescription`.* Kept: the brief asks for it, and it is the
  one place the hidden toggle is decided.
- *Inline `isVideoWithCompression`.* Left: it predates this change and is still used twice.
- *The dialog regression has no test.* As the brief decided (no Compose harness).

From the Kotlin review (approve; no CRITICAL, HIGH or MEDIUM findings):
- *No uploader-level test; move `compressionApplied` into `StripDecision` someday.* Not done:
  `compressionApplied` compares Android `Uri`s, which commons cannot hold. Noted under Tests.

### Status

Done, device check included. Release note (draft): "A video posted with compression on and
'remove location' on is now stripped even when compression can't make it smaller or times out.
Before, the original went up with its location." Close dkamy-8i5 with the dsr PR.

## Whole-branch Kotlin review (after 8i5)

A fresh `ecc:kotlin-review` of `e3f14a9c8f..HEAD`, run at the user's request. Verdict: FAIL on one
HIGH, the `2.4.0-SNAPSHOT` pin, which resolves only from mavenLocal. That is the release gate the
integrator already owns, not a code defect; the reviewer's verdict becomes PASS once `2.4.0` is pinned.
No CRITICAL findings.

| Commit | |
|---|---|
| `55710391fe` | fix(uploads): a cancelled upload deletes its compressed temp file |

- **MEDIUM, temp file left after a late cancel: the stated leak did not hold, but a nearby one did.**
  - The reviewer said a cancel landing just after a successful `sanitize` leaves the stripped file in
    the cache. It does not: `runInterruptible(Dispatchers.IO)` is a `withContext`, which throws
    `CancellationException` on the way out, and the catch deletes the file. The uploader's
    `finally { deleteTempUri(finalUri) }` would catch it too. A new test pins this.
  - The real gap was one level up. Since `strip` is suspend, it can throw `CancellationException`,
    and the uploader's `try` around `stripAfterCompression` caught only the AVIF exception, so the
    compressor's temp file (a GIF converted to MP4) was left behind. That leak already existed when
    cancellation landed at the strip-failed prompt. `upload` and `uploadEncrypted` now delete it
    before rethrowing.
- **LOW, tests: done.** The MP3 routing test now checks its result. A new test covers a non-IO
  sanitizer exception (original returned unstripped, no temp file). The cancellation test is renamed
  so it no longer implies the test proves the thread interrupt itself.
- **MEDIUM, the hidden toggle now decides the fallback: not changed, for the user.** In
  `ImageVideoDescription`, a user who turns strip off at UNCOMPRESSED and then moves the slider hides
  the toggle with "off" still set, so a fallback uploads the original unstripped, by a control they
  can no longer see. Before 8i5 every fallback from this dialog went up unstripped, so 8i5 only
  improves on that. Showing the toggle always, as `NewMediaView` and `ChatFileUploadDialog` do, is the
  natural fix, but which dialogs show the toggle was out of 8i5's scope.
- **Not acted on (LOW):**
  - Hoisting `createTempFile` out of the `try` would turn a full cache into an upload error instead of
    the strip-failed prompt.
  - The duplicate fast-start warning and the `runCatching` in `onSuccess` were weighed and kept
    earlier; see the dsr sections above.

Tests after `55710391fe`: `MetadataStripperTest` **11/11**; `:amethyst:testPlayDebugUnitTest` **1,543
tests, 0 failures, 0 errors, 10 skipped**; `:amethyst:lintPlayDebug` and
`:amethyst:compilePlayDebugAndroidTestKotlin` pass.

### Library snapshot `1e4c3bd`

The integrator republished 2.4.0-SNAPSHOT from fork `1e4c3bd` (same signatures). Behaviour changes
inside the contract:
- Samples before the edit list are refused on video tracks only; audio priming is copied (see the
  trimmed-clips finding).
- An interrupt during the fast-start pass now throws `InterruptedIOException` instead of returning
  `fastStart = false`. `sanitizeMp4`'s `runInterruptible` plus `ensureActive()` path already handles
  that as a cancellation, so nothing changes here.
- The same-file guard (input equal to output) also covers Uris without a scheme. It cannot trigger
  from Amethyst, whose output is always a fresh cache file.

Rebuilt against it, no code change: `:amethyst:testPlayDebugUnitTest` **1,543 tests, 0 failures, 0
errors, 10 skipped** (the tests reran at 19:30, after the AAR landed at 19:27); `MetadataStripperTest`
**11/11**; `:amethyst:lintPlayDebug`, `:amethyst:compilePlayDebugKotlin` and
`:amethyst:compilePlayDebugAndroidTestKotlin` pass.

# Brief: dsr, delegate the video/AAC privacy strip to LightCompressor-enhanced's `Mp4Sanitizer`

Bead: dkamy-dsr (P1; blocks dkamy-c8v). Library side: LightCompressor-enhanced **2.4.0**, being
implemented in parallel from
`/Users/david/StudioProjects/LightCompressor-fork/LightCompressor/docs/briefs/2026-11-09-dsr-mp4-sanitizer.md`
(read it: the API contract below is copied from it). Vitor has agreed to the move.

Branch: `feat/mp4-sanitizer` from `main-upstream` (`e3f14a9c8f`, = `upstream/main`), in a git
worktree. After `git worktree add`, copy `local.properties` from the main checkout
(`cp /Users/david/StudioProjects/amethyst/local.properties ./local.properties`) or the Android
Gradle tasks won't find the SDK. Don't push; the PR goes to upstream later, by the user.

Integration: until the library snapshot exists you can write everything against the API below; it
will not compile. The library session publishes
`com.github.davotoula:LightCompressor-enhanced:2.4.0-SNAPSHOT` to `~/.m2` (Amethyst's
`settings.gradle.kts` lists `mavenLocal()` first), and the integrator (session `amethyst-2b`)
tells you when it is there. Then set `lightcompressor-enhanced = "2.4.0-SNAPSHOT"` in
`gradle/libs.versions.toml` and build. The final pin to the released `2.4.0` is the integrator's.

## Why

`MetadataStripper.stripVideoMetadata` / `stripAudioMetadata` re-mux through `MediaMuxer`
(`remuxTracks`). `MediaMuxer` reserves ~3 KB for `moov` at the front and writes it after `mdat`
when the clip has more samples than fit — anything past a few seconds — so the output is
`ftyp, free 3192, mdat, moov` and every player needs a second range request before the first
frame. The strip is on by default (`AccountSettings.stripLocationOnUpload = true`), so this is the
path every UNCOMPRESSED video and `.m4a` upload takes, and also every compression fallback with
strip on (`stripAfterCompression` sees `compressed.uri == originalUri` and strips). Desktop's strip
is `ffmpeg -c copy … -movflags +faststart` and is already fast-start; Android should match.

Rule, decided 2026-11-09 with the user: **every MP4 Amethyst writes is fast-start; a file Amethyst
does not write goes up untouched.**

| Quality | Strip | Amethyst does | Result |
|---|---|---|---|
| UNCOMPRESSED | off | nothing | bytes verbatim, hash-stable |
| UNCOMPRESSED | on | `Mp4Sanitizer.sanitize` | `ftyp, moov, mdat`; no `udta`/`meta` |
| any level | on/off | LightCompressor encode | `ftyp, moov, mdat` |
| any level, encode fell back | on | `Mp4Sanitizer.sanitize` on the original | `ftyp, moov, mdat` |
| any level, encode fell back | off | nothing | original verbatim |

Rows 1, 3 and 5 are already what the code does; this brief delivers rows 2 and 4 and adds the
"never silent" check for row 3.

## Library API (contract; see the library brief for semantics)

```kotlin
package com.davotoula.lightcompressor.sanitizer

data class SanitizeResult(val file: File, val fastStart: Boolean)

object Mp4Sanitizer {
    @WorkerThread @Throws(IOException::class)
    fun sanitize(context: Context, input: Uri, output: File): SanitizeResult
    fun isFastStart(file: File): Boolean
    fun isMuxableIntoMp4(mime: String): Boolean
}
```

`sanitize` copies the audio and video tracks into a fresh MPEG-4 container (no `udta`/`meta`/`uuid`,
rotation hint reapplied), drops every other track (timed metadata such as a Pixel's
`application/meta` or an iPhone's `mebx`, text), and fast-starts it; `fastStart = false` means the
remux succeeded but the fast-start pass did not. It throws `IOException` — and leaves no `output` —
when the source is unreadable, has no audio/video track, has an audio/video track that cannot go
into MP4 (Opus, MP3) or the remux fails. Residuals, documented in its KDoc: `MediaMuxer`'s own
creation time and a `com.android.version` meta key. It is synchronous and blocking; call it from `Dispatchers.IO`.

## What exists (Amethyst)

- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt`:
  imports `android.media.{MediaCodec, MediaExtractor, MediaFormat, MediaMetadataRetriever, MediaMuxer}`;
  `DEFAULT_REMUX_BUFFER_SIZE`; `extractorToCodecFlags` (68–136); `remuxTracks` (138–209);
  `stripVideoMetadata` (318–351); `stripAudioMetadata` (353–383, with the `mp4a`/`aac` primary-track
  check); `stripMp3Metadata` (ID3 byte surgery, stays); `strip(uri, mimeType, context)` dispatcher
  (483–492). `StrippingResult(uri, stripped)`; a false `stripped` makes the caller run
  `onStrippingFailed()` (a dialog) — that contract stays.
- `AndroidMediaUploader.stripAfterCompression` (640–672): if `isVideo && compressionApplied`,
  trusts the compressor; otherwise `MetadataStripper.strip(...)`. **No logic change needed** —
  rows 2 and 4 both go through `MetadataStripper.strip`, which is what changes.
- `AndroidMediaUploader.compressIfNeeded` (623–634): UNCOMPRESSED never calls `MediaCompressor`
  (row 1/2 gate), and `MediaCompressor.compress` returns the original at UNCOMPRESSED too.
- `VideoCompressionHelper.compressVideo` (79–229): `isStreamable = true`; `onSuccess` resumes with
  the compressed path. Fallbacks (60 s `withTimeoutOrNull`, "not smaller", `onFailure`,
  `onCancelled`) return `MediaCompressorResult(uri, contentType, null)` — the original.
- Tests: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripperTest.kt`
  (JUnit 4 + mockk, relaxed `Context`/`ContentResolver`/`Uri` mocks; covers AVIF only).
  `MediaCompressorTest.kt` in `ui/components/`.
- Logging is Quartz's `com.vitorpamplona.quartz.utils.Log`, lambda form for interpolation,
  throwable as the third argument in a catch (AGENTS.md "Kotlin Style").

## Changes

### `MetadataStripper.kt`

- `stripVideoMetadata(uri, context)` and `stripAudioMetadata(uri, context)` become thin: create
  `File.createTempFile("stripped_video_", ".mp4", context.cacheDir)` (`"stripped_audio_"`, `.m4a`),
  call `Mp4Sanitizer.sanitize(context, uri, tempFile)`, return `StrippingResult(tempFile.toUri(), true)`.
  If `result.fastStart` is false: `Log.w("MetadataStripper") { "Sanitized $kind is not fast-start: ${tempFile.name}" }`
  and still return it stripped — a stripped, non-streamable file is correct for privacy, which is
  what the user asked for. On `IOException`: `Log.w("MetadataStripper", "Failed to strip $kind metadata", e)`,
  delete the temp file if it exists, return `StrippingResult(uri, false)` (today's contract).
  `CancellationException` rethrows as today.
- Delete `remuxTracks`, `extractorToCodecFlags`, `DEFAULT_REMUX_BUFFER_SIZE`, the AAC pre-check
  in `stripAudioMetadata` (the library's `isMuxableIntoMp4` does it and throws), and the
  `MediaCodec`/`MediaExtractor`/`MediaFormat`/`MediaMetadataRetriever`/`MediaMuxer`/`ByteBuffer`
  imports. Keep `strip(...)`'s dispatch exactly: `audio/mpeg` → MP3, other `audio/*` → sanitizer.
- Add a KDoc on the object saying video and MP4 audio stripping is a container rewrite by the
  library's `Mp4Sanitizer` (what it removes, the residuals), and that UNCOMPRESSED without strip
  never reaches here.

### `VideoCompressionHelper.kt` (row 3, "never silent")

In `onSuccess`, after the size sanity check and before resuming with the compressed file:
`if (!Mp4Sanitizer.isFastStart(File(path))) Log.w(LOG_TAG) { "Compressed output is not fast-start: $path" }`.
Read-only, a few atom headers. Do not fail or alter the upload on it.

### `gradle/libs.versions.toml`

`lightcompressor-enhanced = "2.4.0-SNAPSHOT"` once the snapshot is published. Same artifact, no
new dependency, so the licensing step in AGENTS.md does not apply.

### Docs

- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/` has no README; the KDoc
  above is the documentation. If `.claude/skills/android-expert` mentions `MetadataStripper` or
  `MediaMuxer`, update that line (grep; `tools/agent-hooks/agent_config_check.py` checks cited paths).
- No user-visible string changes.

## Scope

| File | Change |
|---|---|
| `service/uploads/MetadataStripper.kt` | delegate video/AAC to `Mp4Sanitizer`; remove the remux code and `android.media.*` imports except `ExifInterface`'s |
| `service/uploads/VideoCompressionHelper.kt` | `isFastStart` warn in `onSuccess` |
| `gradle/libs.versions.toml` | `2.4.0-SNAPSHOT` |
| `src/test/.../MetadataStripperTest.kt` | tests 1–5 |
| `src/test/.../VideoCompressionHelperTest.kt` (new, only if cheap — see decision 2) | test 6 |

Not in scope: `AndroidMediaUploader` logic, `MediaCompressor`, `VoiceAnonymizer` /
`VoiceMessageRecorder` (bead dkamy-8oe), desktop (`FfmpegVideoCompressor` already does this),
`commons`, anything in `commonsUI`, any string. Do not touch `MediaCompressorFileUtils`.

## Tests

Unit (`./gradlew :amethyst:testPlayDebugUnitTest --tests "*MetadataStripper*"`), in
`MetadataStripperTest`, with `mockkObject(Mp4Sanitizer)` and the existing relaxed mocks; use a
`TemporaryFolder` for `context.cacheDir`:

1. `video/mp4` → `sanitize` called once with the uri and a file under `cacheDir`; result uri is
   that file, `stripped == true`.
2. `audio/mp4` and `audio/aac` → `sanitize` called; `audio/mpeg` → `sanitize` **not** called
   (MP3 path runs; it will fail on the relaxed resolver, which is fine — assert only the
   non-call).
3. `sanitize` throws `IOException("track 1 (audio/opus) cannot be written to MP4")` →
   `StrippingResult(uri, false)`, the temp file does not exist afterwards.
4. `sanitize` returns `fastStart = false` → still `stripped == true` and the result uri is the
   temp file. The warning is a log line; no test in `amethyst/src/test` captures Quartz `Log`
   today, so don't build a capture for it.
5. `image/jpeg` and AVIF paths unchanged: the three existing AVIF tests still pass.
6. (`VideoCompressionHelper`, decision 2) `onSuccess` with a non-fast-start file logs a warning and
   still resumes with the compressed path.

Static: `./gradlew spotlessApply :amethyst:compilePlayDebugKotlin :amethyst:lintPlayDebug`;
`python3 tools/agent-hooks/agent_config_check.py` if a skill file changed. Then `./gradlew --stop`.

Device (the user runs it; **stop and ask** when you reach this step — the skill's rule): install
`:amethyst:installPlayDebug` on the Pixel 9a, upload the same 20-second camera clip four times to
the user's Blossom server (UNCOMPRESSED strip on; UNCOMPRESSED strip off; MEDIUM strip on;
MEDIUM strip off), then pull each blob and run

```
ffprobe -v trace <blob> 2>&1 | grep -o "type:'[a-z0-9]\{4\}' parent:'root' sz: [0-9]* [0-9]*"
```

Expected: strip-off UNCOMPRESSED has the source's sha256 and its original atom order;
the other three begin `ftyp, moov`. Also `ffprobe -show_entries format_tags:stream` on the strip-on
UNCOMPRESSED blob shows no `location`, `com.android.capture.fps`, `com.android.manufacturer`/`model`
or `com.apple.*` tags and only audio/video streams (a Pixel camera clip has a third,
`application/meta` timed-metadata track; the sanitizer drops it — today's `remuxTracks` copies it
through). `com.android.version` alone is expected: `MediaMuxer` writes it itself. Note the
`Sanitized … not fast-start` / `Compressed output is not fast-start` lines in logcat — expected
count: zero.

## Decisions for the implementer session

1. **Keep `StrippingResult`'s boolean contract vs surface `fastStart`.** Recommended: keep it.
   `fastStart = false` is logged, not propagated; the dialog behind `onStrippingFailed` is about
   privacy, and a stripped-but-not-streamable file does not warrant it.
2. **Testing `VideoCompressionHelper.onSuccess`.** It is an anonymous `CompressionListener` inside a
   `suspendCancellableCoroutine`, so a unit test means mocking `VideoCompressor.start`. If
   `MediaCompressorTest` or another existing test already does that, add test 6 there; if it would
   need a new harness, skip it and say so in the report — the line is a log statement.
3. **Temp-file names.** Keep `stripped_video_*.mp4` / `stripped_audio_*.m4a`: `deleteTempUri` and
   the cache cleanup don't key on them, but logs and bug reports do.
4. **Snapshot timing.** If the snapshot is not published when you are ready to build, stop and ask
   rather than stubbing the API locally.

## Report

Write `amethyst/plans/2026-11-09-dsr-sanitizer-delegation-report.md`: commits, decisions taken,
divergences and why, test counts, the device results (atom lists and sha256 per upload), and
anything for another bead. Leave the version at `2.4.0-SNAPSHOT`; the integrator pins the release.

# AVIF Instrumented Tests — Implementation Plan

> **Status:** shipped — `AvifUploadPipelineInstrumentedTest.kt`, `MediaMimeTypesTest.kt`, and the three `.avif` fixtures are committed.
> _Audited 2026-06-30._

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Android on-device test layer that catches regressions in the AVIF upload pipeline and the Phase E bugs fixed manually (`C2`–`C8` plus follow-ups in commits `df84475d4`, `5a760c046`, `b9112550b`, `1db110cdf`).

**Architecture:** Four instrumented test files under `amethyst/src/androidTest/` plus one JVM unit test under `amethyst/src/test/`, all driven by three tiny pre-committed AVIF fixtures under `amethyst/src/androidTest/assets/avif/`. No CI wiring; tests run locally on an emulator. No Compose UI tests (matches existing repo pattern — zero Compose UI tests exist today). No production-code refactors.

**Tech Stack:** AndroidX Test (`@RunWith(AndroidJUnit4::class)` + `InstrumentationRegistry`), Coil 3, kotlinx-coroutines `runBlocking`, JUnit 4, `androidx.exifinterface.media.ExifInterface`, `android.graphics.ImageDecoder` (API 31+).

**Companion design:** `amethyst/plans/2026-05-27-avif-instrumented-tests-design.md`. Read §3 and §6 if anything in this plan is unclear.

**Source handover:** `~/docs/amethyst/2026-05-27-avif-instrumented-tests-handover.md` (Phase D Tasks D1–D4). Tasks 2 and 3 of this plan are the handover's D3 and D4 verbatim.

---

## File Structure

**Created:**

| Path | Type | Responsibility |
|---|---|---|
| `amethyst/src/androidTest/assets/avif/still-tiny-8x8.avif` | Binary fixture | 8×8 single-frame AVIF |
| `amethyst/src/androidTest/assets/avif/animated-tiny-3frames.avif` | Binary fixture | 8×8 3-frame animated AVIF |
| `amethyst/src/androidTest/assets/avif/still-tiny-8x8-exif-gps.avif` | Binary fixture | Still AVIF with GPS EXIF, triggers `MetadataStripper` fail-closed |
| `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt` | Instrumented test | `MediaCompressor` byte preservation, `MetadataStripper` clean+poisoned paths, `PreviewMetadataCalculator` blurhash/thumbhash/dim |
| `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt` | Instrumented test | Coil `AvifAnimatedDecoderFactory` produces `AnimatedImageDrawable` for animated AVIF |
| `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt` | JVM unit test | `isAvif()` + `extensionFromMimeType()` (covers BlossomUploader/NIP-96 extension-fallback regression) |
| `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/ThumbnailDiskCacheAvifInstrumentedTest.kt` | Instrumented test | `generateFromFile()` skips animated AVIF, caches still AVIF |
| `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifMetadataStripperPoisonedFixtureInstrumentedTest.kt` | Instrumented test | `MetadataStripper.strip()` throws `AvifMetadataNotVerifiableException` on EXIF-poisoned AVIF |

**Modified:**

| Path | Change |
|---|---|
| `amethyst/plans/2026-05-26-avif-support.md` | Add subsection to §9 "Running instrumented tests" with the commands from Task 8 |

---

### Task 1: Generate and commit AVIF fixtures

**Files:**
- Create: `amethyst/src/androidTest/assets/avif/still-tiny-8x8.avif`
- Create: `amethyst/src/androidTest/assets/avif/animated-tiny-3frames.avif`
- Create: `amethyst/src/androidTest/assets/avif/still-tiny-8x8-exif-gps.avif`

- [ ] **Step 1: Confirm tooling**

Run:
```bash
avifenc --version && avifdec --version && ffmpeg -version | head -1 && exiftool -ver
```
Expected: all four commands print versions. If any is missing:
```bash
brew install libavif ffmpeg exiftool
```

- [ ] **Step 2: Generate the three fixtures in a scratch dir**

Run:
```bash
mkdir -p ~/avif-test-assets && cd ~/avif-test-assets

ffmpeg -y -hide_banner -loglevel error -f lavfi -i 'color=red:s=8x8' -frames:v 1 still-8x8.png
avifenc -q 60 --min 30 --max 50 still-8x8.png still-tiny-8x8.avif

ffmpeg -y -hide_banner -loglevel error -f lavfi -i 'color=red:s=8x8'    -frames:v 1 a1.png
ffmpeg -y -hide_banner -loglevel error -f lavfi -i 'color=green:s=8x8'  -frames:v 1 a2.png
ffmpeg -y -hide_banner -loglevel error -f lavfi -i 'color=blue:s=8x8'   -frames:v 1 a3.png
avifenc --fps 10 a1.png a2.png a3.png animated-tiny-3frames.avif

cp still-tiny-8x8.avif still-tiny-8x8-exif-gps.avif
exiftool -overwrite_original -GPSLatitude=12.345 -GPSLongitude=67.89 still-tiny-8x8-exif-gps.avif
```

- [ ] **Step 3: Verify each fixture**

Run:
```bash
cd ~/avif-test-assets
avifdec --info still-tiny-8x8.avif        | grep -E 'Resolution|frames'
avifdec --info animated-tiny-3frames.avif | grep -E 'Resolution|frames|Repeat'
exiftool still-tiny-8x8-exif-gps.avif | grep -E 'GPS Latitude|GPS Longitude'
ls -la *.avif
```
Expected:
- still: `Resolution: 8x8`, no frame count > 1
- animated: `Resolution: 8x8`, `Frames: 3`
- poisoned: shows GPS Latitude and GPS Longitude
- all 3 files < 2 KB each

- [ ] **Step 4: Copy fixtures into the repo**

Run:
```bash
mkdir -p /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif
cp ~/avif-test-assets/still-tiny-8x8.avif \
   ~/avif-test-assets/animated-tiny-3frames.avif \
   ~/avif-test-assets/still-tiny-8x8-exif-gps.avif \
   /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif/

cd /Users/david/StudioProjects/amethyst
ls -la amethyst/src/androidTest/assets/avif/
git add amethyst/src/androidTest/assets/avif/
git status
```
Expected: 3 new files staged under `amethyst/src/androidTest/assets/avif/`.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
test(uploads): commit tiny AVIF fixtures for instrumented tests

still-tiny-8x8.avif: 8x8 single-frame AVIF for upload-pipeline test.
animated-tiny-3frames.avif: 8x8 3-frame animated AVIF for decode test.
still-tiny-8x8-exif-gps.avif: still AVIF with GPS EXIF that triggers
the AvifMetadataNotVerifiableException fail-closed path in MetadataStripper.

Generated with avifenc (libavif) + exiftool. All files < 2 KB.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add `AvifUploadPipelineInstrumentedTest` (handover D3)

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt`

- [ ] **Step 1: Write the test file**

Create `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt` with:

```kotlin
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.uploads

import android.os.Build
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvifUploadPipelineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    private fun copyAssetToCache(assetPath: String): File {
        val out = File(context.cacheDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    @Test
    fun stillAvifPassesThroughMediaCompressorUnchanged() = runBlocking {
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
        val originalBytes = avif.readBytes()

        val result = MediaCompressor().compress(
            uri = avif.toUri(),
            contentType = AVIF_MIME,
            applicationContext = context,
            mediaQuality = CompressorQuality.MEDIUM,
        )

        assertEquals(AVIF_MIME, result.contentType)
        val resultBytes = File(result.uri.path!!).readBytes()
        assertTrue(
            "AVIF bytes must be preserved through MediaCompressor",
            originalBytes.contentEquals(resultBytes),
        )
    }

    @Test
    fun animatedAvifPassesThroughMediaCompressorUnchanged() = runBlocking {
        val avif = copyAssetToCache("avif/animated-tiny-3frames.avif")
        val originalBytes = avif.readBytes()

        val result = MediaCompressor().compress(
            uri = avif.toUri(),
            contentType = AVIF_MIME,
            applicationContext = context,
            mediaQuality = CompressorQuality.MEDIUM,
        )

        assertEquals(AVIF_MIME, result.contentType)
        val resultBytes = File(result.uri.path!!).readBytes()
        assertTrue(
            "Animated AVIF bytes must be preserved (headline regression to prevent)",
            originalBytes.contentEquals(resultBytes),
        )
    }

    @Test
    fun avifMetadataStripperReturnsCleanFile() {
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
        val result = MetadataStripper.stripImageMetadata(avif.toUri(), context)
        assertTrue("Clean AVIF should be marked stripped=true", result.stripped)
        assertEquals("Clean AVIF URI should be the original", avif.toUri(), result.uri)
    }

    @Test
    fun avifPreviewMetadataGeneratesBlurhashAndThumbhash() {
        assumeTrue("AVIF decoding requires API 31+", Build.VERSION.SDK_INT >= 31)
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")

        val result = PreviewMetadataCalculator.computeFromUri(
            context = context,
            uri = avif.toUri(),
            mimeType = AVIF_MIME,
        )

        assertNotNull("PreviewMetadataCalculator must return non-null on API 31+", result)
        assertNotNull("AVIF blurhash should be generated via ImageDecoder", result!!.blurhash)
        assertNotNull("AVIF thumbhash should be generated via ImageDecoder", result.thumbhash)
        assertNotNull("AVIF dimensions should be returned", result.dim)
        assertEquals(8, result.dim!!.width)
        assertEquals(8, result.dim.height)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:compilePlayDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL. If symbol-not-found errors on `MediaCompressor`, `CompressorQuality`, `MetadataStripper`, `PreviewMetadataCalculator`, or `AVIF_MIME` — confirm each exists in `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/`. Since the test is in the same package, unqualified references should resolve.

- [ ] **Step 3: Run on an API 35 emulator**

Boot an API 35 emulator (Android Studio AVD Manager or `emulator -avd <name>`). Then:
```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.uploads.AvifUploadPipelineInstrumentedTest'
```
Expected: 4/4 tests pass. If `avifPreviewMetadataGeneratesBlurhashAndThumbhash` returns null on API 35, the AVIF decode path is broken — stop and investigate.

- [ ] **Step 4: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt
git commit -m "$(cat <<'EOF'
test(uploads): instrumented coverage for AVIF upload pipeline

Drives still + animated AVIF through MediaCompressor, MetadataStripper,
and PreviewMetadataCalculator on a real Android runtime. Asserts:
- AVIF bytes preserved (no JPEG re-encode)
- MetadataStripper returns stripped=true for clean AVIF
- ImageDecoder produces blurhash/thumbhash/dim on API 31+ (assumeTrue
  skips on older)

Headline regression covered: animated AVIF bytes preserved through
compression.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Add `AvifAnimatedDecodeInstrumentedTest` (handover D4)

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt`

- [ ] **Step 1: Write the test file**

Create `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt`:

```kotlin
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.images

import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.asDrawable
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvifAnimatedDecodeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    private fun copyAsset(assetPath: String): File {
        val out = File(context.cacheDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun avifLoader(): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AvifAnimatedDecoderFactory())
                add(AnimatedImageDecoder.Factory())
            }
            .build()

    @Test
    fun stillAvifDecodesToBitmap() = runBlocking {
        assumeTrue("AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
        val avif = copyAsset("avif/still-tiny-8x8.avif")

        val result = avifLoader().execute(
            ImageRequest.Builder(context).data(avif.toUri()).build(),
        )

        assertTrue("Expected SuccessResult, got ${result::class.simpleName}", result is SuccessResult)
        val bitmap = (result as SuccessResult).image.toBitmap()
        assertEquals(8, bitmap.width)
        assertEquals(8, bitmap.height)
    }

    @Test
    fun animatedAvifDecodesToAnimatedImageDrawable() = runBlocking {
        assumeTrue("Animated AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
        val avif = copyAsset("avif/animated-tiny-3frames.avif")

        val result = avifLoader().execute(
            ImageRequest.Builder(context).data(avif.toUri()).build(),
        )

        assertTrue("Expected SuccessResult", result is SuccessResult)
        val drawable = (result as SuccessResult).image.asDrawable(context.resources)
        assertTrue(
            "Animated AVIF must decode to AnimatedImageDrawable (Coil falls back to BitmapDrawable without our factory)",
            drawable is AnimatedImageDrawable,
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:compilePlayDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL. If `coil3.toBitmap` or `coil3.asDrawable` don't resolve, the Coil 3 version may expose them via different extension paths — try `import coil3.Image.toBitmap` or fall back to `(result.image as coil3.BitmapImage).bitmap`. Check the Coil version in `gradle/libs.versions.toml` if you need to look up the API.

- [ ] **Step 3: Run on an API 35 emulator**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.images.AvifAnimatedDecodeInstrumentedTest'
```
Expected: 2/2 tests pass on API 31+. If `animatedAvifDecodesToAnimatedImageDrawable` fails with `drawable is BitmapDrawable`, the `AvifAnimatedDecoderFactory` was not invoked — verify the factory is registered BEFORE Coil's `AnimatedImageDecoder.Factory()` in `avifLoader()`.

- [ ] **Step 4: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt
git commit -m "$(cat <<'EOF'
test(images): instrumented coverage for animated AVIF Coil decode

Verifies the custom AvifAnimatedDecoderFactory: still AVIF decodes to
Bitmap with expected dimensions, and animated AVIF decodes to
AnimatedImageDrawable (NOT a static BitmapDrawable, which is what
Coil's stock decoder would produce since its isAnimatedHeif sniff
misses the 'avis' brand).

Both tests assumeTrue(SDK_INT >= 31) so older emulators skip cleanly.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Extend `AvifUploadPipelineInstrumentedTest` with the poisoned-fixture assertion

**Files:**
- Modify: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt`

- [ ] **Step 1: Add the `fail` import**

In the imports block of `AvifUploadPipelineInstrumentedTest.kt`, add:
```kotlin
import org.junit.Assert.fail
```
(Keep the alphabetical order — it goes right after `import org.junit.Assert.assertTrue`.)

- [ ] **Step 2: Add the new test method**

Append the following test method inside `AvifUploadPipelineInstrumentedTest`, just before the closing `}` of the class:

```kotlin
    @Test
    fun poisonedAvifMetadataStripperThrowsAvifMetadataNotVerifiableException() {
        val avif = copyAssetToCache("avif/still-tiny-8x8-exif-gps.avif")
        try {
            MetadataStripper.stripImageMetadata(avif.toUri(), context)
            fail("Expected AvifMetadataNotVerifiableException for AVIF with GPS EXIF metadata")
        } catch (e: AvifMetadataNotVerifiableException) {
            assertNotNull("Exception must have a non-null message", e.message)
        }
    }
```

`AvifMetadataNotVerifiableException` is in the same package (per `MetadataStripper.kt:53`) so no import is required.

- [ ] **Step 3: Verify it compiles**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:compilePlayDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run only the new test on the emulator**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.uploads.AvifUploadPipelineInstrumentedTest#poisonedAvifMetadataStripperThrowsAvifMetadataNotVerifiableException'
```
Expected: 1/1 pass. If it fails because the stripper returned a result instead of throwing, GPS was not detected as sensitive — re-verify the fixture has GPS tags (`exiftool still-tiny-8x8-exif-gps.avif | grep GPS`). If GPS is present but no exception, inspect `MetadataStripper.inspectAvifMetadata` to confirm GPS tags are in its sensitive-tag list and adjust the fixture (try `-ImageDescription="test"` instead, which is unambiguously sensitive).

- [ ] **Step 5: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt
git commit -m "$(cat <<'EOF'
test(uploads): assert MetadataStripper fail-closes on AVIF with GPS EXIF

Uses the still-tiny-8x8-exif-gps.avif fixture to verify the
AvifMetadataNotVerifiableException path triggers on AVIFs with
sensitive metadata. This is the contract NewUserMetadataViewModel
depends on (line 218) to surface R.string.avif_metadata_strip_failed
instead of the generic "Upload cancelled" message (commit b9112550b).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Add `MediaMimeTypesTest` (JVM unit test)

**Files:**
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt`

This task covers the BlossomUploader/NIP-96 extension-fallback regression (commit `df84475d4`) by testing the helper both call sites delegate to. `MediaMimeTypes.kt` is pure-Kotlin (no Android imports), so this is a JVM unit test, not instrumented.

- [ ] **Step 1: Write the test file**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt`:

```kotlin
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.uploads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMimeTypesTest {
    @Test
    fun isAvifReturnsTrueForCanonicalAvifMime() {
        assertTrue(isAvif("image/avif"))
    }

    @Test
    fun isAvifReturnsTrueForUppercaseAvifMime() {
        assertTrue(isAvif("IMAGE/AVIF"))
    }

    @Test
    fun isAvifReturnsFalseForNonAvifMimes() {
        assertFalse(isAvif("image/jpeg"))
        assertFalse(isAvif("image/png"))
        assertFalse(isAvif("image/gif"))
        assertFalse(isAvif("image/webp"))
        assertFalse(isAvif("image/heic"))
        // image/avif-sequence is intentionally NOT recognized (not IANA-registered)
        assertFalse(isAvif("image/avif-sequence"))
    }

    @Test
    fun isAvifReturnsFalseForNullOrEmpty() {
        assertFalse(isAvif(null))
        assertFalse(isAvif(""))
    }

    @Test
    fun extensionFromMimeTypeReturnsAvifForAvifMime() {
        // Regression: BlossomUploader + NIP-96 multipart filename rely on this
        // helper to infer .avif when MimeTypeMap doesn't know AVIF (older
        // Android) and the upload server requires a filename with extension.
        assertEquals("avif", extensionFromMimeType("image/avif"))
        assertEquals("avif", extensionFromMimeType("IMAGE/AVIF"))
    }

    @Test
    fun extensionFromMimeTypeReturnsNullForNonAvifMimes() {
        // This helper is AVIF-only by design; non-AVIF callers use MimeTypeMap.
        assertNull(extensionFromMimeType("image/jpeg"))
        assertNull(extensionFromMimeType("image/png"))
        assertNull(extensionFromMimeType(null))
        assertNull(extensionFromMimeType(""))
    }

    @Test
    fun avifMimeAndExtensionConstantsAreStable() {
        // Wire-level constants — changing them is a backwards-incompatible
        // protocol change for uploaded URLs.
        assertEquals("image/avif", AVIF_MIME)
        assertEquals("avif", AVIF_EXTENSION)
    }
}
```

- [ ] **Step 2: Run the test (no emulator needed)**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:testPlayDebugUnitTest --tests 'com.vitorpamplona.amethyst.service.uploads.MediaMimeTypesTest'
```
Expected: 7/7 pass.

- [ ] **Step 3: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt
git commit -m "$(cat <<'EOF'
test(uploads): JVM unit tests for MediaMimeTypes AVIF helpers

Covers isAvif() and extensionFromMimeType() — the helpers that
BlossomUploader and NIP-96 multipart filename construction delegate
to when inferring the .avif filename extension for upload servers
(commit df84475d4).

Pure-Kotlin helpers, no Android dependencies — JVM unit test, not
instrumented.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Add `ThumbnailDiskCacheAvifInstrumentedTest`

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/ThumbnailDiskCacheAvifInstrumentedTest.kt`

Covers commit `5a760c046` — `generateFromFile()` must skip caching animated AVIF (otherwise the avatar permanently freezes on the first frame) while still caching still AVIF.

- [ ] **Step 1: Write the test file**

Create `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/ThumbnailDiskCacheAvifInstrumentedTest.kt`:

```kotlin
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.images

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ThumbnailDiskCacheAvifInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    private lateinit var cacheDir: File
    private lateinit var cache: ThumbnailDiskCache

    @Before
    fun setUp() {
        cacheDir = File(context.cacheDir, "thumbnail-test-${UUID.randomUUID()}")
        cache = ThumbnailDiskCache(cacheDir)
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun copyAsset(assetPath: String): File {
        val out = File(context.cacheDir, "${UUID.randomUUID()}-${assetPath.substringAfterLast('/')}")
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    @Test
    fun animatedAvifIsNotCached() {
        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.avif"
        val source = copyAsset("avif/animated-tiny-3frames.avif")

        val saved = cache.generateFromFile(url, source)

        assertFalse(
            "generateFromFile must return false for animated AVIF (would otherwise freeze avatar on first frame)",
            saved,
        )
        assertNull(
            "load() must return null for an animated-AVIF URL that was skipped",
            cache.load(url),
        )
    }

    @Test
    fun stillAvifIsCachedAsBitmap() {
        // generateFromFile uses BitmapFactory.decodeFile to read the source,
        // which requires platform AVIF decode support (API 31+). On older
        // devices the still-AVIF path returns false because decode fails,
        // not because of the animated-skip branch.
        assumeTrue("Still AVIF decode requires API 31+", Build.VERSION.SDK_INT >= 31)

        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.avif"
        val source = copyAsset("avif/still-tiny-8x8.avif")

        val saved = cache.generateFromFile(url, source)

        assertTrue("generateFromFile must return true for still AVIF", saved)
        val bitmap = cache.load(url)
        assertNotNull("load() must return a non-null Bitmap for a cached still AVIF", bitmap)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:compilePlayDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run on an API 35 emulator**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.images.ThumbnailDiskCacheAvifInstrumentedTest'
```
Expected: 2/2 pass on API 35. If `stillAvifIsCachedAsBitmap` returns `saved=false`, `BitmapFactory.decodeFile` failed to decode the still AVIF — verify the emulator system image supports AVIF (Android 12+ required) and that the fixture is well-formed (`avifdec --info` from Task 1).

- [ ] **Step 4: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/ThumbnailDiskCacheAvifInstrumentedTest.kt
git commit -m "$(cat <<'EOF'
test(images): instrumented coverage for ThumbnailDiskCache AVIF skip

Verifies the animated-AVIF cache-skip contract from commit 5a760c046:
- Animated AVIF: generateFromFile returns false, load returns null
  (otherwise the avatar permanently freezes on the first frame because
  the cached JPEG thumbnail replaces the animated source).
- Still AVIF on API 31+: generateFromFile returns true, load returns
  a non-null Bitmap (still AVIF is safe to cache as a JPEG thumbnail).

Test uses a per-test cacheDir and tears down to avoid cross-test
pollution.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Add `AvifMetadataStripperPoisonedFixtureInstrumentedTest`

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifMetadataStripperPoisonedFixtureInstrumentedTest.kt`

This is a standalone test for the `strip()` dispatcher (the entry point `NewUserMetadataViewModel.kt:218` actually calls). Task 4 covered `stripImageMetadata`; this task covers `strip` so a future refactor that bypasses one path doesn't silently break the other.

- [ ] **Step 1: Write the test file**

Create `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifMetadataStripperPoisonedFixtureInstrumentedTest.kt`:

```kotlin
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.uploads

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvifMetadataStripperPoisonedFixtureInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context

    private fun copyAssetToCache(assetPath: String): File {
        val out = File(context.cacheDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    @Test
    fun stripDispatcherThrowsAvifMetadataNotVerifiableExceptionForPoisonedAvif() {
        // Regression for commit b9112550b: NewUserMetadataViewModel calls
        // MetadataStripper.strip(uri, "image/avif", context) at line 218.
        // The viewmodel only catches AvifMetadataNotVerifiableException to
        // surface the AVIF-specific error string; any other exception type
        // would fall through to "Upload cancelled" and confuse the user.
        val avif = copyAssetToCache("avif/still-tiny-8x8-exif-gps.avif")
        try {
            MetadataStripper.strip(avif.toUri(), AVIF_MIME, context)
            fail("Expected AvifMetadataNotVerifiableException for AVIF with GPS EXIF metadata")
        } catch (e: AvifMetadataNotVerifiableException) {
            assertNotNull("Exception must have a non-null message for UI display", e.message)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:compilePlayDebugAndroidTestKotlin
```
Expected: BUILD SUCCESSFUL. If `MetadataStripper.strip` is not found, open `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt` and confirm the signature at line 479. It should accept `(uri: Uri, mimeType: String?, context: Context)`. Adjust the test call accordingly if the signature differs.

- [ ] **Step 3: Run on an API 35 emulator**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.uploads.AvifMetadataStripperPoisonedFixtureInstrumentedTest'
```
Expected: 1/1 pass.

- [ ] **Step 4: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifMetadataStripperPoisonedFixtureInstrumentedTest.kt
git commit -m "$(cat <<'EOF'
test(uploads): assert MetadataStripper.strip() dispatcher fail-closes on poisoned AVIF

Covers the strip() dispatcher (the entry point NewUserMetadataViewModel
actually calls at line 218) for the AVIF/EXIF-poisoned regression from
commit b9112550b. If this fails-open or throws a non-AVIF exception,
the viewmodel falls through to the generic "Upload cancelled" message
instead of the AVIF-specific R.string.avif_metadata_strip_failed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Update `amethyst/plans/2026-05-26-avif-support.md` §9 with run instructions

**Files:**
- Modify: `amethyst/plans/2026-05-26-avif-support.md` (append a subsection to §9)

- [ ] **Step 1: Locate §9 in the AVIF support plan**

```bash
cd /Users/david/StudioProjects/amethyst
grep -n "^## " amethyst/plans/2026-05-26-avif-support.md
```
Find the line number of the `## 9` heading (or whatever §9 is titled). Then read it:
```bash
sed -n '<§9 line>,$p' amethyst/plans/2026-05-26-avif-support.md | head -80
```

- [ ] **Step 2: Append a "Running the AVIF tests locally" subsection at the end of §9**

Edit `amethyst/plans/2026-05-26-avif-support.md`. Add the following markdown at the end of §9 (immediately before the next `## ` heading, or at the very end of the file if §9 is last):

````markdown
### Running the AVIF tests locally

The AVIF test layer is **not wired into CI** — run it locally on an emulator before merging any
AVIF-touching change. See `amethyst/plans/2026-05-27-avif-instrumented-tests-plan.md` for the
full plan.

**JVM unit test** (no emulator needed):

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests 'com.vitorpamplona.amethyst.service.uploads.MediaMimeTypesTest'
```

**Instrumented tests** (boot an API 35 emulator first; API 28 is a useful second pass to verify
`assumeTrue` gates skip cleanly):

```bash
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.uploads'

./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.images'
```

**Regenerating fixtures** (only needed if you change the fixtures themselves):

```bash
brew install libavif ffmpeg exiftool   # one-time
# See Task 1 in 2026-05-27-avif-instrumented-tests-plan.md for the full generation recipe.
```
````

(The outer four-backtick fences are only for this plan doc. When pasting into the support plan, use the inner content — the three-backtick code blocks are what should end up in the file.)

- [ ] **Step 3: Verify the markdown renders**

Open `amethyst/plans/2026-05-26-avif-support.md` in any markdown viewer (or `head -<line>` it in the terminal) and visually confirm the new subsection appears at the end of §9 with code blocks intact and no broken fences.

- [ ] **Step 4: Commit**

```bash
cd /Users/david/StudioProjects/amethyst
git add amethyst/plans/2026-05-26-avif-support.md
git commit -m "$(cat <<'EOF'
docs(avif): document how to run the instrumented test layer

Adds a "Running the AVIF tests locally" subsection to §9 with the
JVM and instrumented test commands. Tests are not wired into CI;
this is the canonical reference for running them before a merge.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Final verification

- [ ] **Step 1: Run spotlessApply**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew spotlessApply
git status
```
Expected: BUILD SUCCESSFUL. If `git status` shows formatting-only changes, stage and amend them into the most recent test commit (NOT the docs or fixture commits):
```bash
git add <changed-files>
git commit --amend --no-edit
```
(Only `--amend` if the prior commit hasn't been pushed.)

- [ ] **Step 2: Run the JVM unit test**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew :amethyst:testPlayDebugUnitTest --tests 'com.vitorpamplona.amethyst.service.uploads.MediaMimeTypesTest'
```
Expected: 7/7 pass.

- [ ] **Step 3: Run the full instrumented test set on API 35**

```bash
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.uploads'

./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.images'
```
Expected on API 35:
- `service.uploads`: `AvifUploadPipelineInstrumentedTest` (5) + `AvifMetadataStripperPoisonedFixtureInstrumentedTest` (1) = 6 pass + any preexisting tests in the package.
- `service.images`: `AvifAnimatedDecodeInstrumentedTest` (2) + `ThumbnailDiskCacheAvifInstrumentedTest` (2) = 4 pass + any preexisting tests.

- [ ] **Step 4: Spot-check on API 28**

Boot an API 28 emulator and re-run the two commands from Step 3. Expected:
- `assumeTrue(SDK_INT >= 31)`-gated tests (`avifPreviewMetadataGeneratesBlurhashAndThumbhash`, both tests in `AvifAnimatedDecodeInstrumentedTest`, `stillAvifIsCachedAsBitmap`) skip cleanly — JUnit reports `assumption-failed`, not `failure`.
- Non-gated tests in `AvifUploadPipelineInstrumentedTest` may pass OR may fail-closed on the clean AVIF (older `ExifInterface` cannot read AVIF EXIF → throws `AvifMetadataNotVerifiableException`). If they fail, add `assumeTrue(SDK_INT >= 31)` to the affected methods and amend the relevant commit.

- [ ] **Step 5: Confirm clean state**

```bash
cd /Users/david/StudioProjects/amethyst
git log --oneline -10
git status
```
Expected:
- 6 new commits on `feat/avif-support`: 1 fixtures + 4 test additions + 1 test extension + 1 docs
- `git status` clean

- [ ] **Step 6: DO NOT push without explicit user approval**

This plan does not include a push step. CLAUDE.md and project policy require explicit user approval before pushing. Report the branch state to the user and wait.

---

## Notes

- **Branch:** This work continues on `feat/avif-support` (the parent AVIF feature branch). Do not start a new branch unless the user asks.
- **Commit count:** 6 new commits. Logical and reviewable in isolation. Do not squash without asking.
- **Re-running individual tests:** `--tests 'ClassName.methodName'` for JVM; `'-Pandroid.testInstrumentationRunnerArguments.class=fqcn#methodName'` for instrumented.
- **If `connectedPlayDebugAndroidTest` can't find an emulator:** run `adb devices` to confirm. The emulator must be running and unlocked before the gradle task starts.
- **Style:** Kotlin Style rule in CLAUDE.md — never use inline fully-qualified class names. All test files in this plan place imports for `AvifMetadataNotVerifiableException`, `AVIF_MIME`, `MetadataStripper` etc. (or rely on same-package resolution). Don't introduce `com.vitorpamplona.amethyst.service.uploads.MetadataStripper` inline in function bodies.

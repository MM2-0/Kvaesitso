# AVIF Support Implementation Plan

> **Status:** shipped — `service/uploads/MediaMimeTypes.kt` and the rest of the AVIF upload pipeline are present in `amethyst/`.
> _Audited 2026-06-30._

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AVIF (still and animated) work as a first-class image format in Amethyst across every user-visible surface — uploads (Blossom + NIP-96), feeds, profiles, DMs, emoji packs, reactions, gallery, and caching — with graceful no-crash fallback on Android API < 31.

**Architecture:** Small production diff (~150–250 LoC across ~6 files) that makes AVIF behave like animated WebP wherever WebP is special-cased. Centralize AVIF MIME helpers in a new `MediaMimeTypes.kt` consumed by `MediaCompressor`, `MetadataStripper`, `PreviewMetadataCalculator`, `Nip96Uploader`, and `BlossomUploader`. Coil's existing `AnimatedImageDecoder.Factory()` handles display on API 31+ with zero new composables. Real effort is testing: JVM unit tests + Android instrumented tests + a 50-row manual on-device verification.

**Tech Stack:** Kotlin 2.3.20, Android (`minSdk=26`, `compileSdk=37`), JUnit 4, MockK, kotlinx-coroutines-test, Coil 3 (`AnimatedImageDecoder`), `androidx.exifinterface`, Android platform `ImageDecoder` (API 28+).

**Companion docs:**
- Spec: `amethyst/plans/2026-05-26-avif-support.md`
- Manual test plan: `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`

**Branch:** `feat/avif-support` (already created, at `d1610bf97`).

---

## Phase A — Foundation: `MediaMimeTypes` helper

### Task A1: Create `MediaMimeTypes.kt` with AVIF helpers and constants

**Files:**
- Create: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypes.kt`

- [ ] **Step 1: Create the helper file**

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

// RFC 9081 defines image/avif for both still and animated AVIF.
// image/avif-sequence is NOT IANA-registered and is intentionally not handled.
const val AVIF_MIME = "image/avif"
const val AVIF_EXTENSION = "avif"

fun isAvif(contentType: String?): Boolean =
    contentType?.equals(AVIF_MIME, ignoreCase = true) == true

// Centralized fallback when MimeTypeMap.getExtensionFromMimeType returns null for AVIF.
// Older Android versions of MimeTypeMap don't know AVIF; servers reject extension-less filenames.
fun extensionFromMimeType(contentType: String?): String? =
    when {
        isAvif(contentType) -> AVIF_EXTENSION
        else -> null
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :amethyst:compilePlayDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypes.kt
git commit -m "feat(uploads): add MediaMimeTypes helper for AVIF detection

Centralizes image/avif MIME detection and the AVIF extension fallback used
by MediaCompressor, MetadataStripper, PreviewMetadataCalculator, and the
NIP-96 / Blossom uploaders in subsequent commits. Rejects image/avif-sequence
on purpose: RFC 9081 says image/avif covers both still and animated.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A2: Add unit tests for `MediaMimeTypes`

**Files:**
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt`

- [ ] **Step 1: Write the test file**

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
    fun `isAvif matches image avif exactly`() {
        assertTrue(isAvif("image/avif"))
    }

    @Test
    fun `isAvif is case insensitive`() {
        assertTrue(isAvif("IMAGE/AVIF"))
        assertTrue(isAvif("Image/Avif"))
    }

    @Test
    fun `isAvif rejects null`() {
        assertFalse(isAvif(null))
    }

    @Test
    fun `isAvif rejects empty string`() {
        assertFalse(isAvif(""))
    }

    @Test
    fun `isAvif rejects image avif-sequence`() {
        // image/avif-sequence is intentionally not handled (not IANA-registered).
        assertFalse(isAvif("image/avif-sequence"))
    }

    @Test
    fun `isAvif rejects unrelated image types`() {
        assertFalse(isAvif("image/jpeg"))
        assertFalse(isAvif("image/png"))
        assertFalse(isAvif("image/webp"))
        assertFalse(isAvif("image/gif"))
    }

    @Test
    fun `isAvif rejects substring containment`() {
        // Defensively guard against future code that might accept partial matches.
        assertFalse(isAvif("image/avif-sequence"))
        assertFalse(isAvif("application/x-image-avif"))
    }

    @Test
    fun `extensionFromMimeType returns avif for image avif`() {
        assertEquals("avif", extensionFromMimeType("image/avif"))
    }

    @Test
    fun `extensionFromMimeType returns null for unknown types`() {
        assertNull(extensionFromMimeType("image/jpeg"))
        assertNull(extensionFromMimeType(null))
    }

    @Test
    fun `AVIF_MIME constant value`() {
        assertEquals("image/avif", AVIF_MIME)
    }

    @Test
    fun `AVIF_EXTENSION constant value`() {
        assertEquals("avif", AVIF_EXTENSION)
    }
}
```

- [ ] **Step 2: Run tests, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.MediaMimeTypesTest"`
Expected: `BUILD SUCCESSFUL`, 10 tests pass.

- [ ] **Step 3: Commit**

```bash
git add amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypesTest.kt
git commit -m "test(uploads): unit tests for MediaMimeTypes helper

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase B — Upload pipeline

### Task B1: Skip JPEG re-encode for AVIF in `MediaCompressor`

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaCompressor.kt` (the image branch around lines 79–83)
- Modify: `amethyst/src/test/java/com/vitorpamplona/amethyst/ui/components/MediaCompressorTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `MediaCompressorTest.kt` before the closing brace (the file already imports `assertEquals`, `coVerify`, `Compressor`, `MediaCompressor`, `CompressorQuality`, `runTest`, `mockk`, `MediaCompressorFileUtils`, `File`):

```kotlin
    @Test
    fun `AVIF media should not be re-encoded as JPEG`() =
        runTest {
            // setup
            val mockContext = mockk<Context>(relaxed = true)
            val mockUri = mockk<Uri>()

            mockkObject(MediaCompressorFileUtils)
            every { MediaCompressorFileUtils.from(any(), any()) } returns File("test")

            // Execute with AVIF MIME
            val result =
                MediaCompressor().compress(
                    uri = mockUri,
                    contentType = "image/avif",
                    applicationContext = mockContext,
                    mediaQuality = CompressorQuality.MEDIUM,
                )

            // Verify: original URI, original content type, no JPEG conversion, no size set
            assertEquals(mockUri, result.uri)
            assertEquals("image/avif", result.contentType)
            assertEquals(null, result.size)
            coVerify(exactly = 0) { Compressor.compress(any(), any(), any(), any()) }
        }

    @Test
    fun `AVIF MIME is case insensitive`() =
        runTest {
            val mockContext = mockk<Context>(relaxed = true)
            val mockUri = mockk<Uri>()
            mockkObject(MediaCompressorFileUtils)
            every { MediaCompressorFileUtils.from(any(), any()) } returns File("test")

            val result =
                MediaCompressor().compress(
                    uri = mockUri,
                    contentType = "IMAGE/AVIF",
                    applicationContext = mockContext,
                    mediaQuality = CompressorQuality.MEDIUM,
                )

            assertEquals(mockUri, result.uri)
            coVerify(exactly = 0) { Compressor.compress(any(), any(), any(), any()) }
        }
```

- [ ] **Step 2: Run test, verify failure**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.ui.components.MediaCompressorTest"`
Expected: 2 NEW tests FAIL with `Compressor.compress was called 1 time but expected exactly 0`. (Existing tests still pass.)

- [ ] **Step 3: Patch `MediaCompressor.kt`**

In `MediaCompressor.kt`, locate the image branch at lines 79–83:

```kotlin
            contentType?.startsWith("image", ignoreCase = true) == true &&
                !contentType.contains("gif") &&
                !contentType.contains("svg") -> {
                compressImage(uri, contentType, applicationContext, mediaQuality)
            }
```

Add an AVIF exclusion using the helper from Task A1. Add the import at the top of the file:

```kotlin
import com.vitorpamplona.amethyst.service.uploads.isAvif
```

(Note: `isAvif` is in the same package, so the import is technically optional. Include it for explicitness and to mirror the existing import-by-name convention from CLAUDE.md.)

Change the branch to:

```kotlin
            contentType?.startsWith("image", ignoreCase = true) == true &&
                !contentType.contains("gif") &&
                !contentType.contains("svg") &&
                !isAvif(contentType) -> {
                compressImage(uri, contentType, applicationContext, mediaQuality)
            }
```

This causes AVIF to fall through to the `else -> MediaCompressorResult(uri, contentType, null)` branch — the same pass-through used for unknown types today.

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.ui.components.MediaCompressorTest"`
Expected: All tests pass.

- [ ] **Step 5: Run spotless**

Run: `./gradlew :amethyst:spotlessApply`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaCompressor.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/ui/components/MediaCompressorTest.kt
git commit -m "fix(uploads): preserve AVIF bytes through MediaCompressor

AVIF was being routed into the JPEG re-encode branch, which destroyed
animated AVIFs (only the first frame survived) and stripped the AVIF
format. Add AVIF to the image-compression exclusion list, alongside
GIF and SVG, so AVIF falls through to the no-op pass-through branch.

Fixes #837 (upload-pipeline portion 1/4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B2: Fail-closed AVIF metadata inspection in `MetadataStripper`

**Background:** The existing `stripImageMetadata` copies the file, rewrites EXIF with `ExifInterface`, and returns `StrippingResult(uri, true)` on success or `StrippingResult(uri, false)` on any exception. For AVIF that's a privacy hole — `ExifInterface` cannot reliably rewrite AVIF containers, so a failure path silently passes the original AVIF (potentially with GPS EXIF) to upload. Fix: inspect-only path for AVIF that throws a new `AvifMetadataNotVerifiableException` when sensitive tags are present or inspection fails. The throw must escape the function's existing try/catch — we do this by placing the AVIF branch as an early return at the very top of `stripImageMetadata`, before the try block.

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt`
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripperTest.kt`

- [ ] **Step 1: Write the failing test**

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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class MetadataStripperTest {
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        uri = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `AVIF with no sensitive tags returns original uri marked stripped`() {
        // Arrange: AVIF content; ExifInterface reads no sensitive tags.
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        mockkConstructor(ExifInterface::class)
        every { anyConstructed<ExifInterface>().getAttribute(any()) } returns null

        // Act
        val result = MetadataStripper.stripImageMetadata(uri, context)

        // Assert: original URI returned, marked stripped (i.e. "verified clean").
        assertEquals(uri, result.uri)
        assertTrue(result.stripped)
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF with GPS EXIF throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; ExifInterface reports GPS_LATITUDE present.
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        mockkConstructor(ExifInterface::class)
        every { anyConstructed<ExifInterface>().getAttribute(any()) } returns null
        every { anyConstructed<ExifInterface>().getAttribute(ExifInterface.TAG_GPS_LATITUDE) } returns "40.7128"

        // Act: should throw before returning a StrippingResult
        MetadataStripper.stripImageMetadata(uri, context)

        // Assert: handled by `expected` annotation; reaching here is a failure
        fail("Expected AvifMetadataNotVerifiableException, but no exception was thrown")
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF where ExifInterface fails throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; ExifInterface throws on construction.
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        mockkConstructor(ExifInterface::class)
        every { anyConstructed<ExifInterface>().getAttribute(any()) } throws RuntimeException("malformed AVIF")

        MetadataStripper.stripImageMetadata(uri, context)
        fail("Expected AvifMetadataNotVerifiableException")
    }
}
```

- [ ] **Step 2: Run test, verify failure**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.MetadataStripperTest"`
Expected: 3 tests FAIL — first with `AvifMetadataNotVerifiableException` cannot be resolved (unresolved reference), second and third the same. Compilation will fail because the exception class doesn't exist yet.

- [ ] **Step 3: Add the exception class and AVIF early-return in `MetadataStripper.kt`**

At the top of `MetadataStripper.kt`, just after the `data class StrippingResult` declaration (around line 41), add:

```kotlin
/**
 * Raised when an AVIF file's EXIF metadata could not be confirmed safe.
 *
 * AVIF uses an HEIF/ISOBMFF container that `ExifInterface` cannot reliably rewrite,
 * so we choose between (a) verifying the file is already clean (no sensitive tags)
 * and passing it through unchanged, or (b) failing the upload. This exception
 * signals case (b) and must bubble up past the generic image-stripping try/catch
 * — callers should surface it to the user as an upload error.
 */
class AvifMetadataNotVerifiableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

Then, in `stripImageMetadata` (line 181), replace the existing implementation:

```kotlin
fun stripImageMetadata(
    uri: Uri,
    context: Context,
): StrippingResult {
    val mimeType = context.contentResolver.getType(uri) ?: ""

    // AVIF takes a different path: ExifInterface cannot reliably rewrite AVIF
    // containers, so we inspect-only. Clean AVIFs pass through unchanged;
    // AVIFs with sensitive tags or unreadable EXIF throw fail-closed.
    if (isAvif(mimeType)) {
        return inspectAvifMetadata(uri, context)
    }

    var tempFile: File? = null
    return try {
        val extension =
            when {
                mimeType.endsWith("jpeg", ignoreCase = true) ||
                    mimeType.endsWith("jpg", ignoreCase = true) -> ".jpg"

                mimeType.endsWith("png", ignoreCase = true) -> ".png"

                mimeType.endsWith("webp", ignoreCase = true) -> ".webp"

                else -> ".tmp"
            }
        tempFile = File.createTempFile("stripped_", extension, context.cacheDir)

        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: run {
                    if (!tempFile.delete()) {
                        Log.w("MetadataStripper") { "Failed to delete temp file: ${tempFile.absolutePath}" }
                    }
                    return StrippingResult(uri, false)
                }
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val exif = ExifInterface(tempFile.absolutePath)
        for (tag in SENSITIVE_EXIF_TAGS) {
            exif.setAttribute(tag, null)
        }
        exif.saveAttributes()

        Log.d("MetadataStripper", "Stripped EXIF metadata from image")
        StrippingResult(tempFile.toUri(), true)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (tempFile?.delete() == false) {
            Log.w("MetadataStripper") { "Failed to delete temp file: ${tempFile.absolutePath}" }
        }
        Log.d("MetadataStripper") { "Failed to strip image metadata: ${e.message}" }
        StrippingResult(uri, false)
    }
}

private fun inspectAvifMetadata(
    uri: Uri,
    context: Context,
): StrippingResult {
    val stream =
        context.contentResolver.openInputStream(uri)
            ?: throw AvifMetadataNotVerifiableException("Cannot open AVIF input stream to inspect metadata")

    try {
        val exif =
            try {
                stream.use { ExifInterface(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw AvifMetadataNotVerifiableException("Could not parse AVIF EXIF for inspection: ${e.message}", e)
            }

        val present = SENSITIVE_EXIF_TAGS.firstOrNull { tag -> exif.getAttribute(tag) != null }
        if (present != null) {
            throw AvifMetadataNotVerifiableException(
                "AVIF contains sensitive EXIF tag '$present' that cannot be safely stripped; upload refused",
            )
        }

        Log.d("MetadataStripper", "AVIF EXIF inspection: no sensitive tags found, passing through")
        return StrippingResult(uri, true)
    } catch (e: AvifMetadataNotVerifiableException) {
        throw e
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw AvifMetadataNotVerifiableException("Unexpected error inspecting AVIF metadata: ${e.message}", e)
    }
}
```

Note: `stream.use { ExifInterface(it) }` works because `ExifInterface(InputStream)` is the constructor signature. The `use` closes the stream after construction.

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.MetadataStripperTest"`
Expected: All 3 tests pass.

- [ ] **Step 5: Caller audit**

Search for callers of `MetadataStripper.strip(...)` and `MetadataStripper.stripImageMetadata(...)` to verify the `AvifMetadataNotVerifiableException` propagates cleanly to a user-visible error and is not silently swallowed:

Run: `grep -rn "MetadataStripper\." amethyst/src/main/ commons/src/`
Expected output: `UploadOrchestrator.kt` and possibly a few other files. Read the call sites; verify no broad `catch (e: Exception)` that would suppress our exception, OR if such a catch exists, follow the exception's data flow to confirm the user gets an upload-failure message.

If a broad catch suppresses the exception, document the finding in `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md` as a known limitation under §3.3 and add a TODO to the PR description to revisit. **Do not change the broad catch in this task** — that's an orchestrator change with wider blast radius.

- [ ] **Step 6: Run spotless**

Run: `./gradlew :amethyst:spotlessApply`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripperTest.kt
git commit -m "feat(uploads): fail-closed AVIF metadata inspection in MetadataStripper

ExifInterface cannot reliably rewrite AVIF containers (ISOBMFF / HEIF),
so the previous best-effort strip would silently pass GPS-tagged AVIFs
to upload when the rewrite failed. New behavior for image/avif:

  - Open input read-only via ExifInterface(InputStream).
  - If no sensitive tags found: pass original URI through, stripped=true.
  - If sensitive tags found OR inspection fails: throw a new
    AvifMetadataNotVerifiableException that escapes the function's
    existing try/catch and bubbles up to the upload orchestrator.

Fixes #837 (upload-pipeline portion 2/4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B3: AVIF preview-metadata generation via `ImageDecoder`

**Background:** `PreviewMetadataCalculator` uses `BitmapFactory` which has no AVIF support. We add an AVIF branch that uses `android.graphics.ImageDecoder` (API 28+) to decode the first frame (animated AVIFs return frame 0 from `decodeBitmap`). Below API 28 the AVIF branch returns an empty `PreviewHashes` (no crash). Below API 31 the platform decoder will reject AVIF — caught and treated as a missing-preview case.

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/PreviewMetadataCalculator.kt`
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/PreviewMetadataCalculatorTest.kt`

- [ ] **Step 1: Write the failing test**

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
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMetadataCalculatorTest {
    @Test
    fun `shouldAttempt accepts AVIF`() {
        assertTrue(PreviewMetadataCalculator.shouldAttempt("image/avif"))
    }

    @Test
    fun `shouldAttempt accepts AVIF case-insensitive`() {
        assertTrue(PreviewMetadataCalculator.shouldAttempt("IMAGE/AVIF"))
    }

    @Test
    fun `computeFromBytes for AVIF with empty bytes returns empty PreviewHashes`() {
        // Below API 28 (Robolectric default), or with an empty payload, the AVIF
        // branch must produce a PreviewHashes with no decoded data rather than
        // crashing. The dimPrecomputed value is preserved when available.
        val result =
            PreviewMetadataCalculator.computeFromBytes(
                data = ByteArray(0),
                mimeType = "image/avif",
                dimPrecomputed = null,
            )
        assertEquals(null, result.blurhash)
        assertEquals(null, result.thumbhash)
        assertEquals(null, result.dim)
    }

    @Test
    fun `computeFromBytes for AVIF with malformed bytes does not crash`() {
        // A few random bytes are not a valid AVIF; the decoder should fail
        // gracefully and return an empty PreviewHashes (no exception escapes).
        val result =
            PreviewMetadataCalculator.computeFromBytes(
                data = byteArrayOf(0x00, 0x01, 0x02, 0x03),
                mimeType = "image/avif",
                dimPrecomputed = null,
            )
        assertEquals(null, result.blurhash)
        assertEquals(null, result.thumbhash)
    }
}
```

- [ ] **Step 2: Run test, verify behavior**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculatorTest"`
Expected: `shouldAttempt accepts AVIF` passes (existing `isImage` matches `image/avif`). The two `computeFromBytes` tests may pass already because `BitmapFactory.decodeByteArray` returns null on AVIF and `processImage(null, ...)` returns `PreviewHashes.EMPTY`. The proof that we're using `ImageDecoder` (not `BitmapFactory`) lives in the **instrumented** test (Task D3); these JVM tests pin the no-crash behavior and the AVIF routing.

- [ ] **Step 3: Patch `PreviewMetadataCalculator.kt`**

Add to the imports at the top:

```kotlin
import android.graphics.ImageDecoder
import android.os.Build
```

Replace `computeFromBytes` (lines 64–88) with:

```kotlin
    fun computeFromBytes(
        data: ByteArray,
        mimeType: String?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes =
        when {
            isAvif(mimeType) -> {
                val bitmap = decodeAvifBytes(data)
                processImage(bitmap, dimPrecomputed)
            }

            isImage(mimeType) -> {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, createBitmapOptions())
                processImage(bitmap, dimPrecomputed)
            }

            isVideo(mimeType) -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ByteArrayMediaDataSource(data))
                    processRetriever(retriever, dimPrecomputed)
                } finally {
                    retriever.release()
                }
            }

            else -> {
                PreviewHashes(dim = dimPrecomputed)
            }
        }
```

Replace the `isImage` branch inside `computeFromUri` (lines 100–105) with two branches:

```kotlin
                isAvif(mimeType) -> {
                    val bitmap = decodeAvifFromUri(context, uri)
                    processImage(bitmap, dimPrecomputed)
                }

                isImage(mimeType) -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream, null, createBitmapOptions())
                        processImage(bitmap, dimPrecomputed)
                    } ?: PreviewHashes(dim = dimPrecomputed)
                }
```

Add two private helper functions at the bottom of the `object PreviewMetadataCalculator { }` block, just before the final closing brace:

```kotlin
    private fun decodeAvifBytes(data: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // ImageDecoder is API 28+; AVIF is API 31+ regardless.
            // Older devices: skip preview metadata for AVIF.
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28")
            return null
        }
        if (data.isEmpty()) return null
        return try {
            val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(data))
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "AVIF decode failed: ${e.message}", e)
            null
        }
    }

    private fun decodeAvifFromUri(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28 (uri path)")
            return null
        }
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "AVIF decode-from-uri failed: ${e.message}", e)
            null
        }
    }
```

Why `ALLOCATOR_SOFTWARE` and `isMutableRequired = false`: we only need to read pixels for blurhash/thumbhash, and a software-allocated immutable bitmap is the cheapest option. Hardware bitmaps reject `getPixels()` which would break blurhash.

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculatorTest"`
Expected: All tests pass.

- [ ] **Step 5: Run spotless**

Run: `./gradlew :amethyst:spotlessApply`

- [ ] **Step 6: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/PreviewMetadataCalculator.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/PreviewMetadataCalculatorTest.kt
git commit -m "feat(uploads): decode AVIF previews with ImageDecoder for blurhash/thumbhash

BitmapFactory has no AVIF support on any Android version; AVIFs were
uploaded with empty blurhash/thumbhash/dim, leaving receivers with no
placeholder until the full image downloaded. Add an AVIF branch that
uses android.graphics.ImageDecoder (API 28+) to decode the first frame
into a software-allocated immutable bitmap, then reuse the existing
processImage path so the invariant 'decoded once, both hashes from the
same pixels' still holds.

Below API 28 the AVIF branch returns null and the upload proceeds
without a preview (no crash). Below API 31 the platform decoder will
reject AVIF anyway — caught and treated as missing-preview.

Fixes #837 (upload-pipeline portion 3/4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B4: AVIF filename fallback in `Nip96Uploader`

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/nip96/Nip96Uploader.kt`
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/nip96/Nip96UploaderExtensionTest.kt`

- [ ] **Step 1: Write the failing test**

The existing `fallbackExtensionForMimeType` in `Nip96Uploader` is `private`. Bump it to `internal` for testability (acceptable in Kotlin — tests share the package via the same `internal` module).

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
package com.vitorpamplona.amethyst.service.uploads.nip96

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip96UploaderExtensionTest {
    private val uploader = Nip96Uploader()

    @Test
    fun `fallback returns avif for image avif`() {
        assertEquals("avif", uploader.fallbackExtensionForMimeType("image/avif"))
    }

    @Test
    fun `fallback returns avif for image avif case insensitive`() {
        assertEquals("avif", uploader.fallbackExtensionForMimeType("IMAGE/AVIF"))
    }

    @Test
    fun `fallback returns existing HLS mappings`() {
        assertEquals("m3u8", uploader.fallbackExtensionForMimeType("application/vnd.apple.mpegurl"))
        assertEquals("ts", uploader.fallbackExtensionForMimeType("video/mp2t"))
        assertEquals("mp4", uploader.fallbackExtensionForMimeType("video/mp4"))
    }

    @Test
    fun `fallback returns null for unknown types`() {
        assertNull(uploader.fallbackExtensionForMimeType("application/x-bogus"))
    }
}
```

- [ ] **Step 2: Run test, verify failure**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.nip96.Nip96UploaderExtensionTest"`
Expected: compilation FAILS because `fallbackExtensionForMimeType` is `private`. Once changed to `internal` (in Step 3) tests will fail on the AVIF cases (returns `null` today).

- [ ] **Step 3: Patch `Nip96Uploader.kt`**

Add at the top of `Nip96Uploader.kt`:

```kotlin
import com.vitorpamplona.amethyst.service.uploads.AVIF_EXTENSION
import com.vitorpamplona.amethyst.service.uploads.AVIF_MIME
```

In `Nip96Uploader.kt`, find the `fallbackExtensionForMimeType` method (lines 241–247). Change visibility from `private` to `internal` and add the AVIF row using the shared constants:

```kotlin
    // Android's MimeTypeMap does not know every MIME we upload (notably HLS playlist types
    // and AVIF on older Android). When it returns null we fall back to a small static table
    // so the multipart filename still carries a real extension — otherwise the server gets
    // "name." and echoes it back, which breaks HLS URL rewriting (and rejects extension-less
    // uploads on some NIP-96 servers).
    internal fun fallbackExtensionForMimeType(mimeType: String): String? =
        when (mimeType.lowercase()) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/x-mpegurl", "audio/mpegurl" -> "m3u8"
            "video/mp2t" -> "ts"
            "video/iso.segment", "video/mp4" -> "mp4"
            AVIF_MIME -> AVIF_EXTENSION
            else -> null
        }
```

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.nip96.Nip96UploaderExtensionTest"`
Expected: All 4 tests pass.

- [ ] **Step 5: Run spotless**

Run: `./gradlew :amethyst:spotlessApply`

- [ ] **Step 6: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/nip96/Nip96Uploader.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/nip96/Nip96UploaderExtensionTest.kt
git commit -m "fix(uploads): AVIF extension fallback for NIP-96 multipart filename

Android's MimeTypeMap doesn't know image/avif on older Android versions,
so the multipart filename was being sent as 'name.' (empty extension).
Some NIP-96 servers reject extension-less uploads. Add image/avif to the
existing fallback table alongside HLS playlist types.

Fixes #837 (upload-pipeline portion 4a/4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B5: AVIF filename fallback in `BlossomUploader`

**Background:** `BlossomUploader` does not have a fallback helper — line 144–145 calls `MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: ""`. We need an equivalent fallback. Reuse the `extensionFromMimeType` helper from `MediaMimeTypes.kt`.

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/blossom/BlossomUploader.kt`
- Create: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/blossom/BlossomUploaderExtensionTest.kt`

- [ ] **Step 1: Write the failing test**

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
package com.vitorpamplona.amethyst.service.uploads.blossom

import com.vitorpamplona.amethyst.service.uploads.extensionFromMimeType
import org.junit.Assert.assertEquals
import org.junit.Test

class BlossomUploaderExtensionTest {
    @Test
    fun `extensionFromMimeType helper returns avif for image avif`() {
        // BlossomUploader will use this helper as a fallback when
        // MimeTypeMap.getExtensionFromMimeType returns null on older Android.
        // This test pins the dependency BlossomUploader relies on.
        assertEquals("avif", extensionFromMimeType("image/avif"))
    }

    @Test
    fun `extensionFromMimeType helper returns null for non-AVIF`() {
        // Other types are handled by MimeTypeMap; the helper is AVIF-only by design.
        assertEquals(null, extensionFromMimeType("image/jpeg"))
        assertEquals(null, extensionFromMimeType("video/mp4"))
    }
}
```

(This is more a pinning test than a TDD step — `BlossomUploader.upload()` itself is hard to unit-test in pure JVM because it depends on `MimeTypeMap.getSingleton()`. The instrumented test in Task D3 will exercise the real path with a real AVIF.)

- [ ] **Step 2: Run test, verify pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploaderExtensionTest"`
Expected: Tests pass (the helper already exists from Task A1).

- [ ] **Step 3: Patch `BlossomUploader.kt`**

Add at the top of `BlossomUploader.kt`:

```kotlin
import com.vitorpamplona.amethyst.service.uploads.extensionFromMimeType
```

In `BlossomUploader.kt`, locate lines 143–145:

```kotlin
        val fileName = baseFileName ?: RandomInstance.randomChars(16)
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""
```

Replace with:

```kotlin
        val fileName = baseFileName ?: RandomInstance.randomChars(16)
        val extension =
            contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: extensionFromMimeType(it)
            } ?: ""
```

Also patch the `delete` method (lines 243–244) symmetrically:

```kotlin
        val extension =
            contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: extensionFromMimeType(it)
            } ?: ""
```

Note: For Blossom, the filename is mainly used in error messages and `httpAuth("Uploading $fileName")`. The `Content-Type` HTTP header carries the real MIME — so this fix is for cosmetic/error-message correctness more than protocol-correctness on Blossom. The fix is still worth doing for symmetry with NIP-96.

- [ ] **Step 4: Build to verify no compile errors**

Run: `./gradlew :amethyst:compilePlayDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run spotless**

Run: `./gradlew :amethyst:spotlessApply`

- [ ] **Step 6: Commit**

```bash
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/blossom/BlossomUploader.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/uploads/blossom/BlossomUploaderExtensionTest.kt
git commit -m "fix(uploads): AVIF extension fallback in BlossomUploader

Symmetric to the NIP-96 uploader fix: when MimeTypeMap doesn't know
image/avif (older Android), fall back to the centralized helper from
MediaMimeTypes so filenames carry the .avif extension. Applies to
both upload and delete paths.

Fixes #837 (upload-pipeline portion 4b/4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase C — Animation lifecycle audit

### Task C1: Audit display + animation-lifecycle code paths for AVIF

**Background:** This task may produce **zero code change** — the goal is to verify whether existing display and pause-on-scroll code is MIME-agnostic (works on `AnimatedImageDrawable` regardless of source format) or hardcodes a MIME list (needs `image/avif` added).

**Files:**
- Read only initially; modify only if the audit finds hardcoded MIME predicates.

- [ ] **Step 1: Audit Coil factory order in `ImageLoaderSetup.kt`**

Run: `grep -n "Factory\|gifFactory\|svgFactory\|VideoFrameDecoder" amethyst/src/main/java/com/vitorpamplona/amethyst/service/images/ImageLoaderSetup.kt`

Verify that `gifFactory` (which is `AnimatedImageDecoder.Factory()` on API ≥ 28) is registered **before** any decoder that could accidentally claim AVIF. From the current source (line 81-83): the order is `gifFactory`, `svgFactory`, `VideoFrameDecoder.Factory()`. This order is correct — `AnimatedImageDecoder` runs first.

**Document the finding in the PR description, do not change code.**

- [ ] **Step 2: Audit pause/resume mechanism**

Run: `grep -rn "AnimatedImageDrawable\|DisposableEffect.*Animated\|\.start()\|\.stop()" amethyst/src/main/java/com/vitorpamplona/amethyst/ui/components/ amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/types/Emoji.kt`

Read each match. For each `if`/`when` that branches on MIME or extension, decide:

| Predicate found | Action |
|---|---|
| Branches on `drawable is AnimatedImageDrawable` (type check) | None — MIME-agnostic, AVIF works. |
| Branches on `mimeType in setOf("image/gif", "image/webp")` | Add `"image/avif"` to the set. |
| Branches on URL ending (`.endsWith(".gif")`) | Add `.endsWith(".avif")` alternative. |

If no pause/resume mechanism is found at all, that's its own finding: animated images do not auto-pause off-screen today, so AVIF will not either. Note this in the PR description as "current behavior preserved" — do not introduce a new pause/resume mechanism in this PR.

- [ ] **Step 3: Audit emoji pack rendering**

Read these files in full and look for any MIME / extension predicates that would gate animation:

- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/types/Emoji.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/creators/emojiSuggestions/ShowEmojiSuggestionList.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/emojipacks/common/EmojiPackCard.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/emojipacks/display/EmojiPackScreen.kt`

For each, identify the composable that renders the emoji image (`AsyncImage`, `MyAsyncImage`, `Image`, etc.) and confirm Coil handles the URL regardless of extension. Most likely zero changes needed — emojis are just `AsyncImage(url)` calls and Coil does header sniff via the registered decoders.

- [ ] **Step 4: Audit ZoomableContentView**

Read `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/components/ZoomableContentView.kt`. If it has any hardcoded MIME branching for animation, patch it. Otherwise document as no-op.

- [ ] **Step 5: Write audit findings to PR description**

In a draft PR description note, summarize:
1. Coil factory order: correct (`AnimatedImageDecoder` before `GifDecoder`).
2. Pause/resume mechanism: `<found at X, MIME-agnostic / hardcoded — describe>`.
3. Emoji rendering paths: `<no hardcoded MIME / list at Y patched>`.
4. ZoomableContentView: `<no hardcoded MIME / list at Z patched>`.

This becomes part of the "verification" section of the PR body.

- [ ] **Step 6: If any patches were made, run spotless and commit**

```bash
./gradlew :amethyst:spotlessApply
git add <patched files>
git commit -m "feat(ui): include AVIF in animation-aware MIME predicates

Audit of WebP / GIF MIME predicates surfaced N places where image/avif
needed to be added so animated AVIFs receive the same treatment as
animated WebP/GIF. No new composables introduced.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

If no patches were needed, skip the commit and proceed.

---

## Phase D — Test fixtures + Android instrumented tests

### Task D1: Install AVIF tooling and generate the test asset corpus

This task produces local files and committed test fixtures.

- [ ] **Step 1: Install tooling**

```bash
brew install libavif ffmpeg exiftool
avifenc --version
avifdec --version
```

Expected: each prints a version string. If `avifenc` is missing, `brew install libavif` provides it.

- [ ] **Step 2: Create the working dir**

```bash
mkdir -p ~/avif-test-assets && cd ~/avif-test-assets
```

- [ ] **Step 3: Generate the tiny still AVIF for unit fixtures (8×8)**

First, generate a tiny PNG, then encode to AVIF:

```bash
ffmpeg -y -f lavfi -i color=red:s=8x8 -frames:v 1 frame-8x8.png
avifenc -q 90 frame-8x8.png still-tiny-8x8.avif
avifdec --info still-tiny-8x8.avif | head -5
```

Expected: AVIF info shows `8x8`, 1 frame.

- [ ] **Step 4: Generate the tiny animated AVIF for unit fixtures (8×8, 3 frames)**

```bash
ffmpeg -y -f lavfi -i color=red:s=8x8 -frames:v 1 r.png
ffmpeg -y -f lavfi -i color=green:s=8x8 -frames:v 1 g.png
ffmpeg -y -f lavfi -i color=blue:s=8x8 -frames:v 1 b.png
avifenc --fps 10 r.png g.png b.png animated-tiny-3frames.avif
avifdec --info animated-tiny-3frames.avif | head -5
```

Expected: AVIF info shows `8x8`, multiple frames (3).

- [ ] **Step 5: Generate the medium-size still AVIF (manual test fixture, not committed)**

```bash
ffmpeg -y -f lavfi -i testsrc=s=800x600 -frames:v 1 testsrc.png
avifenc testsrc.png still-medium.avif
```

- [ ] **Step 6: Generate the medium animated AVIF (manual test fixture, not committed)**

Find any small GIF locally (if none, generate one):

```bash
ffmpeg -y -f lavfi -i testsrc=s=320x240:r=10 -t 2 testsrc.gif
ffmpeg -y -i testsrc.gif -c:v libaom-av1 -crf 30 animated-medium.avif
avifdec --info animated-medium.avif | head -5
```

- [ ] **Step 7: Generate the emote-sized animated AVIF (manual test fixture, not committed)**

```bash
ffmpeg -y -i testsrc.gif -vf scale=64:64 -c:v libaom-av1 emote-64.avif
```

- [ ] **Step 8: Generate hardening fixtures**

```bash
# Synthetic GPS-tagged AVIF (not committed; for manual upload-rejection test)
cp still-medium.avif still-with-gps.avif
exiftool -overwrite_original -GPSLatitude="40.7128" -GPSLongitude="-74.0060" still-with-gps.avif
exiftool still-with-gps.avif | grep -i gps

# Corrupted AVIF (truncate last 1KB)
SZ=$(stat -f%z animated-medium.avif 2>/dev/null || stat -c%s animated-medium.avif)
dd if=animated-medium.avif of=corrupted.avif bs=1 count=$((SZ - 1024))

# Large animated AVIF
ffmpeg -y -f lavfi -i testsrc=s=1920x1080:r=30 -t 5 -c:v libaom-av1 -crf 20 large-animated.avif
ls -lh large-animated.avif
```

Expected: `exiftool` output shows GPS Latitude / GPS Longitude; `large-animated.avif` size > 5 MB (target >20 MB if disk space allows).

- [ ] **Step 9: Validate all assets in the corpus**

```bash
for f in *.avif; do echo "=== $f ==="; avifdec --info "$f" 2>&1 | head -3; done
```

Expected: each file reports dimensions and a non-zero frame count.

- [ ] **Step 10: No commit yet** — these files stay local for the manual test plan and the instrumented test setup.

---

### Task D2: Commit tiny AVIF fixtures to the repo

**Files:**
- Create: `amethyst/src/androidTest/assets/avif/still-tiny-8x8.avif`
- Create: `amethyst/src/androidTest/assets/avif/animated-tiny-3frames.avif`

- [ ] **Step 1: Create the assets directory and copy fixtures**

```bash
mkdir -p /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif
cp ~/avif-test-assets/still-tiny-8x8.avif        /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif/
cp ~/avif-test-assets/animated-tiny-3frames.avif /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif/
ls -la /Users/david/StudioProjects/amethyst/amethyst/src/androidTest/assets/avif/
```

Expected: two files listed, each < 5 KB.

- [ ] **Step 2: Verify Gradle includes the assets directory**

The Android `assets/` folder under `src/<sourceSet>/assets/` is included automatically by AGP. No `build.gradle` change needed. Confirm by:

```bash
grep -n "assets" /Users/david/StudioProjects/amethyst/amethyst/build.gradle.kts
```

Expected: at most the standard AGP behavior (no override). If there's a custom `sourceSets` block that excludes `assets`, add `androidTest.assets.srcDirs("src/androidTest/assets")`.

- [ ] **Step 3: Commit**

```bash
git add amethyst/src/androidTest/assets/avif/
git commit -m "test(uploads): commit tiny AVIF fixtures for instrumented tests

still-tiny-8x8.avif: 8x8 single-frame AVIF for upload-pipeline test.
animated-tiny-3frames.avif: 8x8 3-frame animated AVIF for decode test.

Generated with avifenc (libavif). Total ~few KB; both files < 5 KB.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D3: Write `AvifUploadPipelineInstrumentedTest`

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt`

- [ ] **Step 1: Write the instrumented test**

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
    fun stillAvifPassesThroughMediaCompressorUnchanged() =
        runBlocking {
            val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
            val originalBytes = avif.readBytes()

            val result =
                MediaCompressor().compress(
                    uri = avif.toUri(),
                    contentType = AVIF_MIME,
                    applicationContext = context,
                    mediaQuality = CompressorQuality.MEDIUM,
                )

            assertEquals(AVIF_MIME, result.contentType)
            // The URI should still point to a file with the original bytes (no JPEG re-encode).
            val resultBytes = File(result.uri.path!!).readBytes()
            assertTrue("AVIF bytes must be preserved through compressor", originalBytes.contentEquals(resultBytes))
        }

    @Test
    fun animatedAvifPassesThroughMediaCompressorUnchanged() =
        runBlocking {
            val avif = copyAssetToCache("avif/animated-tiny-3frames.avif")
            val originalBytes = avif.readBytes()

            val result =
                MediaCompressor().compress(
                    uri = avif.toUri(),
                    contentType = AVIF_MIME,
                    applicationContext = context,
                    mediaQuality = CompressorQuality.MEDIUM,
                )

            assertEquals(AVIF_MIME, result.contentType)
            val resultBytes = File(result.uri.path!!).readBytes()
            assertTrue(
                "Animated AVIF bytes must be preserved through compressor (was previously destroyed by JPEG re-encode)",
                originalBytes.contentEquals(resultBytes),
            )
        }

    @Test
    fun avifMetadataStripperReturnsCleanFile() {
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
        // Generated AVIF has no sensitive EXIF tags.
        val result = MetadataStripper.stripImageMetadata(avif.toUri(), context)
        assertTrue("Clean AVIF should be marked stripped", result.stripped)
        assertEquals("Clean AVIF URI should be the original", avif.toUri(), result.uri)
    }

    @Test
    fun avifPreviewMetadataGeneratesBlurhashAndThumbhash() {
        assumeTrue("AVIF decoding requires API 31+", Build.VERSION.SDK_INT >= 31)
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
        val result =
            PreviewMetadataCalculator.computeFromUri(
                context = context,
                uri = avif.toUri(),
                mimeType = AVIF_MIME,
            )
        assertNotNull("PreviewMetadataCalculator must return non-null for AVIF on API 31+", result)
        assertNotNull("AVIF blurhash should be generated via ImageDecoder", result!!.blurhash)
        assertNotNull("AVIF thumbhash should be generated via ImageDecoder", result.thumbhash)
        assertNotNull("AVIF dimensions should be returned", result.dim)
        assertEquals(8, result.dim!!.width)
        assertEquals(8, result.dim.height)
    }
}
```

- [ ] **Step 2: Run on an API 35 emulator**

Start an emulator (API 35 recommended), then:

```bash
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.uploads.AvifUploadPipelineInstrumentedTest'
```

Expected: 4 tests pass.

- [ ] **Step 3: Run on an API 28 emulator (pre-API-31 path)**

Switch to an API 28 emulator. Re-run the same command.

Expected:
- `stillAvifPassesThroughMediaCompressorUnchanged` PASSES (compressor doesn't decode, just routes by MIME).
- `animatedAvifPassesThroughMediaCompressorUnchanged` PASSES (same reason).
- `avifMetadataStripperReturnsCleanFile` may PASS or FAIL depending on whether `ExifInterface` can parse the AVIF container on API 28. If it FAILS, that means the new `AvifMetadataNotVerifiableException` was thrown, which is the intended fail-closed behavior — so add `try { ... } catch (e: AvifMetadataNotVerifiableException) { /* acceptable */ }` around the call in the test, or split the test into an API-guarded version. Decide based on observed behavior on the emulator.
- `avifPreviewMetadataGeneratesBlurhashAndThumbhash` SKIPPED via `assumeTrue`.

- [ ] **Step 4: Commit**

```bash
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/uploads/AvifUploadPipelineInstrumentedTest.kt
git commit -m "test(uploads): instrumented AVIF upload-pipeline coverage

Drives a generated 8x8 still + 3-frame animated AVIF through the full
pipeline on a real Android runtime:

  - MediaCompressor preserves bytes (no JPEG re-encode)
  - MetadataStripper inspects EXIF and returns original URI for clean AVIFs
  - PreviewMetadataCalculator decodes via ImageDecoder and produces
    blurhash/thumbhash/dim on API 31+

Runs on API 35 (full path) and API 28 (pre-API-31 fallback path).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D4: Write `AvifAnimatedDecodeInstrumentedTest` (Coil display path)

**Files:**
- Create: `amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt`

- [ ] **Step 1: Write the instrumented test**

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
        ImageLoader
            .Builder(context)
            .components { add(AnimatedImageDecoder.Factory()) }
            .build()

    @Test
    fun stillAvifDecodesToBitmap() =
        runBlocking {
            assumeTrue("AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
            val avif = copyAsset("avif/still-tiny-8x8.avif")

            val result =
                avifLoader().execute(
                    ImageRequest
                        .Builder(context)
                        .data(avif.toUri())
                        .build(),
                )

            assertTrue("Expected SuccessResult, got ${result::class.simpleName}", result is SuccessResult)
            val bitmap = (result as SuccessResult).image.toBitmap()
            assertEquals(8, bitmap.width)
            assertEquals(8, bitmap.height)
        }

    @Test
    fun animatedAvifDecodesToAnimatedImageDrawable() =
        runBlocking {
            assumeTrue("Animated AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
            val avif = copyAsset("avif/animated-tiny-3frames.avif")

            val result =
                avifLoader().execute(
                    ImageRequest
                        .Builder(context)
                        .data(avif.toUri())
                        .build(),
                )

            assertTrue("Expected SuccessResult", result is SuccessResult)
            val drawable = (result as SuccessResult).image.asDrawable(context.resources)
            assertTrue(
                "Animated AVIF must decode to AnimatedImageDrawable, got ${drawable::class.simpleName}",
                drawable is AnimatedImageDrawable,
            )
        }
}
```

Note: `coil3.toBitmap()` and `coil3.asDrawable()` are convenience extensions. If the Coil 3 version in use has them at different paths, follow IDE auto-complete to the correct import. If they don't exist, replace with manual conversion via `image.asDrawable(context.resources).toBitmap()`.

- [ ] **Step 2: Run on an API 35 emulator**

```bash
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.service.images.AvifAnimatedDecodeInstrumentedTest'
```

Expected: 2 tests pass.

- [ ] **Step 3: Run on an API 28 emulator**

Same command on API 28.

Expected: 2 tests **skipped** (via `assumeTrue`). This confirms the test guards work as intended.

- [ ] **Step 4: Commit**

```bash
git add amethyst/src/androidTest/java/com/vitorpamplona/amethyst/service/images/AvifAnimatedDecodeInstrumentedTest.kt
git commit -m "test(images): instrumented Coil decode tests for AVIF

Verifies that Coil 3's AnimatedImageDecoder.Factory (which wraps the
platform ImageDecoder on API 28+) correctly decodes:

  - still AVIF -> Bitmap with expected dimensions
  - animated AVIF -> AnimatedImageDrawable

Tests are guarded with assumeTrue(SDK_INT >= 31) since the platform
ImageDecoder rejects AVIF below API 31.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase E — Manual on-device verification

### Task E1: Execute the manual test plan

**Files:**
- Reference: `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`

This phase is human-driven. It does not produce code commits but produces verification evidence for the PR.

- [ ] **Step 1: Build and install on the primary device (API ≥ 31)**

```bash
adb devices  # confirm device connected
./gradlew :amethyst:installPlayDebug
adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
```

Expected: app launches without crash.

- [ ] **Step 2: Sign in as the Amethyst tester account**

Hex: `f512822a89d2369a386bfeb1e687ccd26ceb6bb33e73b98417499bb9054bff1f`.

Configure media servers in settings → Media:
- Blossom: `blossom.primal.net` (or chosen alternative)
- NIP-96: `nostr.build` (or chosen alternative)

- [ ] **Step 3: Walk through §2.1–§2.6 of the manual test plan**

For each numbered row in `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`:
1. Execute the action.
2. Observe the result.
3. Tick the checkbox (mark the row's `☐` → `☑` in your local copy of the doc).
4. If a row fails, file it as a discovered issue and decide whether to fix it under this PR or defer.

Focus order:
1. Row 4 (`adb logcat | grep MediaCompressor` confirms no "Using image compression" log when uploading AVIF).
2. Row 6 (`curl -O <blossom URL>` then `cmp local downloaded` — bytes identical).
3. Row 8 (`nak req -a $TESTER_HEX -k 1 --limit 5 wss://relay.damus.io | jq '.tags[]'` — confirms `imeta` carries `m image/avif`, plus `dim` and `blurhash`).
4. Rows 24–31 (the NIP-30 emoji-pack flow — the headline use case).

- [ ] **Step 4: Walk through hardening rows §3.1–§3.5**

Specifically verify:
- Row 41: uploading `still-with-gps.avif` is **refused** with a user-visible error (verifies the `AvifMetadataNotVerifiableException` reaches the UI).
- Row 45: with multiple animated AVIFs in feed, off-screen items pause (this depends on Task C1's finding — if no pause/resume mechanism exists, document the actual behavior).

- [ ] **Step 5: Switch to API 28–30 device, run §3.4**

On the secondary device:
- View AVIF posts from the tester account → expect Coil error slot.
- View tester profile (with AVIF avatar set in §2.3) → expect robohash fallback or broken-image icon.
- No crashes anywhere.

- [ ] **Step 6: Update the manual test plan locally**

After the run, the local copy of `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md` has all boxes ticked or annotated. Snapshot the final state into the PR description.

---

### Task E2: Record the emoji-pack proof artifact

- [ ] **Step 1: Set up screen recording**

```bash
adb shell screenrecord --time-limit=30 /sdcard/avif-emoji-pack-proof.mp4
```

(Or use the system screen recorder; whichever is more convenient on the primary device.)

- [ ] **Step 2: Walk through the recording sequence**

While recording:
1. Open the new emoji pack screen (`EmojiPackScreen`) → emote animates.
2. Show the emoji pack card in the packs list → thumbnail animates.
3. Compose a note, type `:emote_name:` → suggestion popup shows animated preview.
4. Send the note with the custom emoji inline → emoji animates in the note body.
5. React to a different note with the custom emoji → animated reaction appears.

- [ ] **Step 3: Pull the recording**

```bash
adb pull /sdcard/avif-emoji-pack-proof.mp4 ~/Desktop/
adb shell rm /sdcard/avif-emoji-pack-proof.mp4
ls -lh ~/Desktop/avif-emoji-pack-proof.mp4
```

Expected: file present, < 30 MB.

- [ ] **Step 4: No commit** — the recording will be attached to the PR description, not committed to the repo.

---

## Phase F — Ship

### Task F1: Final format, full test suite, push, open PR

- [ ] **Step 1: Run spotless once more on all changes**

```bash
cd /Users/david/StudioProjects/amethyst
./gradlew spotlessApply
git status
```

Expected: clean tree or only intentional formatting tweaks. If changes appear, amend or commit:

```bash
git add -u
git commit -m "style: spotlessApply" || true
```

- [ ] **Step 2: Run the pre-push hook test scope**

```bash
./gradlew :quartz:jvmTest :commons:jvmTest :nestsClient:jvmTest :quic:jvmTest \
          :amethyst:testPlayDebugUnitTest :cli:test :desktopApp:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Push the branch**

```bash
git push -u origin feat/avif-support
```

- [ ] **Step 4: Open the PR**

Use `gh pr create` (skill: see CLAUDE.md for the canonical heredoc form). PR body should include:

- Summary: "Comprehensive AVIF support — upload pipeline + display verification across feeds, profiles, DMs, emoji packs, reactions, gallery, and caching."
- The audit findings from Task C1 (Coil factory order, animation-lifecycle predicates, emoji rendering paths).
- The ticked verification matrix from Task E1.
- The screen recording from Task E2 as an attached MP4.
- Known limitations from §11 of the spec (API < 31 fallback, Desktop JVM, NIP-96 transcoding caveats, `image/avif-sequence` non-support).
- Links to the spec (`amethyst/plans/2026-05-26-avif-support.md`) and this implementation plan (`amethyst/plans/2026-05-26-avif-implementation-plan.md`).
- `Closes #837`.

- [ ] **Step 5: Attach the screen recording**

After PR creation, use the GitHub UI to drag-drop the recording from Task E2 into the description.

---

## Done.

When all phases A–F are complete and the PR is open with the verification matrix + recording, the work is shippable.

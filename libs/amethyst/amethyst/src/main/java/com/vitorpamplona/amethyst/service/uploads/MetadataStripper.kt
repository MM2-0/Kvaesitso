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

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.davotoula.lightcompressor.sanitizer.Mp4Sanitizer
import com.vitorpamplona.amethyst.commons.service.uploads.isAvif
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

data class StrippingResult(
    val uri: Uri,
    val stripped: Boolean,
)

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

/**
 * Reader of EXIF tags from an AVIF input stream. Tests substitute a fake to avoid
 * mockkConstructor(ExifInterface) which fails under JDK 23 (native methods cannot
 * be retransformed).
 */
fun interface AvifExifReader {
    /**
     * Consumes [stream] (the caller wraps it in `.use { }`) and returns a function
     * that maps an EXIF tag name to its string value (or null if absent).
     */
    fun open(stream: InputStream): (String) -> String?
}

/**
 * Removes location and device metadata from media before upload.
 *
 * - Images: EXIF tags in [SENSITIVE_EXIF_TAGS] are cleared on a copy. AVIF is inspected only, and
 *   refused ([AvifMetadataNotVerifiableException]) when it carries one of them.
 * - Video and MP4 audio (`.m4a`, AAC): a container rewrite by LightCompressor-enhanced's
 *   [Mp4Sanitizer]. Only the audio and video tracks are copied, sample by sample, into a fresh
 *   MPEG-4 file: `udta`, `meta`/`ilst` (location, `com.android.*` / `com.apple.*` tags) and `uuid`
 *   (XMP) boxes do not carry over, timed-metadata and text tracks (a Pixel's `application/meta`,
 *   an iPhone's `mebx`) are dropped, the rotation hint is reapplied and the result is fast-start
 *   (`moov` before `mdat`). Nothing is decoded or re-encoded. Residuals: `MediaMuxer` writes its
 *   own creation time and a `com.android.version` meta key, and metadata inside the bitstream
 *   (H.264 SEI user data) stays. The strip fails, rather than passing the file through, when an
 *   audio or video track cannot go into MP4 (Opus, MP3, Dolby Vision), when a kept track has no
 *   samples, and for a stream-copy trim whose edit list starts mid-GOP (a gallery-editor cut):
 *   remuxing that would bring back the frames the trim removed.
 * - MP3: ID3v2 and ID3v1 tags are cut off the byte stream.
 *
 * Only uploads with stripping on reach this object: an UNCOMPRESSED upload without it goes up
 * byte for byte. A `false` [StrippingResult.stripped] means the caller must ask the user before
 * uploading the original.
 */
object MetadataStripper {
    private const val TAG = "MetadataStripper"
    private const val TEMP_FILE_LABEL = "temp file"

    private val SENSITIVE_EXIF_TAGS =
        arrayOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_USER_COMMENT,
        )

    fun stripImageMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult =
        stripImageMetadata(uri, context) { stream ->
            val exif = ExifInterface(stream)
            exif::getAttribute
        }

    /**
     * Internal overload used by production code (via the public single-arity wrapper) and by
     * unit tests to substitute a fake [openAvifExif] without needing bytecode instrumentation
     * of [ExifInterface].
     *
     * [openAvifExif] receives an already-opened input stream and must return a function that
     * maps an EXIF tag name to its string value (or null if absent). The stream is consumed
     * inside the factory call. For non-AVIF images this factory is never invoked.
     */
    internal fun stripImageMetadata(
        uri: Uri,
        context: Context,
        openAvifExif: AvifExifReader,
    ): StrippingResult {
        val mimeType = context.contentResolver.getType(uri) ?: ""

        // AVIF takes a different path: ExifInterface cannot reliably rewrite AVIF
        // containers, so we inspect-only. Clean AVIFs pass through unchanged;
        // AVIFs with sensitive tags or unreadable EXIF throw fail-closed.
        if (isAvif(mimeType)) {
            return inspectAvifMetadata(uri, context, openAvifExif)
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
                        tempFile.deleteOrWarn(TAG, TEMP_FILE_LABEL)
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

            Log.d(TAG, "Stripped EXIF metadata from image")
            StrippingResult(tempFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            tempFile?.deleteOrWarn(TAG, TEMP_FILE_LABEL)
            Log.d(TAG) { "Failed to strip image metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    private fun inspectAvifMetadata(
        uri: Uri,
        context: Context,
        openAvifExif: AvifExifReader,
    ): StrippingResult {
        val stream =
            context.contentResolver.openInputStream(uri)
                ?: throw AvifMetadataNotVerifiableException("Cannot open AVIF input stream to inspect metadata")

        val getAttribute =
            try {
                stream.use { openAvifExif.open(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw AvifMetadataNotVerifiableException("Could not parse AVIF EXIF for inspection: ${e.message}", e)
            }

        val present = SENSITIVE_EXIF_TAGS.firstOrNull { tag -> getAttribute(tag) != null }
        if (present != null) {
            throw AvifMetadataNotVerifiableException(
                "AVIF contains sensitive EXIF tag '$present' that cannot be safely stripped; upload refused",
            )
        }

        Log.d(TAG, "AVIF EXIF inspection: no sensitive tags found, passing through")
        return StrippingResult(uri, true)
    }

    suspend fun stripVideoMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult = sanitizeMp4(uri, context, kind = "video", suffix = ".mp4")

    suspend fun stripAudioMetadata(
        uri: Uri,
        context: Context,
    ): StrippingResult = sanitizeMp4(uri, context, kind = "audio", suffix = ".m4a")

    /**
     * Rewrites the MP4 at [uri] into a cache file through [Mp4Sanitizer]. A remux whose fast-start
     * pass failed is still stripped, so it is returned with a warning; any failure returns [uri]
     * unstripped. Cancelling the caller interrupts the remux, which the library then abandons.
     */
    private suspend fun sanitizeMp4(
        uri: Uri,
        context: Context,
        kind: String,
        suffix: String,
    ): StrippingResult {
        var tempFile: File? = null
        return try {
            val output = File.createTempFile("stripped_${kind}_", suffix, context.cacheDir)
            tempFile = output
            val result = runInterruptible(Dispatchers.IO) { Mp4Sanitizer.sanitize(context, uri, output) }
            if (!result.fastStart) Log.w(TAG) { "Sanitized $kind is not fast-start: ${output.name}" }
            Log.d(TAG) { "Stripped metadata from $kind" }
            StrippingResult(output.toUri(), true)
        } catch (e: Exception) {
            tempFile?.deleteOrWarn(TAG, TEMP_FILE_LABEL)
            if (e is CancellationException) throw e
            // An interrupted remux surfaces as InterruptedIOException, which runInterruptible does
            // not turn into a cancellation: a cancelled upload stops here instead of asking the user.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Failed to strip $kind metadata", e)
            StrippingResult(uri, false)
        }
    }

    fun stripMp3Metadata(
        uri: Uri,
        context: Context,
    ): StrippingResult {
        var tempInputFile: File? = null
        return try {
            tempInputFile = File.createTempFile("mp3_input_", ".mp3", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempInputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                tempInputFile.deleteOrWarn(TAG, TEMP_FILE_LABEL)
                return StrippingResult(uri, false)
            }

            val fileSize = tempInputFile.length()
            var startOffset = 0L
            var endOffset = fileSize

            // Read first 10 bytes to check for ID3v2 header
            val header = ByteArray(10)
            tempInputFile.inputStream().use { it.read(header) }

            if (fileSize >= 10 &&
                header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) {
                val size =
                    (header[6].toInt() and 0x7F shl 21) or
                        (header[7].toInt() and 0x7F shl 14) or
                        (header[8].toInt() and 0x7F shl 7) or
                        (header[9].toInt() and 0x7F)
                startOffset = 10L + size
            }

            // Read last 128 bytes to check for ID3v1 tag
            if (endOffset - startOffset >= 128) {
                val tail = ByteArray(128)
                RandomAccessFile(tempInputFile, "r").use { raf ->
                    raf.seek(endOffset - 128)
                    raf.readFully(tail)
                }
                if (tail[0] == 'T'.code.toByte() &&
                    tail[1] == 'A'.code.toByte() &&
                    tail[2] == 'G'.code.toByte()
                ) {
                    endOffset -= 128
                }
            }

            if (startOffset == 0L && endOffset == fileSize) {
                tempInputFile.deleteOrWarn(TAG, TEMP_FILE_LABEL)
                tempInputFile = null
                return StrippingResult(uri, true) // no tags found, already clean
            }

            val tempOutputFile = File.createTempFile("stripped_mp3_", ".mp3", context.cacheDir)
            RandomAccessFile(tempInputFile, "r").use { raf ->
                raf.seek(startOffset)
                tempOutputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var remaining = endOffset - startOffset
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            tempInputFile.deleteOrWarn(TAG, TEMP_FILE_LABEL)
            tempInputFile = null

            Log.d(TAG, "Stripped ID3 tags from MP3")
            StrippingResult(tempOutputFile.toUri(), true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            tempInputFile?.deleteOrWarn(TAG, TEMP_FILE_LABEL)
            Log.d(TAG) { "Failed to strip MP3 metadata: ${e.message}" }
            StrippingResult(uri, false)
        }
    }

    suspend fun strip(
        uri: Uri,
        mimeType: String?,
        context: Context,
    ): StrippingResult =
        when {
            mimeType?.startsWith("image/", ignoreCase = true) == true -> stripImageMetadata(uri, context)
            mimeType?.startsWith("video/", ignoreCase = true) == true -> stripVideoMetadata(uri, context)
            mimeType?.equals("audio/mpeg", ignoreCase = true) == true -> stripMp3Metadata(uri, context)
            mimeType?.startsWith("audio/", ignoreCase = true) == true -> stripAudioMetadata(uri, context)
            else -> StrippingResult(uri, false)
        }
}

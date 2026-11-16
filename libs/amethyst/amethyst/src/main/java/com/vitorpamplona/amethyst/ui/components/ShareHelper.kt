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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.richtext.mimeTypeMap
import com.vitorpamplona.amethyst.commons.richtext.normalizeMimeType
import com.vitorpamplona.amethyst.commons.service.image.BlossomFetcher
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object ShareHelper {
    private const val TAG = "ShareHelper"
    private const val DEFAULT_IMAGE_EXTENSION = "jpg"
    private const val DEFAULT_VIDEO_EXTENSION = "mp4"
    private const val SHARED_FILE_PREFIX = "shared_media"
    private const val GENERIC_BINARY_MIME_TYPE = "application/octet-stream"

    data class SharableFile(
        val uri: Uri,
        val extension: String,
        val file: File,
    )

    // Image type magic numbers
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val WEBP_HEADER_START = "RIFF".toByteArray()
    private val WEBP_HEADER_END = "WEBP".toByteArray()
    private val GIF_MAGIC = "GIF8".toByteArray()

    // Video type magic numbers
    // Note: WebM and MKV share the same EBML header (Matroska container)
    private val WEBM_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val AVI_HEADER_START = "RIFF".toByteArray()
    private val AVI_HEADER_END = "AVI ".toByteArray()
    private val MOV_FTYP = "ftyp".toByteArray()
    private val MOV_MOOV = "moov".toByteArray()
    private val MOV_MDAT = "mdat".toByteArray()
    private val MOV_FREE = "free".toByteArray()
    private val MP4_BRAND_ISOM = "isom".toByteArray()
    private val MP4_BRAND_MP41 = "mp41".toByteArray()
    private val MP4_BRAND_MP42 = "mp42".toByteArray()
    private val MOV_BRAND_QT = "qt  ".toByteArray()

    /**
     * Picks the MIME type to put on an `ACTION_SEND` intent.
     *
     * `Intent.type` has to be a real `type/subtype` — an `IntentFilter` matches the two halves
     * separately, so a slash-less value matches nothing and the chooser opens empty. The declared
     * type is author-supplied and may be unusable (see [normalizeMimeType]), so it is treated as a
     * hint; [fileExtension] is the safer signal because [getMediaExtension] sniffs it from the
     * file's magic numbers rather than trusting the event.
     */
    internal fun resolveShareMimeType(
        declaredMimeType: String?,
        fileExtension: String,
    ): String =
        normalizeMimeType(declaredMimeType)
            ?: mimeTypeMap[fileExtension.lowercase()]
            // Unreachable today: getMediaExtension only ever returns keys of mimeTypeMap. Kept so
            // the return type stays a well-formed MIME if that ever stops holding.
            ?: GENERIC_BINARY_MIME_TYPE

    suspend fun getSharableUriFromUrl(
        context: Context,
        imageUrl: String,
    ): Pair<Uri, String> =
        withContext(Dispatchers.IO) {
            // Safely get snapshot and file
            Amethyst.instance.diskCache.openSnapshot(BlossomFetcher.diskCacheKey(imageUrl))?.use { snapshot ->
                val file = snapshot.data.toFile()

                // Determine file extension and prepare sharable file
                val fileExtension = getImageExtension(file)
                val fileCopy = prepareSharableFile(context, file, fileExtension)

                // Return sharable uri
                return@use Pair(
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", fileCopy),
                    fileExtension,
                )
            } ?: throw IOException("Unable to open snapshot for: $imageUrl")
        }

    @VisibleForTesting
    internal fun getImageExtension(file: File): String = getMediaExtension(file, isVideo = false)

    @VisibleForTesting
    internal fun getVideoExtension(file: File): String = getMediaExtension(file, isVideo = true)

    @VisibleForTesting
    internal fun getMediaExtension(
        file: File,
        isVideo: Boolean,
    ): String {
        val defaultExtension = if (isVideo) DEFAULT_VIDEO_EXTENSION else DEFAULT_IMAGE_EXTENSION
        return try {
            FileInputStream(file).use { inputStream ->
                val header = ByteArray(16)
                val bytesRead = inputStream.read(header)

                if (bytesRead < 4) {
                    return defaultExtension
                }

                if (isVideo) {
                    when {
                        // Video formats
                        // WebM/MKV: Check first 4 bytes (EBML header)
                        // Both use Matroska container; default to webm as it's more common on web
                        matchesMagicNumbers(header, 0, WEBM_MAGIC) -> "webm"

                        // AVI: Check "RIFF" (bytes 0-3) and "AVI " (bytes 8-11)
                        matchesMagicNumbers(header, 0, AVI_HEADER_START) &&
                            bytesRead >= 12 &&
                            matchesMagicNumbers(header, 8, AVI_HEADER_END) -> "avi"

                        // MP4/MOV: Check for ftyp box (bytes 4-7 should be "ftyp")
                        bytesRead >= 12 && matchesMagicNumbers(header, 4, MOV_FTYP) -> detectMp4OrMov(header)

                        // MP4/MOV alternative: moov, mdat, or free at offset 4
                        bytesRead >= 8 &&
                            (
                                matchesMagicNumbers(header, 4, MOV_MOOV) ||
                                    matchesMagicNumbers(header, 4, MOV_MDAT) ||
                                    matchesMagicNumbers(header, 4, MOV_FREE)
                            ) -> "mp4"

                        else -> defaultExtension
                    }
                } else {
                    when {
                        // Image formats
                        // JPEG: Check first 2 bytes
                        matchesMagicNumbers(header, 0, JPEG_MAGIC) -> "jpg"

                        // PNG: Check first 4 bytes
                        matchesMagicNumbers(header, 0, PNG_MAGIC) -> "png"

                        // GIF: Check first 4 bytes for "GIF8"
                        matchesMagicNumbers(header, 0, GIF_MAGIC) -> "gif"

                        // WEBP: Check "RIFF" (bytes 0-3) and "WEBP" (bytes 8-11)
                        matchesMagicNumbers(header, 0, WEBP_HEADER_START) &&
                            bytesRead >= 12 &&
                            matchesMagicNumbers(header, 8, WEBP_HEADER_END) -> "webp"

                        else -> defaultExtension
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not determine media type for ${file.name}, defaulting to $defaultExtension", e)
            defaultExtension
        }
    }

    private fun detectMp4OrMov(header: ByteArray): String {
        // Check brand at bytes 8-11 to differentiate MP4 from MOV
        return when {
            matchesMagicNumbers(header, 8, MP4_BRAND_ISOM) -> "mp4"
            matchesMagicNumbers(header, 8, MP4_BRAND_MP41) -> "mp4"
            matchesMagicNumbers(header, 8, MP4_BRAND_MP42) -> "mp4"
            matchesMagicNumbers(header, 8, MOV_BRAND_QT) -> "mov"
            else -> "mp4" // Default to mp4 for unknown ftyp brands
        }
    }

    private fun matchesMagicNumbers(
        data: ByteArray,
        offset: Int,
        magicBytes: ByteArray,
    ): Boolean {
        if (offset + magicBytes.size > data.size) {
            return false
        }

        for (i in magicBytes.indices) {
            if (data[offset + i] != magicBytes[i]) {
                return false
            }
        }
        return true
    }

    private fun prepareSharableFile(
        context: Context,
        originalFile: File,
        extension: String,
    ): File {
        val timestamp = System.currentTimeMillis()
        val sharableFile = File(context.cacheDir, "${SHARED_FILE_PREFIX}_$timestamp.$extension")

        try {
            originalFile.copyTo(sharableFile, overwrite = true)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy file for sharing", e)
            throw e
        }

        return sharableFile
    }

    suspend fun getSharableUriForLocalVideo(
        context: Context,
        localFile: File,
    ): Pair<Uri, String> =
        withContext(Dispatchers.IO) {
            val fileExtension = getVideoExtension(localFile)
            val sharableFile = prepareSharableFile(context, localFile, fileExtension)
            Pair(
                FileProvider.getUriForFile(context, "${context.packageName}.provider", sharableFile),
                fileExtension,
            )
        }

    fun createTempVideoFile(context: Context): File {
        val timestamp = System.currentTimeMillis()
        return File(context.cacheDir, "${SHARED_FILE_PREFIX}_video_$timestamp.tmp")
    }

    fun prepareTempVideoForSharing(
        context: Context,
        tempFile: File,
    ): SharableFile {
        val extension = getVideoExtension(tempFile)
        val timestamp = System.currentTimeMillis()
        val sharableFile = File(context.cacheDir, "${SHARED_FILE_PREFIX}_$timestamp.$extension")

        try {
            moveTempFileForSharing(tempFile, sharableFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename temp video file for sharing", e)
            throw e
        }

        return SharableFile(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", sharableFile),
            extension = extension,
            file = sharableFile,
        )
    }

    internal fun moveTempFileForSharing(
        tempFile: File,
        sharableFile: File,
        rename: (File, File) -> Boolean = { source, dest -> source.renameTo(dest) },
    ) {
        val renamed = rename(tempFile, sharableFile)
        if (!renamed) {
            tempFile.copyTo(sharableFile, overwrite = true)
            if (!tempFile.delete()) {
                Log.w(TAG) { "Failed to delete temp file ${tempFile.path} after copy" }
            }
        }
    }
}

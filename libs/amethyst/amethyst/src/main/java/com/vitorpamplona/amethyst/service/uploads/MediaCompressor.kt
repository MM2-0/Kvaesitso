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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import com.davotoula.lightcompressor.video.GifToMp4Converter
import com.vitorpamplona.amethyst.commons.cartridges.CartDetector
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.isAvif
import com.vitorpamplona.amethyst.ui.components.util.MediaCompressorFileUtils
import com.vitorpamplona.quartz.utils.Log
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.Constraint
import id.zelory.compressor.determineImageRotation
import id.zelory.compressor.overWrite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.roundToInt

class MediaCompressor {
    // ALL ERRORS ARE IGNORED. The original file is returned.
    suspend fun compress(
        uri: Uri,
        contentType: String?,
        mediaQuality: CompressorQuality,
        applicationContext: Context,
        useH265: Boolean = false,
        convertGifToMp4: Boolean = false,
    ): MediaCompressorResult {
        // Convert GIF to MP4 if requested. The GIF converter already produces a well-compressed
        // H.264 MP4 so no additional video compression step is needed.
        if (convertGifToMp4 && contentType?.contains("gif", ignoreCase = true) == true) {
            Log.d("MediaCompressor") { "Converting GIF to MP4" }
            val converted = GifToMp4Converter.convert(uri, applicationContext)
            if (converted != null) {
                return MediaCompressorResult(converted.file.toUri(), converted.mimeType, converted.size)
            }
            Log.w("MediaCompressor") { "GIF to MP4 conversion failed, uploading as original GIF" }
            return MediaCompressorResult(uri, contentType, null)
        }

        // Skip compression if user selected uncompressed
        if (mediaQuality == CompressorQuality.UNCOMPRESSED) {
            Log.d("MediaCompressor", "UNCOMPRESSED quality selected, skipping compression.")
            return MediaCompressorResult(uri, contentType, null)
        }

        // branch into compression based on content type
        return when {
            contentType?.startsWith("video", ignoreCase = true) == true -> {
                VideoCompressionHelper.compressVideo(uri, contentType, applicationContext, mediaQuality, useH265)
            }

            isReencodableImage(contentType) -> {
                if (isCartridge(uri, contentType, applicationContext)) {
                    // A game cart lives in the low bits of its pixels: any re-encode destroys it.
                    Log.d("MediaCompressor", "Game cartridge detected, uploading it unchanged.")
                    MediaCompressorResult(uri, contentType, null)
                } else {
                    compressImage(uri, contentType, applicationContext, mediaQuality)
                }
            }

            else -> {
                MediaCompressorResult(uri, contentType, null)
            }
        }
    }

    private suspend fun compressImage(
        uri: Uri,
        contentType: String?,
        context: Context,
        mediaQuality: CompressorQuality,
    ): MediaCompressorResult {
        val maxDimension = mediaQuality.imageMaxDimension ?: return MediaCompressorResult(uri, contentType, null)

        var tempFile: File? = null
        return try {
            Log.d("MediaCompressor") { "Using image compression $mediaQuality" }
            tempFile = MediaCompressorFileUtils.from(uri, context)
            // Zelory hands its output back through a cancellable withContext: cancelled during the
            // encode (the composer's preview is, every time the slider moves on), it drops a
            // finished file no one can then delete. So cancellation is checked before the work and
            // the work runs to its end; a caller cancelled after that holds the file to clean up.
            currentCoroutineContext().ensureActive()
            val compressedImageFile =
                Compressor.compress(context, tempFile, Dispatchers.IO + NonCancellable) {
                    constraint(FitWithinConstraint(maxDimension, mediaQuality.imageQuality))
                }
            if (tempFile != compressedImageFile && !tempFile.delete()) {
                Log.w("MediaCompressor") { "Failed to delete temp file: ${tempFile.absolutePath}" }
            }
            Log.d("MediaCompressor") { "Image compression success. New size [${compressedImageFile.length()}]" }
            MediaCompressorResult(compressedImageFile.toUri(), MimeTypes.IMAGE_JPEG, compressedImageFile.length())
        } catch (e: Exception) {
            if (e is CancellationException) {
                // The copy of the original is a full-size file in the cache.
                if (tempFile?.delete() == false) {
                    Log.w("MediaCompressor") { "Failed to delete temp file: ${tempFile.absolutePath}" }
                }
                throw e
            }
            Log.d("MediaCompressor") { "Image compression failed: ${e.message}" }
            if (tempFile?.delete() == false) {
                Log.w("MediaCompressor") { "Failed to delete temp file: ${tempFile.absolutePath}" }
            }
            MediaCompressorResult(uri, contentType, null)
        }
    }

    /** A 160x205 PNG that loads as a game cartridge. Small files only, so this never reads a photo. */
    private fun isCartridge(
        uri: Uri,
        contentType: String?,
        context: Context,
    ): Boolean {
        if (contentType?.contains("png", ignoreCase = true) != true) return false
        return try {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(MAX_CART_BYTES + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read > MAX_CART_BYTES) null else buffer.copyOf(read)
                } ?: return false
            CartDetector.isCartridge(bytes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    companion object {
        private const val MAX_CART_BYTES = 256 * 1024

        /** Still images this compressor re-encodes; animated GIFs, SVGs and AVIFs go up as they are. */
        fun isReencodableImage(contentType: String?): Boolean =
            contentType?.startsWith("image", ignoreCase = true) == true &&
                !contentType.contains("gif") &&
                !contentType.contains("svg") &&
                !isAvif(contentType)
    }
}

/**
 * Decodes at the coarsest power-of-two stride that keeps the longer edge at or above [maxDimension], applies the EXIF
 * rotation, scales so the longer edge is at most [maxDimension] (never up), and writes one JPEG at
 * [quality]. Zelory's `default` constraint stops at the stride, so a 4032 x 3024 photo asked for
 * 640 px came out at 2016 x 1512.
 */
private class FitWithinConstraint(
    private val maxDimension: Int,
    private val quality: Int,
) : Constraint {
    private var done = false

    override fun isSatisfied(imageFile: File): Boolean = done

    override fun satisfy(imageFile: File): File {
        // Zelory's own sampler stops when the shorter edge would drop below the target, so a
        // 4032 x 3024 photo asked for 1920 px decoded at full size; this one goes by the longer edge.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        val longerSource = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longerSource / (sample * 2) >= maxDimension) sample *= 2

        val sampled =
            BitmapFactory.decodeFile(imageFile.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalStateException("Could not decode ${imageFile.name}")
        // Each step frees the one before it: a phone photo is tens of MB per copy.
        val rotated = determineImageRotation(imageFile, sampled)
        if (rotated !== sampled) sampled.recycle()
        val longer = maxOf(rotated.width, rotated.height)
        val fitted =
            if (longer > maxDimension) {
                val ratio = maxDimension.toFloat() / longer
                rotated.scale((rotated.width * ratio).roundToInt().coerceAtLeast(1), (rotated.height * ratio).roundToInt().coerceAtLeast(1))
            } else {
                rotated
            }
        if (fitted !== rotated) rotated.recycle()
        done = true
        return try {
            overWrite(imageFile, fitted, Bitmap.CompressFormat.JPEG, quality)
        } finally {
            fitted.recycle()
        }
    }
}

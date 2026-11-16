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
package com.vitorpamplona.amethyst.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.vitorpamplona.amethyst.commons.service.ai.AltTextSuggester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Alt-text suggestion service backed by Gemini Nano through AICore.
 *
 * Produces a full descriptive sentence via `genai-image-description`, or null when the device
 * cannot run the model.
 */
class MLKitImageLabelService(
    private val context: Context,
) : AltTextSuggester {
    private var describer: ImageDescriber? = null

    // FeatureStatus is an Int enum. Cached per-instance — describer availability does not flip
    // mid-session in practice, and one composer mount only needs to ask AICore once.
    @Volatile private var cachedGenAiStatus: Int? = null

    private fun ensureDescriber(): ImageDescriber? =
        describer
            ?: try {
                ImageDescription
                    .getClient(ImageDescriberOptions.builder(context).build())
                    .also { describer = it }
            } catch (_: Exception) {
                null
            }

    override suspend fun suggestAltText(uri: Uri): String? = describeWithGenAi(uri)

    private suspend fun describeWithGenAi(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val client = ensureDescriber() ?: return@withContext null
            try {
                // awaitDetached rather than ListenableFuture.get(): describing an image takes
                // seconds, and get() would hold an IO thread for all of it — uninterruptibly,
                // so backing out of the composer would leave the thread pinned until AICore
                // answered. Detaching also keeps us off the cancellation path that crashes
                // these clients; see GenAiFutures.
                val status =
                    cachedGenAiStatus ?: client.checkFeatureStatus().awaitDetached().also { cachedGenAiStatus = it }
                if (status != FeatureStatus.AVAILABLE) return@withContext null
                val bitmap = loadDownscaledBitmap(uri) ?: return@withContext null
                val request = ImageDescriptionRequest.builder(bitmap).build()
                client
                    .runInference(request)
                    .awaitDetached()
                    .description
                    .trim()
                    .takeIf { it.isNotEmpty() }
            } catch (e: CancellationException) {
                // Now that the awaits suspend, cancelling the caller lands here: it must not
                // be swallowed as "no suggestion", or the coroutine would keep running.
                throw e
            } catch (_: Exception) {
                null
            }
        }

    // Two-pass decode keeps a 12 MP camera shot from blowing past 40 MB of ARGB_8888 — the
    // on-device describer downscales internally anyway, so a ~1024 px input is plenty.
    private fun loadDownscaledBitmap(uri: Uri): Bitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, TARGET_DIM_PX)
                }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }

    private fun sampleSizeFor(
        width: Int,
        height: Int,
        target: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val maxDim = maxOf(width, height)
        while (maxDim / sample > target) sample *= 2
        return sample
    }

    override fun close() {
        describer?.close()
        describer = null
        cachedGenAiStatus = null
    }

    companion object {
        private const val TARGET_DIM_PX = 1024
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a video view draws while the note is being captured as an image (see
 * [LocalMediaCapture]): a player renders into a surface that a `GraphicsLayer` cannot record,
 * so this shows a still with a play badge on top. [model] is what Coil loads for the still: the
 * poster, or, where Coil can decode video frames (Android), the video itself. Without a model,
 * or when it fails to load, the blurhash stands in, or a black frame.
 */
@Composable
fun VideoStillFrame(
    model: Any?,
    ratio: Float?,
    blurhash: String?,
    thumbhash: String?,
    borderModifier: Modifier,
    contentDescription: String?,
) {
    VideoStillFrameBox(ratio, borderModifier) {
        if (model == null) {
            StillFramePlaceholder(ratio, blurhash, thumbhash)
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                // With a known ratio the box is already sized, so the frame fills and crops it;
                // otherwise the frame sets the height by its own aspect.
                contentScale = if (ratio != null) ContentScale.Crop else ContentScale.FillWidth,
                modifier = if (ratio != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent()
                    }

                    is AsyncImagePainter.State.Error -> {
                        StillFramePlaceholder(ratio, blurhash, thumbhash)
                    }

                    else -> {
                        ReportCaptureLoading()
                        StillFramePlaceholder(ratio, blurhash, thumbhash)
                    }
                }
            }
        }
    }
}

/**
 * [VideoStillFrame] for a platform that extracts the still itself: [loadFrame] returns it, or
 * null when the video has none.
 */
@Composable
fun VideoStillFrame(
    key: Any,
    loadFrame: suspend () -> ImageBitmap?,
    ratio: Float?,
    blurhash: String?,
    thumbhash: String?,
    borderModifier: Modifier,
    contentDescription: String?,
) {
    // Null while loading, then the frame, or Unit when there is none or extracting it failed.
    val frame by produceState<Any?>(null, key) {
        value =
            try {
                loadFrame()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("VideoStillFrame", "Could not extract a frame for the capture", e)
                null
            } ?: Unit
    }

    VideoStillFrameBox(ratio, borderModifier) {
        val bitmap = frame
        if (bitmap is ImageBitmap) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = if (ratio != null) ContentScale.Crop else ContentScale.FillWidth,
                modifier = if (ratio != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
        } else {
            if (bitmap == null) ReportCaptureLoading()
            StillFramePlaceholder(ratio, blurhash, thumbhash)
        }
    }
}

@Composable
private fun VideoStillFrameBox(
    ratio: Float?,
    borderModifier: Modifier,
    frame: @Composable () -> Unit,
) {
    val frameModifier = if (ratio != null) borderModifier.aspectRatio(ratio) else borderModifier.fillMaxWidth()

    Box(frameModifier, contentAlignment = Alignment.Center) {
        frame()

        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
                filled = true,
            )
        }
    }
}

@Composable
private fun StillFramePlaceholder(
    ratio: Float?,
    blurhash: String?,
    thumbhash: String?,
) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(ratio ?: DEFAULT_STILL_FRAME_RATIO)
    Box(modifier.background(Color.Black)) {
        DisplayBlurHash(blurhash, null, ContentScale.Crop, Modifier.fillMaxSize(), thumbhash = thumbhash)
    }
}

private const val DEFAULT_STILL_FRAME_RATIO = 16f / 9f

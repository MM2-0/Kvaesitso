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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberVideoThumbnail(media: SelectedMedia): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, media) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    createVideoThumb(context, media.uri)?.asImageBitmap()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("VideoThumbnail", "Couldn't create thumbnail, but the video can be uploaded", e)
                    null
                }
            }
    }
    return thumb
}

// A preview tile: a frame this size is plenty, where a full 4K frame would be ~33 MB.
private const val THUMB_MAX_SIDE = 1024

/**
 * Creates a bitmap thumbnail, at most [THUMB_MAX_SIDE] on its longer side, from a video uri of the
 * scheme type content://
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
private fun createVideoThumb(
    context: Context,
    uri: Uri,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_MAX_SIDE, THUMB_MAX_SIDE)
    } catch (ex: Exception) {
        Log.w("VideoThumbnail", "Couldn't create thumbnail, but the video can be uploaded", ex)
        null
    } finally {
        retriever.release()
    }
}

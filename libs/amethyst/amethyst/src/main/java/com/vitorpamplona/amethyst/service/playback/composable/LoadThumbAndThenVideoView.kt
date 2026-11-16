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
package com.vitorpamplona.amethyst.service.playback.composable

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.ui.components.ReportCaptureLoading
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun LoadThumbAndThenVideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumbUri: String,
    authorName: String? = null,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    nostrUriCallback: String? = null,
    isLiveStream: Boolean = false,
    accountViewModel: AccountViewModel,
    onDialog: (() -> Unit)? = null,
) {
    var loadingFinished by remember(thumbUri) { mutableStateOf<Pair<Boolean, Drawable?>>(Pair(false, null)) }
    val context = LocalContext.current
    val resources = LocalResources.current

    // Run the Coil fetch in this LaunchedEffect's scope (was previously launched into the
    // AccountViewModel's viewModelScope, which meant a scroll-away wouldn't cancel the in-flight
    // image request — wasted bandwidth, plus the late callback wrote into a state that no
    // longer mattered). Keying on thumbUri also makes the effect re-fire when a recycled slot
    // gets a new audio track with a new cover instead of stalling on the stale Pair(true, ...).
    LaunchedEffect(thumbUri) {
        loadingFinished =
            try {
                val request = ImageRequest.Builder(context).data(thumbUri).build()
                val drawable =
                    withContext(Dispatchers.IO) {
                        context.imageLoader
                            .execute(request)
                            .image
                            ?.asDrawable(resources)
                    }
                Pair(true, drawable)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VideoView", "Fail to load cover $thumbUri", e)
                Pair(true, null)
            }
    }

    if (!loadingFinished.first) {
        ReportCaptureLoading()
    } else {
        VideoView(
            videoUri = videoUri,
            mimeType = mimeType,
            title = title,
            thumb = loadingFinished.second?.let { VideoThumb(it) },
            roundedCorner = roundedCorner,
            contentScale = contentScale,
            artworkUri = thumbUri,
            authorName = authorName,
            nostrUriCallback = nostrUriCallback,
            isLiveStream = isLiveStream,
            accountViewModel = accountViewModel,
            onDialog = onDialog,
        )
    }
}

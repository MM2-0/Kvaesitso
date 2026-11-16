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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_video
import com.vitorpamplona.amethyst.commons.resources.video_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.service.isVideoOnline
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import kotlinx.coroutines.launch

// Account actions that need Android (a content Uri, the media store) or the JVM HTTP stack the
// app's OkHttp clients come from. They stay out of AccountViewModel so the view model needs neither.

suspend fun AccountViewModel.checkVideoIsOnline(videoUrl: String): Boolean = httpClientBuilder.isVideoOnline(videoUrl)

fun AccountViewModel.saveMediaToGallery(
    videoUri: String?,
    mimeType: String?,
    localContext: Context,
) {
    viewModelScope.launch {
        // onSuccess is a plain callback, so the text is resolved here first.
        val savedText = loadStringRes(Res.string.video_saved_to_the_gallery)
        MediaSaverToDisk.saveDownloadingIfNeeded(
            videoUri = videoUri,
            okHttpClient = httpClientBuilder::okHttpClientForVideo,
            mimeType = mimeType,
            localContext = localContext,
            resolveBlossom = {
                Amethyst.instance.blossomResolver
                    .findServers(it)
                    ?.serverUrl
            },
            onSuccess = {
                Handler(Looper.getMainLooper()).post {
                    Toast
                        .makeText(localContext.applicationContext, savedText, Toast.LENGTH_SHORT)
                        .show()
                }
            },
            onError = {
                toastManager.toast(Res.string.failed_to_save_the_video, null, it)
            },
        )
    }
}

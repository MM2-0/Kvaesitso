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

import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.preview.UrlPreview
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

@Stable
object UrlCachedPreviewer {
    var cache = LruCache<String, UrlPreviewState>(100)
        private set

    // Fetches under way: callers asking for the same link at once (a card and the composer's
    // imeta pass, or one link in several notes) share a single request.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<UrlPreviewState>>()

    suspend fun previewInfo(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
        onReady: suspend (UrlPreviewState) -> Unit,
    ) {
        cache[url]?.let {
            onReady(it)
            return
        }

        val mine = CompletableDeferred<UrlPreviewState>()
        val running = inFlight.putIfAbsent(url, mine)
        if (running != null) {
            val state =
                try {
                    running.await()
                } catch (e: CancellationException) {
                    // The caller that started the fetch was cancelled first: fetch it here instead.
                    currentCoroutineContext().ensureActive()
                    return previewInfo(url, okHttpClient, onReady)
                }
            onReady(state)
            return
        }

        try {
            // A fetch may have finished between the cache check and the claim.
            val state = cache[url] ?: load(url, okHttpClient)
            mine.complete(state)
            onReady(state)
        } finally {
            mine.cancel()
            inFlight.remove(url, mine)
        }
    }

    private suspend fun load(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
    ): UrlPreviewState {
        var result: UrlPreviewState = UrlPreviewState.Empty
        UrlPreview().fetch(
            url,
            okHttpClient,
            onComplete = { urlInfo ->
                val cached = cache[url]
                result =
                    if (cached is UrlPreviewState.Loaded || cached is UrlPreviewState.Empty) {
                        cached
                    } else {
                        val state =
                            if (urlInfo.fetchComplete() && urlInfo.url == url) {
                                UrlPreviewState.Loaded(urlInfo)
                            } else {
                                UrlPreviewState.Empty
                            }
                        cache.put(url, state)
                        state
                    }
            },
            onFailed = { throwable ->
                result =
                    cache[url] ?: UrlPreviewState.Error(throwable.message ?: "Error Loading url preview").also { cache.put(url, it) }
            },
        )
        return result
    }
}

/** Loads the OpenGraph preview of [url] through [UrlCachedPreviewer], on the account's preview HTTP client. */
fun AccountViewModel.urlPreview(
    url: String,
    onResult: suspend (UrlPreviewState) -> Unit,
) {
    viewModelScope.launch(Dispatchers.IO) {
        UrlCachedPreviewer.previewInfo(url, httpClientBuilder::okHttpClientForPreview, onResult)
    }
}

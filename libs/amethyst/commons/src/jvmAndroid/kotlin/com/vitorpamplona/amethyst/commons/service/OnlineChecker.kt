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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.utils.Log
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

/** Checks whether a stream URL answers, over OkHttp, recording the result in [OnlineStatusCache]. */
object OnlineChecker {
    val checkOnlineCache get() = OnlineStatusCache.cache

    fun isCachedAndOffline(url: String?): Boolean = OnlineStatusCache.isCachedAndOffline(url)

    fun isOnlineCached(url: String?): Boolean = OnlineStatusCache.isOnlineCached(url)

    fun resetIfOfflineToRetry(url: String) = OnlineStatusCache.resetIfOfflineToRetry(url)

    suspend fun isOnline(
        url: String?,
        okHttpClient: (String) -> OkHttpClient,
    ): Boolean {
        if (url.isNullOrBlank()) return false
        OnlineStatusCache.fresh(url)?.let { return it.online }

        return try {
            val result =
                if (url.startsWith("wss")) {
                    val request =
                        Request
                            .Builder()
                            .url(url.replace("wss+livekit://", "wss://"))
                            .header("Upgrade", "websocket")
                            .header("Connection", "Upgrade")
                            .header("Sec-WebSocket-Key", RandomInstance.bytes(16).toByteString().base64())
                            .header("Sec-WebSocket-Version", "13")
                            .header("Sec-WebSocket-Extensions", "permessage-deflate")
                            .build()

                    val client =
                        okHttpClient(url)
                            .newBuilder()
                            .eventListener(EventListener.NONE)
                            .protocols(listOf(Protocol.HTTP_1_1))
                            .build()

                    client.newCall(request).executeAsync().use { it.isSuccessful }
                } else {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .build()
                    val client = okHttpClient(url)

                    client.newCall(request).executeAsync().use {
                        it.isSuccessful
                    }
                }

            OnlineStatusCache.record(url, result)
            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            OnlineStatusCache.record(url, false)
            Log.e("LiveActivities", "Failed to check streaming url $url", e)
            false
        }
    }
}

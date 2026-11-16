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
package com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/** The OkHttp [Nip11Fetcher]. */
class Nip11Retriever(
    val okHttpClient: (NormalizedRelayUrl) -> OkHttpClient,
) : Nip11Fetcher {
    override suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11ErrorCode, String?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val url = relay.toHttp()
        val request =
            try {
                Request
                    .Builder()
                    .header("Accept", "application/nostr+json")
                    .url(url)
                    .build()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("RelayInfoFail", "Invalid URL ${relay.url}", e)
                onError(relay, Nip11ErrorCode.FAIL_TO_ASSEMBLE_URL, e.message)
                return@withContext
            }

        try {
            val client = okHttpClient(relay)

            client.newCall(request).executeAsync().use { response ->
                val body = response.body.string()
                try {
                    if (response.isSuccessful) {
                        if (body.startsWith("{")) {
                            onInfo(Nip11RelayInformation.fromJson(body))
                        } else {
                            onError(relay, Nip11ErrorCode.FAIL_TO_PARSE_RESULT, body)
                        }
                    } else {
                        onError(relay, Nip11ErrorCode.FAIL_WITH_HTTP_STATUS, response.code.toString())
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(
                        "RelayInfoFail",
                        "Resulting Message from Relay ${relay.url} in not parseable: $body",
                        e,
                    )
                    onError(relay, Nip11ErrorCode.FAIL_TO_PARSE_RESULT, e.message)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayInfoFail", "Failed to fetch NIP-11 from ${relay.url}", e)
            onError(relay, Nip11ErrorCode.FAIL_TO_REACH_SERVER, e.message ?: e::class.simpleName)
        }
    }
}

/** A [Nip11CachedRetriever] over OkHttp, the client picked per relay. */
fun Nip11CachedRetriever(okHttpClient: (NormalizedRelayUrl) -> OkHttpClient): Nip11CachedRetriever = Nip11CachedRetriever(Nip11Retriever(okHttpClient))

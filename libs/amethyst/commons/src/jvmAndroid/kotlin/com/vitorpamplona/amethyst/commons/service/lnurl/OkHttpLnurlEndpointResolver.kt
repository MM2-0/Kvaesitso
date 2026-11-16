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
package com.vitorpamplona.amethyst.commons.service.lnurl

import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import kotlin.coroutines.cancellation.CancellationException

/**
 * OkHttp-backed [LnurlEndpointResolver]. Used by `LocalCache.consume(ZapReceiptEvent)`
 * to look up a recipient's LNURL provider's `nostrPubkey` when validating an
 * incoming zap receipt (NIP-57 Appendix F).
 *
 * Reads from [LnurlEndpointCache] first; on miss, fetches the
 * `/.well-known/lnurlp/<user>` endpoint, parses `nostrPubkey` + `allowsNostr`,
 * caches the result, and returns it. Returns null on HTTP / parse failure;
 * callers should treat that as "validation unavailable" rather than "invalid".
 *
 * Caching and fetch deduplication both belong to [LnurlEndpointCache]; this
 * class is only the HTTP half.
 */
class OkHttpLnurlEndpointResolver(
    private val okHttpClient: (String) -> OkHttpClient,
) : LnurlEndpointResolver {
    override suspend fun resolve(lnurlpUrl: String): LnurlEndpointInfo? = LnurlEndpointCache.getOrFetch(lnurlpUrl, ::fetch)

    private suspend fun fetch(url: String): LnurlEndpointInfo? =
        withContext(Dispatchers.IO) {
            try {
                val client = okHttpClient(url)
                val request = Request.Builder().url(url).build()
                client.newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    val root = Json.parseToJsonElement(body) as? JsonObject ?: return@use null
                    LnurlEndpointInfo(
                        nostrPubkey = root.get("nostrPubkey")?.asText()?.ifBlank { null },
                        allowsNostr = root.get("allowsNostr")?.asBoolean() ?: false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
}

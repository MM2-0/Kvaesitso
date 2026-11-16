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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException

// A Routstr key and a Cashu token are money: they travel the way money calls do, through Tor
// when the user asked for it there, and always through Tor to an onion.
actual fun IRoleBasedHttpClientBuilder.routstrApi(): RoutstrApi = OkHttpRoutstrApi { okHttpClientForMoney(it) }

/** [RoutstrApi] over OkHttp; [clientFor] picks the client for each URL (Tor or not). */
class OkHttpRoutstrApi(
    private val clientFor: (String) -> OkHttpClient,
) : RoutstrApi {
    private val json = "application/json".toMediaType()

    private fun root(base: String) = base.trim().trimEnd('/').removeSuffix("/v1")

    private suspend fun <T> call(
        url: String,
        build: Request.Builder.() -> Request.Builder,
        read: (String) -> T?,
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                try {
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .run(build)
                        .build()
                } catch (e: IllegalArgumentException) {
                    throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE))
                }
            try {
                clientFor(url).newCall(request).executeAsync().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) throw RoutstrException(RoutstrErrors.parse(response.code, body))
                    read(body) ?: throw RoutstrException(RoutstrError(response.code, RoutstrFailure.UNEXPECTED))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The URL names the node only: no key or token is ever in it.
                Log.w(TAG) { "No answer from ${request.url.host}: ${e.javaClass.simpleName}" }
                throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE))
            }
        }

    private fun Request.Builder.bearer(apiKey: String?): Request.Builder = apply { if (!apiKey.isNullOrEmpty()) header("Authorization", "Bearer $apiKey") }

    private fun Request.Builder.postJson(body: String): Request.Builder = post(body.toRequestBody(json))

    override suspend fun info(base: String): RoutstrInfo = call(root(base) + "/v1/info", { get() }, RoutstrJson::info)

    override suspend fun models(
        base: String,
        apiKey: String?,
    ): List<RoutstrModel> = call(root(base) + "/v1/models", { get().bearer(apiKey) }) { body -> RoutstrJson.models(body).takeIf { it.isNotEmpty() || RoutstrJson.parse(body) != null } }

    override suspend fun balance(
        base: String,
        apiKey: String,
    ): RoutstrBalance = call(root(base) + "/v1/balance/info", { get().bearer(apiKey) }, RoutstrJson::balance)

    override suspend fun createInvoice(
        base: String,
        amountSats: Long,
        purpose: RoutstrPurpose,
        apiKey: String?,
    ): RoutstrInvoice {
        val body =
            buildJsonObject {
                put("amount_sats", amountSats)
                put("purpose", purpose.wire)
                // A top-up's key goes in Authorization only: the body's `api_key` is deprecated.
            }.toString()
        return call(root(base) + "/v2/lightning/invoice", { postJson(body).bearer(apiKey.takeIf { purpose == RoutstrPurpose.TOPUP }) }, RoutstrJson::invoice)
    }

    override suspend fun invoiceStatus(
        base: String,
        invoiceId: String,
    ): RoutstrInvoiceStatus {
        // An id is the node's own, but it lands in a path: refuse anything that would change the path.
        if (invoiceId.isEmpty() || invoiceId.any { !(it.isLetterOrDigit() || it == '-' || it == '_') }) {
            throw RoutstrException(RoutstrError(null, RoutstrFailure.NOT_FOUND))
        }
        return call(root(base) + "/v2/lightning/invoice/$invoiceId/status", { get() }, RoutstrJson::invoiceStatus)
    }

    override suspend fun recover(
        base: String,
        bolt11: String,
    ): RoutstrInvoiceStatus {
        val body = buildJsonObject { put("bolt11", bolt11) }.toString()
        return call(root(base) + "/v2/lightning/recover", { postJson(body) }, RoutstrJson::invoiceStatus)
    }

    override suspend fun createWithCashu(
        base: String,
        token: String,
    ): RoutstrCashuResult {
        val body = buildJsonObject { put("initial_balance_token", token) }.toString()
        return call(root(base) + "/v1/balance/create", { postJson(body) }, RoutstrJson::cashu)
    }

    override suspend fun topupWithCashu(
        base: String,
        apiKey: String,
        token: String,
    ): RoutstrCashuResult {
        val body = buildJsonObject { put("cashu_token", token) }.toString()
        return call(root(base) + "/v1/balance/topup", { postJson(body).bearer(apiKey) }, RoutstrJson::cashu)
    }

    companion object {
        private const val TAG = "RoutstrApi"
    }
}

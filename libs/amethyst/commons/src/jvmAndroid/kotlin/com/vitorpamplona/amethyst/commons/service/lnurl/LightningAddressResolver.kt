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

import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Platform-agnostic Lightning Address resolver for LNURL-pay flow.
 * Shared between Android and Desktop for zap functionality.
 *
 * Flow:
 * 1. Lightning address (user@domain) → LNURL endpoint URL
 * 2. Fetch LNURL-pay JSON → extract callback URL
 * 3. Call callback with amount → get BOLT11 invoice
 */
class LightningAddressResolver(
    private val httpClient: OkHttpClient,
) {
    /**
     * Result of resolving a lightning address to a BOLT11 invoice.
     */
    sealed class Result {
        data class Success(
            val invoice: String,
        ) : Result()

        data class Error(
            val message: String,
        ) : Result()
    }

    /**
     * Converts a lightning address to its LNURL-pay endpoint URL.
     * Supports: user@domain, LNURL bech32
     */
    fun assembleUrl(lnAddress: String): String? = LnurlForm.toUrl(lnAddress)

    /**
     * Resolves a lightning address to a BOLT11 invoice.
     *
     * @param lnAddress Lightning address (user@domain) or LNURL
     * @param milliSats Amount in millisatoshis
     * @param message Optional comment for the payment
     * @param zapRequest Optional NIP-57 zap request event
     * @param onProgress Progress callback (0.0 to 1.0)
     */
    suspend fun fetchInvoice(
        lnAddress: String,
        milliSats: Long,
        message: String = "",
        zapRequest: ZapRequestEvent? = null,
        onProgress: (Float) -> Unit = {},
    ): Result =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Resolve LN address to LNURL endpoint
                val url =
                    assembleUrl(lnAddress)
                        ?: return@withContext Result.Error("Invalid lightning address: $lnAddress")

                onProgress(0.2f)

                // Step 2: Fetch LNURL-pay JSON
                val lnurlJson =
                    fetchUrl(url)
                        ?: return@withContext Result.Error("Failed to fetch LNURL endpoint: $url")

                onProgress(0.4f)

                val lnurlp =
                    try {
                        Json.parseToJsonElement(lnurlJson) as? JsonObject
                    } catch (e: Exception) {
                        return@withContext Result.Error("Failed to parse LNURL response")
                    }

                val callbackUrl =
                    lnurlp?.get("callback")?.asText()?.ifBlank { null }
                        ?: return@withContext Result.Error("No callback URL in LNURL response")

                val allowsNostr = lnurlp.get("allowsNostr")?.asBoolean() ?: false
                val nostrPubkey = lnurlp.get("nostrPubkey")?.asText()?.ifBlank { null }

                // Prime the receipt-validation cache so incoming zap receipts can
                // be checked against this provider's nostrPubkey (NIP-57 Appendix F)
                // without re-fetching the lnurlp endpoint.
                LnurlEndpointCache.put(
                    url,
                    LnurlEndpointInfo(nostrPubkey = nostrPubkey, allowsNostr = allowsNostr),
                )

                onProgress(0.5f)

                // Step 3: Fetch invoice from callback
                val invoiceJson =
                    fetchInvoiceFromCallback(
                        callbackUrl = callbackUrl,
                        milliSats = milliSats,
                        message = message,
                        zapRequest = if (allowsNostr) zapRequest else null,
                    ) ?: return@withContext Result.Error("Failed to connect to payment server")

                onProgress(0.7f)

                val invoiceResponse =
                    try {
                        Json.parseToJsonElement(invoiceJson) as? JsonObject
                    } catch (e: Exception) {
                        return@withContext Result.Error("Failed to parse invoice response")
                    }

                val pr = invoiceResponse?.get("pr")?.asText()?.ifBlank { null }

                if (pr == null) {
                    val reason =
                        invoiceResponse?.get("reason")?.asText()?.ifBlank { null }
                            ?: invoiceResponse?.get("message")?.asText()?.ifBlank { null }
                    return@withContext Result.Error(reason ?: "No invoice in response")
                }

                // Step 4: Validate invoice amount
                val expectedAmountInSats =
                    BigDecimal(milliSats)
                        .divide(BigDecimal(1000), RoundingMode.HALF_UP)
                        .toLong()

                val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)

                if (invoiceAmount.toLong() != expectedAmountInSats) {
                    return@withContext Result.Error(
                        "Invoice amount mismatch: got ${invoiceAmount.toLong()} sats, expected $expectedAmountInSats sats",
                    )
                }

                onProgress(1.0f)

                Result.Success(pr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e.message ?: "Unknown error")
            }
        }

    private suspend fun fetchUrl(url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).executeAsync().use { response ->
                    if (response.isSuccessful) {
                        response.body.string()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    private suspend fun fetchInvoiceFromCallback(
        callbackUrl: String,
        milliSats: Long,
        message: String,
        zapRequest: ZapRequestEvent?,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val encodedMessage = URLEncoder.encode(message, "utf-8")
                val urlBinder = if (callbackUrl.contains("?")) "&" else "?"
                var url = "$callbackUrl${urlBinder}amount=$milliSats&comment=$encodedMessage"

                if (zapRequest != null) {
                    val encodedRequest = URLEncoder.encode(zapRequest.toJson(), "utf-8")
                    url += "&nostr=$encodedRequest"
                }

                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).executeAsync().use { response ->
                    // Return body even on error — caller extracts "reason" or "message" from JSON
                    response.body.string()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
}

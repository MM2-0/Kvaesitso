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

import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.LnurlFailureDetail
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.UrlEncoder
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/** LNURL-pay (LUD-06/16) for zaps and payments, over whatever [transport] the platform supplies. */
class LightningInvoiceResolver(
    private val transport: LnurlHttpTransport,
) {
    fun assembleUrl(lnAddress: String): String? = LnurlForm.toUrl(lnAddress)

    /** A failure the user has to be told about, as a typed [notice] rather than text. */
    class LightningAddressError(
        val notice: UserNotice,
    ) : Exception(notice.toString())

    private suspend fun fetchLightningAddressJson(lnAddress: String): String {
        val url =
            assembleUrl(lnAddress) ?: throw LightningAddressError(InvoiceNotice.CannotAssembleLnurl(lnAddress))

        return try {
            val response = transport.get(url)
            if (response.isSuccessful) {
                response.body
            } else {
                throw LightningAddressError(InvoiceNotice.ServiceUnavailable(url, lnAddress, failureDetail(response)))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The refusal above is already the user-facing failure; don't re-wrap it as unreachable.
            if (e is LightningAddressError) throw e
            throw LightningAddressError(
                InvoiceNotice.CannotResolve(
                    url,
                    lnAddress,
                    e.suppressedExceptions.getOrNull(0)?.message ?: e.cause?.message ?: e.message,
                ),
            )
        }
    }

    suspend fun fetchLightningInvoice(
        lnCallback: String,
        milliSats: Long,
        message: String,
        nostrRequest: ZapRequestEvent? = null,
    ): String {
        val encodedMessage = UrlEncoder.encode(message)

        val urlBinder = if (lnCallback.contains("?")) "&" else "?"
        var url = "$lnCallback${urlBinder}amount=$milliSats&comment=$encodedMessage"

        if (nostrRequest != null) {
            val encodedNostrRequest = UrlEncoder.encode(nostrRequest.toJson())
            url += "&nostr=$encodedNostrRequest"
        }

        val response = transport.get(url)
        return if (response.isSuccessful) {
            response.body
        } else {
            throw LightningAddressError(InvoiceNotice.CannotFetch(lnCallback, failureDetail(response)))
        }
    }

    fun failureDetail(response: LnurlHttpResponse): LnurlFailureDetail {
        val body = response.body

        val errorMessage =
            (parseJson(body) as? JsonObject)?.let { tree ->
                val errorNode = tree["error"]
                val messageNode = tree["message"]
                val statusNode = tree["status"]

                if (errorNode != null &&
                    errorNode.isBoolean() &&
                    messageNode != null &&
                    errorNode.asBoolean()
                ) {
                    messageNode.asText()?.let { return LnurlFailureDetail.ServerMessage(it) }
                }

                val status = statusNode?.asText()
                val message = messageNode?.asText()

                if (status == "error" && message != null) {
                    message
                } else {
                    (errorNode as? JsonObject)?.get("message")?.asText()
                }
            }

        if (errorMessage == null) {
            Log.d("LightningInvoiceResolver") { "Error parsing LNResponse: $body" }
        }

        return errorMessage?.let { LnurlFailureDetail.ServerMessage(it) }
            ?: LnurlFailureDetail.HttpStatus(response.status, response.reason)
    }

    /**
     * @param onZapRequestSent receives the zap request that was ACTUALLY sent to the
     *   callback, or null when it was not. A provider that does not advertise
     *   `allowsNostr` never sees [nostrRequest], and its invoice therefore commits to
     *   nothing about it — so a caller must not go on to claim the two are bound. See
     *   the drop below.
     */
    suspend fun lnAddressInvoice(
        lnAddress: String,
        milliSats: Long,
        message: String,
        nostrRequest: ZapRequestEvent? = null,
        onProgress: (percent: Float) -> Unit,
        onZapRequestSent: (ZapRequestEvent?) -> Unit = {},
    ): String {
        val lnurlpUrl = assembleUrl(lnAddress)

        val lnAddressJson =
            fetchLightningAddressJson(lnAddress)

        onProgress(0.4f)

        val lnurlp =
            try {
                Json.parseToJsonElement(lnAddressJson) as? JsonObject
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(InvoiceNotice.AddressJsonInvalid(lnAddress))
            }

        val callbackUrl = lnurlp?.get("callback")?.asText()?.ifBlank { null }

        if (callbackUrl == null) {
            throw LightningAddressError(InvoiceNotice.CallbackNotFound(lnAddress))
        }

        val allowsNostr = lnurlp["allowsNostr"]?.asBoolean() ?: false
        val nostrPubkey = lnurlp["nostrPubkey"]?.asText()?.ifBlank { null }

        // Prime the receipt-validation cache so incoming zap receipts can be
        // checked against this provider's nostrPubkey (NIP-57 Appendix F)
        // without re-fetching the lnurlp endpoint.
        if (lnurlpUrl != null) {
            LnurlEndpointCache.put(
                lnurlpUrl,
                LnurlEndpointInfo(nostrPubkey = nostrPubkey, allowsNostr = allowsNostr),
            )
        }

        // NIP-57 binds a zap request to its invoice through `description_hash`, and a
        // provider that ignores `nostr=` mints an invoice that commits to nothing about
        // it. Report what actually went, so a caller cannot attach the event to a
        // payment it was never bound to.
        val sentZapRequest = nostrRequest?.takeIf { allowsNostr }
        onZapRequestSent(sentZapRequest)

        val invoice =
            fetchLightningInvoice(
                lnCallback = callbackUrl,
                milliSats = milliSats,
                message = message,
                nostrRequest = sentZapRequest,
            )

        onProgress(0.6f)

        val lnInvoice =
            try {
                Json.parseToJsonElement(invoice) as? JsonObject
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw LightningAddressError(InvoiceNotice.InvoiceJsonInvalid(lnAddress))
            }

        val pr = lnInvoice?.get("pr")?.asText()?.ifBlank { null }

        if (pr == null) {
            onProgress(0.0f)
            val reason = lnInvoice?.get("reason")?.asText()?.ifBlank { null }

            if (reason != null) {
                throw LightningAddressError(InvoiceNotice.WalletRefused(lnAddress, reason))
            } else {
                throw LightningAddressError(InvoiceNotice.InvoiceMissing(lnAddress))
            }
        }

        // Forces LN Invoice amount to be the requested amount.
        val expectedAmountInSats = roundHalfUpToSats(milliSats)

        val invoiceAmount = LnInvoiceUtil.getAmountInSats(pr)

        if (invoiceAmount.toLongValue() != expectedAmountInSats) {
            onProgress(0.0f)
            throw LightningAddressError(InvoiceNotice.WrongAmount(invoiceAmount.toLongValue(), lnAddress, expectedAmountInSats))
        }

        onProgress(0.7f)

        return pr
    }
}

/** Millisats to sats, rounding a half sat up, as `BigDecimal.divide(1000, HALF_UP)` did. */
internal fun roundHalfUpToSats(milliSats: Long): Long = if (milliSats >= 0) (milliSats + 500) / 1000 else -((-milliSats + 500) / 1000)

private fun parseJson(body: String): JsonElement? =
    try {
        Json.parseToJsonElement(body)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

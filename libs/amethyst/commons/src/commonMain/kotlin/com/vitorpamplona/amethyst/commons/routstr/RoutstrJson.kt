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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * What a Routstr node answers, read leniently: every field may be missing, a number may come as a
 * string, and an unknown shape reads as null rather than throwing. Shapes are the live nodes'
 * (routstr-core 0.5.1, checked 2026-11-13), which differ from the docs in places.
 */

/** `GET /v1/info`, which needs no key. A node is a Routstr node when it answers with [npub] and [mints]. */
@Immutable
data class RoutstrInfo(
    val name: String?,
    val description: String?,
    val version: String?,
    val npub: String?,
    val mints: List<String>?,
    val httpUrl: String?,
    val onionUrl: String?,
) {
    val isRoutstr: Boolean get() = npub != null && mints != null

    /** The node's key, from [npub] (an `npub1…` or a hex key); null when it gives none or a bad one. */
    val pubKeyHex: HexKey? get() = npub?.trim()?.let { decodePublicKeyAsHexOrNull(it) }
}

/** `sats_pricing`: OpenRouter's pricing fields, in sats per token (and per request). Null means "not said". */
@Immutable
data class SatsPricing(
    val prompt: Double?,
    val completion: Double?,
    val request: Double?,
    /** The most the node reserves for one request: the balance must cover it, or the node answers 402. */
    val maxCost: Double?,
) {
    val priced: Boolean get() = prompt != null && completion != null
}

/** One entry of `GET /v1/models` (OpenRouter's shape, plus [sats]). */
@Immutable
data class RoutstrModel(
    val id: String,
    val name: String?,
    val contextLength: Long?,
    /** Whether the model writes text (`architecture.output_modalities` has `text`, or says nothing). */
    val textOutput: Boolean,
    val enabled: Boolean,
    val usdPrompt: Double?,
    val usdCompletion: Double?,
    val sats: SatsPricing?,
    val maxCompletionTokens: Long?,
)

/** `GET /v1/balance/info`: [balanceMsats] is what can be spent. The echoed `api_key` is never read. */
@Immutable
data class RoutstrBalance(
    val balanceMsats: Long,
    val reservedMsats: Long?,
    val totalRequests: Long?,
    val totalSpentMsats: Long?,
    val validityDate: Long?,
) {
    val balanceSats: Long get() = balanceMsats / 1000
}

/** `POST /v2/lightning/invoice`. */
@Immutable
data class RoutstrInvoice(
    val invoiceId: String,
    val bolt11: String,
    val amountSats: Long,
    val expiresAt: Long?,
    val paymentHash: String?,
)

/** `GET /v2/lightning/invoice/{id}/status` and `POST /v2/lightning/recover`. */
@Immutable
data class RoutstrInvoiceStatus(
    val status: String,
    val apiKey: String?,
    val amountSats: Long?,
    val paidAt: Long?,
    val expiresAt: Long?,
) {
    /** `paid`: the node has the money. */
    val isPaid: Boolean get() = status.equals("paid", ignoreCase = true) || paidAt != null

    /**
     * `settlement_pending` or `reconciliation_required`: the payment reached the node, which is
     * still settling it. Kept as paid: never dropped, never bought again.
     */
    val isSettling: Boolean get() = status.equals("settlement_pending", ignoreCase = true) || status.equals("reconciliation_required", ignoreCase = true)

    /** `expired`: not final, a node keeps settling an expired invoice for 24 hours. */
    val isExpired: Boolean get() = status.equals("expired", ignoreCase = true)

    override fun toString() = "RoutstrInvoiceStatus(status=$status, apiKey=${if (apiKey != null) "<redacted>" else "<none>"}, amountSats=$amountSats)"
}

/**
 * `POST /v1/balance/create` or `/v1/balance/topup` with a Cashu token. [changeToken] is money
 * the node handed back (a foreign mint's token, or what didn't fit): it must reach the user's
 * wallet, never be dropped.
 */
@Immutable
data class RoutstrCashuResult(
    val apiKey: String?,
    val balanceMsats: Long?,
    val changeToken: String?,
    val changeAmount: Long?,
    val changeUnit: String?,
) {
    override fun toString() = "RoutstrCashuResult(apiKey=${if (apiKey != null) "<redacted>" else "<none>"}, change=${changeAmount ?: 0} ${changeUnit.orEmpty()})"
}

object RoutstrJson {
    private val lenient = Json { ignoreUnknownKeys = true }

    fun parse(text: String): JsonElement? =
        try {
            lenient.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            null
        }

    internal fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)
            ?.takeIf { it !is JsonNull }
            ?.content
            ?.trim()
            ?.ifEmpty { null }

    internal fun JsonObject.num(key: String): Double? {
        val primitive = get(key) as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return (primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull())?.takeIf { it.isFinite() }
    }

    internal fun JsonObject.long(key: String): Long? {
        val primitive = get(key) as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.longOrNull ?: num(key)?.toLong()
    }

    internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.strings(key: String): List<String>? =
        (get(key) as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)
                ?.takeIf { p -> p.isString }
                ?.content
                ?.trim()
                ?.ifEmpty { null }
        }

    fun info(body: String): RoutstrInfo? {
        val json = parse(body) as? JsonObject ?: return null
        return RoutstrInfo(
            name = json.str("name"),
            description = json.str("description"),
            version = json.str("version"),
            npub = json.str("npub"),
            mints = json.strings("mints"),
            httpUrl = json.str("http_url"),
            onionUrl = json.str("onion_url"),
        )
    }

    fun satsPricing(raw: JsonElement?): SatsPricing? {
        val json = raw as? JsonObject ?: return null

        fun price(key: String) = json.num(key)?.takeIf { it >= 0 }
        return SatsPricing(price("prompt"), price("completion"), price("request"), price("max_cost"))
    }

    /** `GET /v1/models`: `{"data": [...]}` or a bare array. */
    fun models(body: String): List<RoutstrModel> {
        val root = parse(body)
        val list = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: return emptyList()
        return list.mapNotNull { entry ->
            val json = entry as? JsonObject ?: return@mapNotNull null
            val id = json.str("id") ?: return@mapNotNull null
            val pricing = json.obj("pricing")
            val modalities = json.obj("architecture")?.get("output_modalities") as? JsonArray
            val textOutput = modalities == null || modalities.any { (it as? JsonPrimitive)?.content.equals("text", ignoreCase = true) }
            RoutstrModel(
                id = id,
                name = json.str("name"),
                contextLength = json.long("context_length"),
                textOutput = textOutput,
                enabled = (json["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                usdPrompt = pricing?.num("prompt")?.takeIf { it >= 0 },
                usdCompletion = pricing?.num("completion")?.takeIf { it >= 0 },
                sats = satsPricing(json["sats_pricing"]),
                maxCompletionTokens = json.obj("top_provider")?.long("max_completion_tokens"),
            )
        }
    }

    fun balance(body: String): RoutstrBalance? {
        val json = parse(body) as? JsonObject ?: return null
        val balance = json.long("balance") ?: return null
        return RoutstrBalance(
            balanceMsats = balance,
            reservedMsats = json.long("reserved"),
            totalRequests = json.long("total_requests"),
            totalSpentMsats = json.long("total_spent"),
            validityDate = json.long("validity_date"),
        )
    }

    fun invoice(body: String): RoutstrInvoice? {
        val json = parse(body) as? JsonObject ?: return null
        val id = json.str("invoice_id") ?: return null
        val bolt11 = json.str("bolt11") ?: return null
        return RoutstrInvoice(id, bolt11, json.long("amount_sats") ?: 0, json.long("expires_at"), json.str("payment_hash"))
    }

    fun invoiceStatus(body: String): RoutstrInvoiceStatus? {
        val json = parse(body) as? JsonObject ?: return null
        val apiKey = json.str("api_key")
        // `recover` may answer without a status: a key means it was paid.
        val status = json.str("status") ?: if (apiKey != null) "paid" else return null
        return RoutstrInvoiceStatus(status, apiKey, json.long("amount_sats"), json.long("paid_at"), json.long("expires_at"))
    }

    fun cashu(body: String): RoutstrCashuResult? {
        val json = parse(body) as? JsonObject ?: return null
        return RoutstrCashuResult(
            apiKey = json.str("api_key"),
            balanceMsats = json.long("balance"),
            changeToken = json.str("change_token"),
            changeAmount = json.long("change_amount"),
            changeUnit = json.str("change_unit"),
        )
    }
}

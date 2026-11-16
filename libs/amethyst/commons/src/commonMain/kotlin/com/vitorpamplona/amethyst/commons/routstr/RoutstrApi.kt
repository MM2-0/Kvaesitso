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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether a Lightning invoice buys a new key or adds to one. */
@Serializable
enum class RoutstrPurpose(
    val wire: String,
) {
    @SerialName("create")
    CREATE("create"),

    @SerialName("topup")
    TOPUP("topup"),
}

/**
 * A Routstr node's HTTP API. [base] is the node's root (`https://node.example`, or its
 * `http://….onion` when Tor is on), without `/v1`. Every call throws [RoutstrException] on an
 * error answer or no answer; none of them ever logs a key or a token.
 */
interface RoutstrApi {
    /** `GET /v1/info`: no key. */
    suspend fun info(base: String): RoutstrInfo

    /** `GET /v1/models`, with `sats_pricing`; with [apiKey] when there is one. */
    suspend fun models(
        base: String,
        apiKey: String? = null,
    ): List<RoutstrModel>

    /** `GET /v1/balance/info` with `Authorization: Bearer`. */
    suspend fun balance(
        base: String,
        apiKey: String,
    ): RoutstrBalance

    /** `POST /v2/lightning/invoice`; a top-up sends [apiKey] as the Bearer (the body's `api_key` is deprecated). */
    suspend fun createInvoice(
        base: String,
        amountSats: Long,
        purpose: RoutstrPurpose,
        apiKey: String? = null,
    ): RoutstrInvoice

    /** `GET /v2/lightning/invoice/{invoiceId}/status`. */
    suspend fun invoiceStatus(
        base: String,
        invoiceId: String,
    ): RoutstrInvoiceStatus

    /** `POST /v2/lightning/recover`: the key of a paid invoice whose id was lost. */
    suspend fun recover(
        base: String,
        bolt11: String,
    ): RoutstrInvoiceStatus

    /** `POST /v1/balance/create` with a Cashu token. */
    suspend fun createWithCashu(
        base: String,
        token: String,
    ): RoutstrCashuResult

    /** `POST /v1/balance/topup` with a Cashu token and the key as the Bearer. */
    suspend fun topupWithCashu(
        base: String,
        apiKey: String,
        token: String,
    ): RoutstrCashuResult
}

/** The app's Routstr client: its calls travel the way money calls do, Tor included when the user asked for it. */
expect fun IRoleBasedHttpClientBuilder.routstrApi(): RoutstrApi

/** A platform with no HTTP client for Routstr: every call says so. */
object UnsupportedRoutstrApi : RoutstrApi {
    private fun no(): Nothing = throw RoutstrException(RoutstrError(null, RoutstrFailure.UNSUPPORTED))

    override suspend fun info(base: String): RoutstrInfo = no()

    override suspend fun models(
        base: String,
        apiKey: String?,
    ): List<RoutstrModel> = no()

    override suspend fun balance(
        base: String,
        apiKey: String,
    ): RoutstrBalance = no()

    override suspend fun createInvoice(
        base: String,
        amountSats: Long,
        purpose: RoutstrPurpose,
        apiKey: String?,
    ): RoutstrInvoice = no()

    override suspend fun invoiceStatus(
        base: String,
        invoiceId: String,
    ): RoutstrInvoiceStatus = no()

    override suspend fun recover(
        base: String,
        bolt11: String,
    ): RoutstrInvoiceStatus = no()

    override suspend fun createWithCashu(
        base: String,
        token: String,
    ): RoutstrCashuResult = no()

    override suspend fun topupWithCashu(
        base: String,
        apiKey: String,
        token: String,
    ): RoutstrCashuResult = no()
}

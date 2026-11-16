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

import com.vitorpamplona.amethyst.commons.routstr.RoutstrJson.long
import com.vitorpamplona.amethyst.commons.routstr.RoutstrJson.obj
import com.vitorpamplona.amethyst.commons.routstr.RoutstrJson.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What went wrong with a Routstr call, for the screen to put in words (commons has no strings). */
enum class RoutstrFailure {
    /** 402 `insufficient_balance`: the key works, the balance doesn't cover the request. */
    INSUFFICIENT_BALANCE,

    /** 401 `key_not_found`: a Routstr key works only on the node that issued it. */
    KEY_NOT_FOUND,

    /** 401 `invalid_api_key`, or any other 401/403. */
    INVALID_KEY,

    /** 401 `key_expired`. */
    KEY_EXPIRED,

    /** 424 `UPSTREAM_UNAVAILABLE`: the node's own provider is down. Nothing was spent. */
    UPSTREAM_UNAVAILABLE,

    /** 429, the node's or its upstream's. */
    RATE_LIMITED,

    /** 503 `payment_settlement_failed`: the node charged and withheld the answer. Never resent on its own. */
    PAYMENT_SETTLEMENT_FAILED,

    /** A Cashu token the node wouldn't take: spent, or from a mint it doesn't accept. */
    TOKEN_REJECTED,

    /** The invoice or the key isn't there (404). */
    NOT_FOUND,

    /** The answer wasn't what a Routstr node sends. */
    UNEXPECTED,

    /** No answer: offline, a wrong URL, or Tor isn't running. */
    UNREACHABLE,

    /** This platform can't make the call. */
    UNSUPPORTED,

    /** Any other HTTP status. */
    HTTP,
}

/** A Routstr error, read from either of the shapes nodes send: `{"error": {…}}` or `{"detail": {"error": {…}}}`. */
data class RoutstrError(
    val status: Int?,
    val failure: RoutstrFailure,
    val code: String? = null,
    val type: String? = null,
    /** What the node said, trimmed: never shown for a key error, which may echo the key. */
    val message: String? = null,
    val requiredMsats: Long? = null,
    val availableMsats: Long? = null,
    val shortfallMsats: Long? = null,
) {
    /** The sats to top up for a 402, rounded up: a fraction of a sat still has to be paid. */
    val shortfallSats: Long? get() = shortfallMsats?.let { (it + 999) / 1000 } ?: requiredMsats?.let { req -> availableMsats?.let { (req - it + 999) / 1000 } }
}

class RoutstrException(
    val error: RoutstrError,
) : Exception("Routstr: ${error.failure} ${error.status ?: ""} ${error.code.orEmpty()}") {
    val failure: RoutstrFailure get() = error.failure
}

object RoutstrErrors {
    /** [status] and its [body] as a [RoutstrError]; the codes win over the status where they disagree. */
    fun parse(
        status: Int,
        body: String,
    ): RoutstrError {
        val root = RoutstrJson.parse(body) as? JsonObject
        val detail = root?.get("detail")
        // `{"detail": {"error": {...}}}`, `{"detail": {...}}`, `{"error": {...}}`, `{"error": "text"}`, `{"detail": "text"}`.
        val inner =
            (detail as? JsonObject)?.obj("error")
                ?: root?.obj("error")
                ?: (detail as? JsonObject)
        val code = inner?.str("code") ?: (detail as? JsonObject)?.str("code") ?: root?.str("code")
        val type = inner?.str("type") ?: root?.str("type")
        val message =
            inner?.str("message")
                ?: (root?.get("error") as? JsonPrimitive)?.content
                ?: (detail as? JsonPrimitive)?.content
        val details = inner?.obj("details") ?: inner
        val required = details?.long("required_msats") ?: inner?.long("required_msats")
        val available = details?.long("available_msats") ?: inner?.long("available_msats")
        val shortfall = details?.long("shortfall_msats") ?: inner?.long("shortfall_msats")
        return RoutstrError(status, failureOf(status, code, type, message), code, type, message?.take(200), required, available, shortfall)
    }

    fun failureOf(
        status: Int,
        code: String?,
        type: String?,
        message: String?,
    ): RoutstrFailure {
        val c = code?.lowercase().orEmpty()
        val t = type?.lowercase().orEmpty()
        val m = message?.lowercase().orEmpty()
        return when {
            c == "insufficient_balance" || t == "insufficient_balance" || status == 402 -> RoutstrFailure.INSUFFICIENT_BALANCE
            c == "key_not_found" -> RoutstrFailure.KEY_NOT_FOUND
            c == "key_expired" -> RoutstrFailure.KEY_EXPIRED
            c == "invalid_api_key" -> RoutstrFailure.INVALID_KEY
            c == "payment_settlement_failed" || t == "payment_settlement_failed" -> RoutstrFailure.PAYMENT_SETTLEMENT_FAILED
            c == "upstream_unavailable" || status == 424 -> RoutstrFailure.UPSTREAM_UNAVAILABLE
            c == "upstream_rate_limit" || status == 429 -> RoutstrFailure.RATE_LIMITED
            status == 401 || status == 403 -> RoutstrFailure.INVALID_KEY
            "token" in c || "cashu" in c || (status in 400..422 && ("token" in m || "proof" in m || "mint" in m)) -> RoutstrFailure.TOKEN_REJECTED
            status == 404 -> RoutstrFailure.NOT_FOUND
            else -> RoutstrFailure.HTTP
        }
    }
}

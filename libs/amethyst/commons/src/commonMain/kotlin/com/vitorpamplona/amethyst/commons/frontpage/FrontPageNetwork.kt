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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.routstr.RoutstrErrors
import com.vitorpamplona.amethyst.commons.routstr.RoutstrFailure
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag

/** Why the writer failed, for the UI to put in words (commons has no strings). */
enum class WriterFailure {
    REJECTED,
    NO_CREDIT,
    RATE_LIMITED,
    UNKNOWN_MODEL,
    UNREACHABLE,
    EMPTY,
    HTTP,
    UNSUPPORTED,

    /** The answer stopped before the page was done: an error frame, a `finish_reason` other than `stop`, or no end at all. */
    CUT_OFF,

    /** The key's URL breaks NIP-FA's https rule, so the key was never sent. */
    INSECURE_URL,

    /** A Routstr node's 402 `insufficient_balance`: the key works, its balance is too low for this model. */
    LOW_BALANCE,

    /** A Routstr node's 401 `key_not_found`: the key belongs to another node. */
    KEY_NOT_ON_NODE,

    /** A Routstr node's 424: its own provider is down. Nothing was spent. */
    UPSTREAM_DOWN,

    /** A Routstr node's 503 `payment_settlement_failed`: it charged and withheld the answer. Never resent on its own. */
    SETTLEMENT_FAILED,
}

class WriterException(
    val failure: WriterFailure,
    val status: Int? = null,
    /** What the provider said, trimmed; shown only for [WriterFailure.HTTP]. */
    val providerSaid: String = "",
    /** For [WriterFailure.LOW_BALANCE]: the sats to top up, when the node said. */
    val shortfallSats: Long? = null,
) : Exception("Writer failed: $failure ${status ?: ""}")

fun failureFor(status: Int): WriterFailure =
    when (status) {
        401, 403 -> WriterFailure.REJECTED
        402 -> WriterFailure.NO_CREDIT
        429 -> WriterFailure.RATE_LIMITED
        404 -> WriterFailure.UNKNOWN_MODEL
        424 -> WriterFailure.UPSTREAM_DOWN
        else -> WriterFailure.HTTP
    }

/**
 * [status] with the error [body] read: a Routstr node's codes (`insufficient_balance`,
 * `key_not_found`, `payment_settlement_failed`, `UPSTREAM_UNAVAILABLE`, in `{error}` or
 * `{detail:{error}}`) say more than the status does. Any other provider reads as [failureFor].
 */
fun writerExceptionFor(
    status: Int,
    body: String,
): WriterException {
    val said =
        body
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
    val routstr = RoutstrErrors.parse(status, body)
    val failure =
        when {
            routstr.code.equals("insufficient_balance", ignoreCase = true) -> WriterFailure.LOW_BALANCE
            routstr.code.equals("key_not_found", ignoreCase = true) -> WriterFailure.KEY_NOT_ON_NODE
            routstr.failure == RoutstrFailure.PAYMENT_SETTLEMENT_FAILED -> WriterFailure.SETTLEMENT_FAILED
            routstr.code.equals("upstream_unavailable", ignoreCase = true) -> WriterFailure.UPSTREAM_DOWN
            routstr.code.equals("upstream_rate_limit", ignoreCase = true) -> WriterFailure.RATE_LIMITED
            else -> failureFor(status)
        }
    return WriterException(failure, status, said, routstr.shortfallSats.takeIf { failure == WriterFailure.LOW_BALANCE })
}

/** The models `GET {url}/models` lists for this key, with prices where it gives them; ranking them is [rankModels]'s job. */
expect suspend fun IRoleBasedHttpClientBuilder.listAgentModels(key: AgentKeyTag): List<AgentModel>

/**
 * One Chat Completions call, streamed so the reader sees the model think and the page arrive
 * ([onEvent]: sent, first byte, reasoning, text); non-streamed when the provider refuses a stream,
 * said with [WriterEvent.AllAtOnce] before the whole answer comes. [maxTokens] and [effort] go
 * out as [chatRequestBody] maps them; a provider that refuses the request outright (400 or 422)
 * while it carries them is asked once more without them, as before they existed.
 * @throws WriterException
 */
expect suspend fun IRoleBasedHttpClientBuilder.chatComplete(
    key: AgentKeyTag,
    model: String,
    messages: List<ChatMessage>,
    maxTokens: Int? = null,
    effort: WritingEffort = WritingEffort.STANDARD,
    onEvent: (WriterEvent) -> Unit,
): Written

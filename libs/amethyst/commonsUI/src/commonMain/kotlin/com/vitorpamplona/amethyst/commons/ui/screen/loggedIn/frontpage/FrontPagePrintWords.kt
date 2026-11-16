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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_credit
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_cut_off
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_empty
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_http
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_insecure
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_key_not_on_node
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_low_balance
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_low_balance_sats
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_model
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rate
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rejected
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_settlement
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_upstream_down
import com.vitorpamplona.amethyst.commons.resources.front_page_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_credit
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_cut_off
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_empty
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_http
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_insecure
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_key_not_on_node
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_low_balance
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_model
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_rate
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_refused
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_rejected
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_settlement
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_unsupported
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_upstream_down
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_title
import com.vitorpamplona.amethyst.commons.resources.front_page_unsupported
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import org.jetbrains.compose.resources.StringResource

/**
 * A print that gave no paper, in words: a full sentence for the screen and the notification
 * ([sentenceRes]), a short one for the popup ([shortRes], "The provider rejected the key."). One table for every
 * place that says it, so the screen, the popup and both platforms' notifications agree.
 */
fun WriterFailure.sentenceRes(): StringResource =
    when (this) {
        WriterFailure.REJECTED -> Res.string.front_page_fail_rejected
        WriterFailure.NO_CREDIT -> Res.string.front_page_fail_credit
        WriterFailure.RATE_LIMITED -> Res.string.front_page_fail_rate
        WriterFailure.UNKNOWN_MODEL -> Res.string.front_page_fail_model
        WriterFailure.EMPTY -> Res.string.front_page_fail_empty
        WriterFailure.HTTP -> Res.string.front_page_fail_http
        WriterFailure.UNSUPPORTED -> Res.string.front_page_unsupported
        WriterFailure.UNREACHABLE -> Res.string.front_page_fail_unreachable
        WriterFailure.CUT_OFF -> Res.string.front_page_fail_cut_off
        WriterFailure.INSECURE_URL -> Res.string.front_page_fail_insecure
        WriterFailure.LOW_BALANCE -> Res.string.front_page_fail_low_balance
        WriterFailure.KEY_NOT_ON_NODE -> Res.string.front_page_fail_key_not_on_node
        WriterFailure.UPSTREAM_DOWN -> Res.string.front_page_fail_upstream_down
        WriterFailure.SETTLEMENT_FAILED -> Res.string.front_page_fail_settlement
    }

private fun WriterFailure.shortRes(): StringResource =
    when (this) {
        WriterFailure.REJECTED -> Res.string.front_page_reason_rejected
        WriterFailure.NO_CREDIT -> Res.string.front_page_reason_credit
        WriterFailure.RATE_LIMITED -> Res.string.front_page_reason_rate
        WriterFailure.UNKNOWN_MODEL -> Res.string.front_page_reason_model
        WriterFailure.EMPTY -> Res.string.front_page_reason_empty
        WriterFailure.HTTP -> Res.string.front_page_reason_http
        WriterFailure.UNSUPPORTED -> Res.string.front_page_reason_unsupported
        WriterFailure.UNREACHABLE -> Res.string.front_page_reason_unreachable
        WriterFailure.CUT_OFF -> Res.string.front_page_reason_cut_off
        WriterFailure.INSECURE_URL -> Res.string.front_page_reason_insecure
        WriterFailure.LOW_BALANCE -> Res.string.front_page_reason_low_balance
        WriterFailure.KEY_NOT_ON_NODE -> Res.string.front_page_reason_key_not_on_node
        WriterFailure.UPSTREAM_DOWN -> Res.string.front_page_reason_upstream_down
        WriterFailure.SETTLEMENT_FAILED -> Res.string.front_page_reason_settlement
    }

private fun PrintJobEnd.sentence(): Pair<StringResource, Int?>? =
    when (this) {
        is PrintJobEnd.Failed -> failure.sentenceRes() to status.takeIf { failure == WriterFailure.HTTP }
        PrintJobEnd.NotReady -> Res.string.front_page_not_ready to null
        PrintJobEnd.Refused -> Res.string.front_page_refused_title to null
        is PrintJobEnd.Ready -> null
    }

private fun PrintJobEnd.short(): Pair<StringResource, Int?>? =
    when (this) {
        is PrintJobEnd.Failed -> failure.shortRes() to status.takeIf { failure == WriterFailure.HTTP }
        PrintJobEnd.NotReady -> Res.string.front_page_reason_not_ready to null
        PrintJobEnd.Refused -> Res.string.front_page_reason_refused to null
        is PrintJobEnd.Ready -> null
    }

/** Why a print gave no paper, as a sentence, outside composition (a notification); null for a paper. */
suspend fun loadPrintEndSentence(end: PrintJobEnd): String? {
    val shortfall = (end as? PrintJobEnd.Failed)?.takeIf { it.failure == WriterFailure.LOW_BALANCE }?.shortfallSats
    if (shortfall != null) return loadPluralStringRes(Res.plurals.front_page_fail_low_balance_sats, shortfall.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(shortfall))
    val (res, status) = end.sentence() ?: return null
    return if (res == Res.string.front_page_fail_http) loadStringRes(res, status ?: 0) else loadStringRes(res)
}

/** Why a print failed, as the screen says it: with the sats to top up when a Routstr node said. */
@Composable
fun writerFailureSentence(
    failure: WriterFailure,
    status: Int?,
    shortfallSats: Long?,
): String =
    when {
        failure == WriterFailure.LOW_BALANCE && shortfallSats != null ->
            pluralStringRes(Res.plurals.front_page_fail_low_balance_sats, shortfallSats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(shortfallSats))
        failure == WriterFailure.HTTP -> stringRes(failure.sentenceRes(), status ?: 0)
        else -> stringRes(failure.sentenceRes())
    }

/** "The provider rejected the key.": why a print gave no paper, a short sentence for the popup; null for a paper. */
@Composable
fun printEndShort(end: PrintJobEnd): String? {
    val (res, status) = end.short() ?: return null
    return if (res == Res.string.front_page_reason_http) stringRes(res, status ?: 0) else stringRes(res)
}

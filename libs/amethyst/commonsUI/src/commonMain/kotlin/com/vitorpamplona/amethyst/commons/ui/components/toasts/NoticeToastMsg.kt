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
package com.vitorpamplona.amethyst.commons.ui.components.toasts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.Bolt12OfferNotice
import com.vitorpamplona.amethyst.commons.notices.CashuRedeemNotice
import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice

/** A typed [UserNotice] from headless code, worded by `NoticeDialog`. */
@Immutable
class NoticeToastMsg(
    val notice: UserNotice,
) : ToastMsg(notice.severity()) {
    /** Notices are value types, so the same notice twice is the same message. */
    override val dedupeKey: Any get() = notice
}

/** Confirmations read as information, failures with a cause as errors, the rest as warnings. */
private fun UserNotice.severity(): ToastSeverity =
    when (this) {
        Bolt12OfferNotice.PaymentSent, ConcordNotice.ChannelKeyRotated, is CashuRedeemNotice.Redeemed -> ToastSeverity.INFO
        is SignerNotice.Failed, is ActionFailed -> ToastSeverity.ERROR
        else -> ToastSeverity.WARNING
    }

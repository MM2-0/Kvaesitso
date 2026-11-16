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
package com.vitorpamplona.amethyst.commons.onchain.ui

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendError
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_backend_not_configured
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_broadcast
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_build
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_load_utxos
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_publish_receipt
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_publish_receipt_partial
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_recipient_dust
import com.vitorpamplona.amethyst.commons.resources.onchain_send_error_sign
import com.vitorpamplona.amethyst.commons.resources.onchain_stage_broadcasting
import com.vitorpamplona.amethyst.commons.resources.onchain_stage_building
import com.vitorpamplona.amethyst.commons.resources.onchain_stage_loading_utxos
import com.vitorpamplona.amethyst.commons.resources.onchain_stage_publishing
import com.vitorpamplona.amethyst.commons.resources.onchain_stage_signing
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/**
 * Maps the machine-readable [OnchainZapSendResult.Failure] coming out of the
 * KMP sender (which only carries English text) to localized UI strings.
 */
suspend fun OnchainZapSendResult.Failure.userMessage(): String {
    val total = totalReceipts
    return if (error == OnchainZapSendError.RECEIPT_PUBLISH_FAILED && total != null) {
        loadPluralStringRes(
            Res.plurals.onchain_send_error_publish_receipt_partial,
            total,
            publishedReceiptEventIds.size,
            total,
        )
    } else {
        loadStringRes(error.messageRes())
    }
}

/**
 * Untranslated diagnostic detail worth showing under the localized headline.
 * Which errors carry a human-readable cause is a fact about the sender,
 * declared on [OnchainZapSendError.causeIsUserFacing].
 */
fun OnchainZapSendResult.Failure.technicalDetail(): String? = if (error.causeIsUserFacing) cause?.message else null

private fun OnchainZapSendError.messageRes(): StringResource =
    when (this) {
        OnchainZapSendError.BACKEND_NOT_CONFIGURED -> Res.string.onchain_send_error_backend_not_configured
        OnchainZapSendError.LOAD_UTXOS_FAILED -> Res.string.onchain_send_error_load_utxos
        OnchainZapSendError.BUILD_FAILED -> Res.string.onchain_send_error_build
        OnchainZapSendError.RECIPIENT_BELOW_DUST -> Res.string.onchain_send_error_recipient_dust
        OnchainZapSendError.SIGN_FAILED -> Res.string.onchain_send_error_sign
        OnchainZapSendError.BROADCAST_FAILED -> Res.string.onchain_send_error_broadcast
        OnchainZapSendError.RECEIPT_PUBLISH_FAILED -> Res.string.onchain_send_error_publish_receipt
    }

fun OnchainZapSendStage.labelRes(): StringResource =
    when (this) {
        OnchainZapSendStage.LOADING_UTXOS -> Res.string.onchain_stage_loading_utxos
        OnchainZapSendStage.BUILDING -> Res.string.onchain_stage_building
        OnchainZapSendStage.SIGNING -> Res.string.onchain_stage_signing
        OnchainZapSendStage.BROADCASTING -> Res.string.onchain_stage_broadcasting
        OnchainZapSendStage.PUBLISHING -> Res.string.onchain_stage_publishing
    }

/** Composition-side twin of [userMessage], for the failure card. */
@Composable
fun OnchainZapSendResult.Failure.userMessageText(): String {
    val total = totalReceipts
    return if (error == OnchainZapSendError.RECEIPT_PUBLISH_FAILED && total != null) {
        pluralStringRes(
            Res.plurals.onchain_send_error_publish_receipt_partial,
            total,
            publishedReceiptEventIds.size,
            total,
        )
    } else {
        stringRes(error.messageRes())
    }
}

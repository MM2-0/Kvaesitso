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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.error_dialog_payment_error
import com.vitorpamplona.amethyst.commons.resources.no_payment_app_found
import com.vitorpamplona.amethyst.commons.resources.no_payment_targets_message
import com.vitorpamplona.amethyst.commons.resources.payment_targets
import com.vitorpamplona.amethyst.commons.resources.show_qr
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.PaymentTargetPill
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.paymentTargetUri
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import kotlinx.coroutines.launch

@Composable
fun PaymentTargetsDialog(
    targets: List<PaymentTarget>,
    onDismiss: () -> Unit,
    /** Returns true when it handled the target with an in-app wallet. */
    payInApp: ((PaymentTarget) -> Boolean)? = null,
) {
    val noPaymentAppFoundStr = stringRes(Res.string.no_payment_app_found)
    val shortNotice = rememberShortNotice()
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }

    M3ActionDialog(
        title = stringRes(Res.string.payment_targets),
        onDismiss = onDismiss,
    ) {
        M3ActionSection {
            if (targets.isEmpty()) {
                M3ActionRow(
                    icon = MaterialSymbols.AccountBalanceWallet,
                    text = stringRes(Res.string.no_payment_targets_message),
                    enabled = false,
                    onClick = {},
                )
            } else {
                targets.forEach { target ->
                    PaymentTargetRow(
                        target = target,
                        onShowQr = { qrContent = target.authority },
                        onCopy = {
                            scope.launch {
                                clipboardManager.setText(target.authority)
                                shortNotice.show(loadStringRes(Res.string.copied_to_clipboard))
                            }
                        },
                        onPay = {
                            if (payInApp?.invoke(target) != true) {
                                // Same handoff the profile chip does: the type's own
                                // scheme when it has one (payto:// only as the
                                // fallback), and no task flags — CLEAR_TASK used to
                                // wipe whatever the wallet app already had open.
                                runCatching { uriHandler.openUri(paymentTargetUri(target)) }
                                    .onSuccess { onDismiss() }
                                    .onFailure { errorMessage = noPaymentAppFoundStr }
                            }
                        },
                    )
                }
            }
        }
    }

    qrContent?.let { value ->
        PaymentTargetQrDialog(value = value, onDismiss = { qrContent = null })
    }

    errorMessage?.let { msg ->
        ErrorMessageDialog(
            title = stringRes(Res.string.error_dialog_payment_error),
            textContent = msg,
            onDismiss = { errorMessage = null },
        )
    }
}

@Composable
private fun PaymentTargetRow(
    target: PaymentTarget,
    onShowQr: () -> Unit,
    onCopy: () -> Unit,
    onPay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Same pill the profile page renders for this target: type icon,
        // tinted type label and the shortened authority, instead of the raw
        // wallet id spelled out over two lines. Tapping it pays and long-press
        // copies, exactly like on the profile, so the row carries no separate
        // pay button — three icon buttons left the address 0dp of width.
        Box(modifier = Modifier.weight(1f)) {
            PaymentTargetPill(target = target, onClick = onPay)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onShowQr) {
            Icon(
                symbol = MaterialSymbols.QrCode2,
                contentDescription = stringRes(Res.string.show_qr),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = stringRes(Res.string.copy_to_clipboard),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaymentTargetQrDialog(
    value: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QrCodeDrawer(
                    contents = value,
                    modifier = Modifier.size(260.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

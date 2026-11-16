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
package com.vitorpamplona.amethyst.commons.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.pay_elsewhere_description
import com.vitorpamplona.amethyst.commons.resources.pay_elsewhere_title
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * What to do when no app on this device can take a payment link: show it as a QR code for a
 * wallet on another device, and let the user copy it into any wallet. [uri] is the payment link
 * the wallet app would have received (`lightning:<invoice>` or `bitcoin:?lno=<offer>`).
 */
@Composable
fun PayElsewhereDialog(
    uri: String,
    onDismiss: () -> Unit,
) {
    val payment = remember(uri) { paymentFromUri(uri) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // The button says it was copied: a desktop has no toast to tell.
    var copied by remember(uri) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.pay_elsewhere_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringRes(Res.string.pay_elsewhere_description))
                QrCodeDrawer(contents = uri, modifier = Modifier.size(240.dp))
                Text(
                    text = payment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        clipboard.setText(payment)
                        copied = true
                    }
                },
            ) {
                Text(stringRes(if (copied) Res.string.copied_to_clipboard else Res.string.copy_to_clipboard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) }
        },
    )
}

/** The bare invoice or offer inside a payment link: what a wallet's paste field expects. */
internal fun paymentFromUri(uri: String): String =
    when {
        uri.startsWith("lightning:", ignoreCase = true) -> uri.substring("lightning:".length)
        uri.startsWith("bitcoin:?lno=", ignoreCase = true) -> uri.substring("bitcoin:?lno=".length)
        else -> uri
    }

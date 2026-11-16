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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_pay_withdraw_error
import com.vitorpamplona.amethyst.commons.resources.lnurl_label
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.lightning.LnWithdrawalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun MayBeWithdrawal(
    lnurlWord: String,
    accountViewModel: AccountViewModel,
) {
    var lnWithdrawal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = lnurlWord) {
        launch(Dispatchers.IO) { lnWithdrawal = LnWithdrawalUtil.findWithdrawal(lnurlWord) }
    }

    CrossfadeIfEnabled(targetState = lnWithdrawal) {
        if (it != null) {
            ClickableWithdrawal(withdrawalString = it)
        } else {
            Text(
                text = lnurlWord,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            )
        }
    }
}

/** [MayBeWithdrawal] for where previews are off: a one-line label instead of the LNURL, copy only. */
@Composable
fun CompactWithdrawalLabel(lnurlWord: String) {
    val withdrawal = remember(lnurlWord) { LnWithdrawalUtil.findWithdrawal(lnurlWord) }
    if (withdrawal == null) {
        Text(
            text = lnurlWord,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        )
        return
    }

    PaymentLabel(stringRes(Res.string.lnurl_label), copyValue = withdrawal) {
        Icon(
            imageVector = CustomHashTagIcons.Lightning,
            contentDescription = null,
            modifier = Size16Modifier,
            tint = Color.Unspecified,
        )
    }
}

@Composable
fun ClickableWithdrawal(withdrawalString: String) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val walletLauncher = rememberWalletAppLauncher()

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = stringRes(Res.string.error_dialog_pay_withdraw_error),
            textContent = showErrorMessageDialog ?: "",
            onDismiss = { showErrorMessageDialog = null },
        )
    }

    ClickableTextPrimary(
        text = "$withdrawalString ",
        onClick = { walletLauncher.payInvoice(withdrawalString, noWalletFoundStr, { }) { showErrorMessageDialog = it } },
    )
}

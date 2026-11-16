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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amount_in_sats
import com.vitorpamplona.amethyst.commons.resources.custom_zaps_add_a_message
import com.vitorpamplona.amethyst.commons.resources.custom_zaps_add_a_message_example
import com.vitorpamplona.amethyst.commons.resources.custom_zaps_add_a_message_nonzap
import com.vitorpamplona.amethyst.commons.resources.custom_zaps_add_a_message_private
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.feed_is_empty
import com.vitorpamplona.amethyst.commons.resources.manual_zaps
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.paid
import com.vitorpamplona.amethyst.commons.resources.pay
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.resources.send_onchain_instead
import com.vitorpamplona.amethyst.commons.resources.send_zap
import com.vitorpamplona.amethyst.commons.resources.wallet_number
import com.vitorpamplona.amethyst.commons.resources.zap_button_label
import com.vitorpamplona.amethyst.commons.resources.zap_type
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_nonzap
import com.vitorpamplona.amethyst.commons.resources.zap_type_nonzap_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_private
import com.vitorpamplona.amethyst.commons.resources.zap_type_private_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_public
import com.vitorpamplona.amethyst.commons.resources.zap_type_public_explainer
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList

class ZapOptionViewModel : ViewModel() {
    private var account: Account? = null

    var customAmount by mutableStateOf(TextFieldValue("21"))
    var customMessage by mutableStateOf(TextFieldValue(""))

    fun load(account: Account) {
        this.account = account
    }

    fun canSend(): Boolean = value() != null

    fun value(): Long? = customAmount.text.trim().toLongOrNull()

    fun cancel() {
        // No cleanup needed; dialog state is reset on close
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapCustomDialog(
    onZapStarts: () -> Unit,
    onClose: () -> Unit,
    onError: (title: String, text: String, user: User?) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
    accountViewModel: AccountViewModel,
    baseNote: Note,
) {
    val postViewModel: ZapOptionViewModel = rememberViewModel { ZapOptionViewModel() }

    LaunchedEffect(accountViewModel) { postViewModel.load(accountViewModel.account) }

    // Zaps on private rumors are forced private (AccountViewModel.zap), so
    // only offer the choices that match what will actually be sent.
    val isPrivateTarget = baseNote.isPrivateRumor()

    val zapTypes =
        listOf(
            Triple(
                ZapReceiptEvent.ZapType.PUBLIC,
                stringRes(id = Res.string.zap_type_public),
                stringRes(id = Res.string.zap_type_public_explainer),
            ),
            Triple(
                ZapReceiptEvent.ZapType.PRIVATE,
                stringRes(id = Res.string.zap_type_private),
                stringRes(id = Res.string.zap_type_private_explainer),
            ),
            Triple(
                ZapReceiptEvent.ZapType.ANONYMOUS,
                stringRes(id = Res.string.zap_type_anonymous),
                stringRes(id = Res.string.zap_type_anonymous_explainer),
            ),
            Triple(
                ZapReceiptEvent.ZapType.NONZAP,
                stringRes(id = Res.string.zap_type_nonzap),
                stringRes(id = Res.string.zap_type_nonzap_explainer),
            ),
        ).filter {
            !isPrivateTarget || it.first == ZapReceiptEvent.ZapType.PRIVATE || it.first == ZapReceiptEvent.ZapType.NONZAP
        }

    var selectedZapType by
        remember(accountViewModel, baseNote) {
            val default = accountViewModel.defaultZapType()
            mutableStateOf(
                if (isPrivateTarget && default != ZapReceiptEvent.ZapType.NONZAP) {
                    ZapReceiptEvent.ZapType.PRIVATE
                } else {
                    default
                },
            )
        }

    val presetAmounts = remember(accountViewModel) { accountViewModel.zapAmountChoices() }

    // True while the on-chain hand-off sheet is open. Dismissing the on-chain
    // sheet closes the whole zap flow — the user has already committed to a
    // payment method, no "go back to Lightning" is offered here.
    var sendOnchain by remember { mutableStateOf(false) }

    // Hides the "Send on-chain instead" hand-off when the user has turned the
    // on-chain wallet off, matching every other on-chain surface.
    val showOnchainWallet by accountViewModel.settings.uiSettingsFlow.showOnchainWallet
        .collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .imePaddingSafe(),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringRes(id = Res.string.send_zap),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Preset amount quick-select chips
                if (presetAmounts.isNotEmpty()) {
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presetAmounts.forEach { amount ->
                            SuggestionChip(
                                onClick = {
                                    postViewModel.customAmount = TextFieldValue(amount.toString())
                                },
                                label = { Text("⚡ ${showAmount(BigDecimal(amount))}") },
                            )
                        }
                    }
                }

                // Amount input
                OutlinedTextField(
                    label = { Text(text = stringRes(id = Res.string.amount_in_sats)) },
                    value = postViewModel.customAmount,
                    onValueChange = { postViewModel.customAmount = it },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number,
                        ),
                    placeholder = {
                        Text(
                            text = "21, 50, 100",
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    suffix = { Text(text = stringRes(id = Res.string.sats)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )

                // Zap type label
                Text(
                    text = stringRes(id = Res.string.zap_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                // Zap type selection chips
                FlowRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    zapTypes.forEach { (type, label, _) ->
                        FilterChip(
                            selected = selectedZapType == type,
                            onClick = { selectedZapType = type },
                            label = { Text(label) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                    }
                }

                // Message input
                SpellCheckedOutlinedTextField(
                    label = {
                        when (selectedZapType) {
                            ZapReceiptEvent.ZapType.PUBLIC, ZapReceiptEvent.ZapType.ANONYMOUS -> {
                                Text(text = stringRes(id = Res.string.custom_zaps_add_a_message))
                            }

                            ZapReceiptEvent.ZapType.PRIVATE -> {
                                Text(text = stringRes(id = Res.string.custom_zaps_add_a_message_private))
                            }

                            ZapReceiptEvent.ZapType.NONZAP -> {
                                Text(text = stringRes(id = Res.string.custom_zaps_add_a_message_nonzap))
                            }
                        }
                    },
                    value = postViewModel.customMessage,
                    onValueChange = { postViewModel.customMessage = it },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                        ),
                    placeholder = {
                        Text(
                            text = stringRes(id = Res.string.custom_zaps_add_a_message_example),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                )

                // Full-width send button
                ZapButton(
                    isActive = postViewModel.canSend() && !baseNote.isDraft(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onZapStarts()
                    accountViewModel.zap(
                        baseNote,
                        postViewModel.value()!! * 1000L,
                        null,
                        postViewModel.customMessage.text,
                        onError = onError,
                        onProgress = onProgress,
                        onPayViaIntent = onPayViaIntent,
                        zapType = selectedZapType,
                    )
                    onClose()
                }

                // Hand off to the on-chain dialog with the entered amount +
                // message prefilled. Disabled while the amount is empty so the
                // user can't open a sheet that immediately gates on its own
                // empty field. Hidden entirely when the on-chain wallet is off.
                if (showOnchainWallet) {
                    TextButton(
                        onClick = { sendOnchain = true },
                        enabled = postViewModel.canSend() && !baseNote.isDraft(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                    ) {
                        Text(text = stringRes(id = Res.string.send_onchain_instead))
                    }
                }
            }
        }
    }

    if (sendOnchain) {
        val zappedEventHint = remember(baseNote) { baseNote.toEventHint<Event>() }
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = {
                sendOnchain = false
                onClose()
            },
            recipientPubKey = baseNote.author?.pubkeyHex,
            zappedEvent = zappedEventHint,
            prefillAmountSats = postViewModel.value(),
            prefillComment = postViewModel.customMessage.text,
        )
    }
}

@Composable
fun ZapButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onPost: () -> Unit,
) {
    Button(
        onClick = { if (isActive) onPost() },
        shape = ButtonBorder,
        modifier = modifier,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            ),
    ) {
        Text(text = stringRes(Res.string.zap_button_label), color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayViaIntentScreen(
    paymentId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.manual_zaps), nav)
        },
    ) { pad ->
        val list = accountViewModel.tempManualPaymentCache.get(paymentId)

        if (list == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringRes(Res.string.feed_is_empty))
            }
        } else {
            LazyColumn(
                Modifier.padding(
                    16.dp,
                    pad.calculateTopPadding(),
                    16.dp,
                    pad.calculateBottomPadding(),
                ),
            ) {
                itemsIndexed(
                    accountViewModel.tempManualPaymentCache.get(paymentId) ?: emptyList(),
                    key = { _: Int, invoice: ZapPaymentHandler.Payable ->
                        invoice.invoice
                    },
                ) { index, payable ->
                    DisplayPayable(index, payable, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun DisplayPayable(
    index: Int,
    payable: ZapPaymentHandler.Payable,
    accountViewModel: AccountViewModel,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val paid = rememberSaveable(payable) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = Size10dp),
    ) {
        val payee = payable.info.user
        if (payee != null) {
            BaseUserPicture(payee, Size55dp, accountViewModel = accountViewModel)
        } else {
            DisplayBlankAuthor(size = Size55dp, accountViewModel = accountViewModel)
        }

        Spacer(modifier = DoubleHorzSpacer)

        Column(modifier = Modifier.weight(1f)) {
            if (payee != null) {
                UsernameDisplay(payee, accountViewModel = accountViewModel)
            } else {
                Text(
                    text = stringRes(id = Res.string.wallet_number, index + 1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Row {
                Text(
                    text = showAmount(BigDecimal((payable.amountMilliSats / 1000.0f).toString())),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = StdHorzSpacer)
                Text(
                    text = stringRes(id = Res.string.sats),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }

        Spacer(modifier = DoubleHorzSpacer)
        val walletLauncher = rememberWalletAppLauncher()

        PayButton(isActive = !paid.value) {
            walletLauncher.payInvoice(payable.invoice, noWalletFoundStr, { paid.value = true }) {
                accountViewModel.toastManager.toast(
                    Res.string.error_dialog_zap_error,
                    UserBasedErrorMessage(it, payable.info.user),
                )
            }
        }
    }
}

@Composable
fun PayButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onPost: () -> Unit = {},
) {
    Button(
        modifier = modifier,
        onClick = onPost,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            ),
        contentPadding = ZeroPadding,
    ) {
        if (isActive) {
            Text(text = stringRes(Res.string.pay), color = Color.White)
        } else {
            Text(text = stringRes(Res.string.paid), color = Color.White)
        }
    }
}

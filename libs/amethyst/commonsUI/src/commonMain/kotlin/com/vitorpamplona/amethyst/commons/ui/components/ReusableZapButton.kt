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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.donate_now
import com.vitorpamplona.amethyst.commons.resources.draft_note
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_zap_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_send_zaps
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.no_zap_amount_setup_long_press_to_change
import com.vitorpamplona.amethyst.commons.resources.thank_you
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.ZapButtonCallbacks
import com.vitorpamplona.amethyst.commons.ui.components.ZapButtonConfig
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ObserveZapIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.commons.ui.note.ZapIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.note.payViaIntentOrManualSplit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun ReusableZapButton(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    config: ZapButtonConfig = ZapButtonConfig(),
    callbacks: ZapButtonCallbacks = ZapButtonCallbacks(),
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    var wantsToZap by remember { mutableStateOf<ImmutableList<Long>?>(null) }
    var onchainZapAmount by remember { mutableStateOf<Long?>(null) }
    var showOnchainDialog by remember { mutableStateOf(false) }

    // Makes sure the user is loaded to get his ln address ahead of time (for DVM buttons)
    if (config.showUserFinderSubscription) {
        baseNote.author?.let { author ->
            UserFinderFilterAssemblerSubscription(author, accountViewModel)
        }
    }

    val walletLauncher = rememberWalletAppLauncher()
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }
    var zapStartingTime by remember { mutableLongStateOf(0L) }
    var hasZapped by remember { mutableStateOf(false) }

    Button(
        onClick = {
            handleZapClick(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                zapAmountChoices = config.zapAmountChoices,
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onZappingProgress = { progress ->
                    scope.launch { zappingProgress = progress }
                },
                onMultipleChoices = { options ->
                    wantsToZap = options.toImmutableList()
                },
                onError = { _, message, toUser ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, toUser)
                    }
                },
                onPayViaIntent = {
                    payViaIntentOrManualSplit(it, walletLauncher, noWalletFoundStr, accountViewModel, nav, onPaymentError = { zappingProgress = 0f })
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        wantsToZap?.let { zapAmountChoices ->
            ZapAmountChoicePopup(
                baseNote = baseNote,
                zapAmountChoices = zapAmountChoices,
                popupYOffset = config.iconSize,
                accountViewModel = accountViewModel,
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onDismiss = {
                    wantsToZap = null
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = null
                },
                onError = { _, message, user ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) { zappingProgress = it }
                },
                onPayViaIntent = {
                    payViaIntentOrManualSplit(it, walletLauncher, noWalletFoundStr, accountViewModel, nav, onPaymentError = { zappingProgress = 0f })
                },
                onOnchainAmount = { amount ->
                    wantsToZap = null
                    onchainZapAmount = amount
                    showOnchainDialog = true
                },
                onReloadNutzap = { amount ->
                    wantsToZap = null
                    navigateToReloadMint(accountViewModel, nav, baseNote, amount)
                },
            )
        }

        if (showOnchainDialog) {
            val zappedEventHint = remember(baseNote) { baseNote.toEventHint<Event>() }
            OnchainZapSendDialog(
                accountViewModel = accountViewModel,
                onDismiss = {
                    showOnchainDialog = false
                    onchainZapAmount = null
                },
                recipientPubKey = baseNote.author?.pubkeyHex,
                zappedEvent = zappedEventHint,
                prefillAmountSats = onchainZapAmount,
            )
        }

        // Zap Icon and Progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = config.iconSizeModifier,
        ) {
            if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
                Spacer(ModifierWidth3dp)

                val animatedProgress by animateFloatAsState(
                    targetValue = zappingProgress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "ZapIconIndicator",
                )

                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                    zapStartingTime,
                ) { wasZappedByLoggedInUser ->
                    CrossfadeIfEnabled(
                        targetState = wasZappedByLoggedInUser.value,
                        label = "ZapIcon",
                    ) {
                        if (it) {
                            ZappedIcon(config.iconSizeModifier)
                        } else {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = config.animationModifier,
                                strokeWidth = 2.dp,
                                color = config.grayTint,
                            )
                        }
                    }
                }
            } else {
                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                ) { wasZappedByLoggedInUser ->
                    LaunchedEffect(wasZappedByLoggedInUser.value) {
                        hasZapped = wasZappedByLoggedInUser.value
                        callbacks.onZapComplete?.invoke(wasZappedByLoggedInUser.value)

                        if (wasZappedByLoggedInUser.value && !accountViewModel.account.hasDonatedInThisVersion()) {
                            delay(1000)
                            accountViewModel.markDonatedInThisVersion()
                        }
                    }

                    CrossfadeIfEnabled(
                        targetState = wasZappedByLoggedInUser.value,
                        label = "ZapIcon",
                    ) {
                        if (it) {
                            ZappedIcon(config.iconSizeModifier)
                        } else {
                            ZapIcon(config.iconSizeModifier, config.grayTint)
                        }
                    }
                }
            }
        }

        val displayText =
            when {
                hasZapped -> config.thankYouText ?: stringRes(id = Res.string.thank_you)
                else -> config.buttonText ?: stringRes(id = Res.string.donate_now)
            }

        Text(text = displayText)
    }
}

private fun handleZapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    zapAmountChoices: List<Long>?,
    onZapStarts: () -> Unit,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: (List<Long>) -> Unit,
    onError: (String, String, User?) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toastManager.toast(
            Res.string.draft_note,
            Res.string.it_s_not_possible_to_zap_to_a_draft_note,
        )
        return
    }

    val choices = zapAmountChoices ?: accountViewModel.zapAmountChoices()

    when {
        choices.isEmpty() -> {
            accountViewModel.toastManager.toast(
                Res.string.error_dialog_zap_error,
                Res.string.no_zap_amount_setup_long_press_to_change,
            )
        }

        !accountViewModel.isWriteable() -> {
            accountViewModel.toastManager.toast(
                Res.string.error_dialog_zap_error,
                Res.string.login_with_a_private_key_to_be_able_to_send_zaps,
            )
        }

        choices.size == 1 -> {
            val amount = choices.first()

            if (amount > 1100 || zapAmountChoices != null) {
                onZapStarts()
                accountViewModel.zap(
                    baseNote,
                    amount * 1000,
                    null,
                    "",
                    showErrorIfNoLnAddress = false,
                    onError = onError,
                    onProgress = { onZappingProgress(it) },
                    onPayViaIntent = onPayViaIntent,
                )
            } else {
                onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
            }
        }

        else -> {
            if (choices.any { it > 1100 } || zapAmountChoices != null) {
                onMultipleChoices(choices)
            } else {
                onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
            }
        }
    }
}

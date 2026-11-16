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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.DEFAULT_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.commons.model.MIN_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.onchain.DustRecipientException
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendError
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapShare
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSplitter
import com.vitorpamplona.amethyst.commons.onchain.ui.labelRes
import com.vitorpamplona.amethyst.commons.onchain.ui.technicalDetail
import com.vitorpamplona.amethyst.commons.onchain.ui.userMessageText
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_scan_qr_code
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.onchain_send_amount
import com.vitorpamplona.amethyst.commons.resources.onchain_send_broadcast_no_receipt
import com.vitorpamplona.amethyst.commons.resources.onchain_send_button_amount
import com.vitorpamplona.amethyst.commons.resources.onchain_send_button_amount_split
import com.vitorpamplona.amethyst.commons.resources.onchain_send_change_recipient
import com.vitorpamplona.amethyst.commons.resources.onchain_send_close
import com.vitorpamplona.amethyst.commons.resources.onchain_send_comment_label
import com.vitorpamplona.amethyst.commons.resources.onchain_send_done
import com.vitorpamplona.amethyst.commons.resources.onchain_send_dont_split
import com.vitorpamplona.amethyst.commons.resources.onchain_send_failed_at
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_eta_fast
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_eta_normal
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_eta_slow
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_fast
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_normal
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_rate_eta
import com.vitorpamplona.amethyst.commons.resources.onchain_send_fee_slow
import com.vitorpamplona.amethyst.commons.resources.onchain_send_loading_fees
import com.vitorpamplona.amethyst.commons.resources.onchain_send_min_warning
import com.vitorpamplona.amethyst.commons.resources.onchain_send_post_author
import com.vitorpamplona.amethyst.commons.resources.onchain_send_priority
import com.vitorpamplona.amethyst.commons.resources.onchain_send_recipient
import com.vitorpamplona.amethyst.commons.resources.onchain_send_recipient_placeholder
import com.vitorpamplona.amethyst.commons.resources.onchain_send_result_change
import com.vitorpamplona.amethyst.commons.resources.onchain_send_result_fee
import com.vitorpamplona.amethyst.commons.resources.onchain_send_result_transaction
import com.vitorpamplona.amethyst.commons.resources.onchain_send_sats_amount
import com.vitorpamplona.amethyst.commons.resources.onchain_send_sats_suffix
import com.vitorpamplona.amethyst.commons.resources.onchain_send_sending
import com.vitorpamplona.amethyst.commons.resources.onchain_send_splits_label
import com.vitorpamplona.amethyst.commons.resources.onchain_send_splits_skipped
import com.vitorpamplona.amethyst.commons.resources.onchain_send_success
import com.vitorpamplona.amethyst.commons.resources.onchain_send_title
import com.vitorpamplona.amethyst.commons.resources.onchain_send_to
import com.vitorpamplona.amethyst.commons.resources.onchain_send_use_note_split
import com.vitorpamplona.amethyst.commons.resources.reload_mint_sats_amount
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.NamecoinResolutionRow
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.sats
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/** Shared with the profile Send Payment screen's on-chain rail. */
enum class FeeTier(
    val labelRes: StringResource,
    val etaLabelRes: StringResource,
) {
    SLOW(Res.string.onchain_send_fee_slow, Res.string.onchain_send_fee_eta_slow),
    NORMAL(Res.string.onchain_send_fee_normal, Res.string.onchain_send_fee_eta_normal),
    FAST(Res.string.onchain_send_fee_fast, Res.string.onchain_send_fee_eta_fast),
}

fun FeeEstimates.rateFor(tier: FeeTier): Double =
    when (tier) {
        FeeTier.SLOW -> slowSatPerVbyte
        FeeTier.NORMAL -> normalSatPerVbyte
        FeeTier.FAST -> fastSatPerVbyte
    }

/**
 * Modal bottom sheet that drives a NIP-BC onchain zap.
 *
 * Layout (top to bottom):
 *   - Title row with close button
 *   - Recipient picker — search field with inline dropdown, or selected-user chip
 *   - Amount section — quick-pick chips (reuses [AccountViewModel.zapAmountChoices])
 *     and a big sats text field
 *   - Optional comment
 *   - Fee priority — three chips with rate + ETA, in a FlowRow that wraps
 *   - Sticky bottom send button
 *
 * When [recipientPubKey] is null the user picks a recipient. When provided
 * (e.g. from a note's zap menu) the recipient is fixed and [zappedEvent]
 * attributes the zap to that event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnchainZapSendDialog(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    recipientPubKey: HexKey? = null,
    zappedEvent: EventHintBundle<out Event>? = null,
    prefillAmountSats: Long? = null,
    prefillComment: String = "",
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }
    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    var searchInput by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var amountInput by remember { mutableStateOf(prefillAmountSats?.toString().orEmpty()) }
    var comment by remember { mutableStateOf(prefillComment) }
    var feeTier by remember { mutableStateOf(FeeTier.NORMAL) }
    var fees by remember { mutableStateOf<FeeEstimates?>(null) }

    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OnchainZapSendResult?>(null) }

    // Pull pubkey-based zap splits off the zapped event. Lightning-address-only
    // splits are dropped because we can't derive a Taproot output from an
    // lnAddress — they appear in [skippedLnSplits] so the UI can warn the user
    // that those recipients won't be paid on-chain. The sender's own pubkey is
    // also dropped (zapping your own post is common; the on-chain builder
    // refuses self-pays) and duplicate pubkeys are merged.
    val senderPubKey = accountViewModel.account.signer.pubKey
    val zappedEventId = zappedEvent?.event?.id
    val rawSplits =
        remember(zappedEventId) {
            zappedEvent?.event?.zapSplitSetup().orEmpty()
        }
    val onchainSplits =
        remember(zappedEventId, senderPubKey) {
            val raw = rawSplits.filterIsInstance<ZapSplitSetup>().map { it.pubKeyHex to it.weight }
            OnchainZapSplitter.prepare(raw, senderPubKey)
        }
    val skippedLnSplits =
        remember(zappedEventId) {
            rawSplits.filterIsInstance<ZapSplitSetupLnAddress>()
        }
    var useSplits by remember(zappedEventId) { mutableStateOf(onchainSplits.isNotEmpty()) }
    val splitMode = useSplits && onchainSplits.isNotEmpty()

    // Fetch fee estimates with bounded retry. Covers two boot races:
    //  - LocalCache.onchainBackend is null briefly while AppModules wires it up
    //  - feeEstimates() throws on a flaky network
    // Without retry, the Send button would stay permanently disabled because
    // the build path needs a fee rate.
    LaunchedEffect(Unit) {
        repeat(4) { attempt ->
            if (fees != null) return@LaunchedEffect
            val backend = LocalCache.onchainBackend
            if (backend != null) {
                val newFees = runCatchingCancellable { withContext(Dispatchers.IO) { backend.feeEstimates() } }.getOrNull()
                if (newFees != null) {
                    fees = newFees
                    return@LaunchedEffect
                }
            }
            if (attempt < 3) delay(1_000L * (attempt + 1))
        }
    }

    // On-chain shares the single zap-amount list now; show only the entries
    // that clear the on-chain minimum as quick presets.
    val zapAmountChoices by accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices
        .collectAsStateWithLifecycle()
    // Presets that clear the on-chain minimum. If the user's unified list has
    // none (e.g. only small Lightning amounts), fall back to a sensible default
    // so the dialog still offers a quick-pick chip instead of an empty row.
    val presetAmounts =
        remember(zapAmountChoices) {
            zapAmountChoices.filter { it >= MIN_ONCHAIN_ZAP_SATS }.ifEmpty { listOf(DEFAULT_ONCHAIN_ZAP_SATS) }
        }

    // Mirror the dropdown's NIP-05 / Namecoin (.bit) resolution so Send can
    // enable as soon as the typed name resolves, without forcing the user to
    // tap the suggestion. Reuses the exact same path as the dropdown, so
    // bare .bit names (e.g. testls.bit) and m@testls.bit both work.
    val nip05Resolved by userSuggestions.nip05ResolutionFlow.collectAsStateWithLifecycle(initialValue = null)

    val resolvedRecipient: HexKey? =
        recipientPubKey
            ?: selectedUser?.pubkeyHex
            ?: searchInput.trim().takeIf { it.isNotEmpty() }?.let { decodePublicKeyAsHexOrNull(it) }
            ?: nip05Resolved?.pubkeyHex
    val amountSats = amountInput.trim().toLongOrNull()

    // Preview the per-recipient share allocation. Always compute the full
    // list (even when some shares would land below dust) so the UI can show
    // every recipient's amount; below-dust offenders are flagged separately
    // and gate the Send button so the user can't tap into a guaranteed
    // BUILDING-stage failure.
    val previewShares =
        remember(splitMode, onchainSplits, amountSats) {
            if (!splitMode || amountSats == null || amountSats <= 0) {
                null
            } else {
                runCatching {
                    OnchainZapSplitter.distributeUnchecked(amountSats, onchainSplits)
                }.getOrNull()
            }
        }
    val belowDustShares =
        remember(previewShares) {
            previewShares.orEmpty().filter { it.sats < OnchainZapBuilder.DUST_THRESHOLD_SATS }
        }

    val belowMinimum = amountSats != null && amountSats > 0 && amountSats < MIN_ONCHAIN_ZAP_SATS

    val canSend =
        !sending &&
            result == null &&
            (splitMode || (resolvedRecipient != null && resolvedRecipient != senderPubKey)) &&
            amountSats != null &&
            amountSats >= MIN_ONCHAIN_ZAP_SATS &&
            fees != null &&
            (!splitMode || (previewShares != null && belowDustShares.isEmpty()))

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePaddingSafe()
                    .navigationBarsPadding(),
        ) {
            Header(onClose = { if (!sending) onDismiss() })

            when (val r = result) {
                is OnchainZapSendResult.Success -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        SuccessBody(r)
                    }
                    DoneButton(label = stringRes(Res.string.onchain_send_done), onClick = onDismiss)
                }

                is OnchainZapSendResult.Failure -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        FailureBody(r)
                    }
                    DoneButton(label = stringRes(Res.string.onchain_send_close), onClick = onDismiss)
                }

                null -> {
                    if (sending) {
                        SendingState()
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp),
                        ) {
                            if (splitMode) {
                                SplitsRecipientSection(
                                    splits = onchainSplits,
                                    previewShares = previewShares,
                                    skippedLnSplits = skippedLnSplits,
                                    onDisable = { useSplits = false },
                                    accountViewModel = accountViewModel,
                                )
                            } else {
                                RecipientSection(
                                    accountViewModel = accountViewModel,
                                    recipientPubKey = recipientPubKey,
                                    userSuggestions = userSuggestions,
                                    selectedUser = selectedUser,
                                    onSelectUser = {
                                        selectedUser = it
                                        searchInput = ""
                                        userSuggestions.reset()
                                    },
                                    onClearUser = {
                                        selectedUser = null
                                        searchInput = ""
                                        userSuggestions.reset()
                                    },
                                    searchInput = searchInput,
                                    onSearchChange = { newValue ->
                                        searchInput = newValue
                                        if (newValue.length > 2) {
                                            userSuggestions.processCurrentWord(newValue)
                                        } else {
                                            userSuggestions.reset()
                                        }
                                    },
                                )
                                if (onchainSplits.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(
                                        onClick = { useSplits = true },
                                    ) {
                                        Text(pluralStringRes(Res.plurals.onchain_send_use_note_split, onchainSplits.size, onchainSplits.size))
                                    }
                                }
                            }

                            SectionSpacer()

                            AmountSection(
                                amountInput = amountInput,
                                onAmountChange = { amountInput = it },
                                presetAmounts = presetAmounts,
                                belowMinimum = belowMinimum,
                            )

                            SectionSpacer()

                            SpellCheckedOutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text(stringRes(Res.string.onchain_send_comment_label)) },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            SectionSpacer()

                            FeeSection(
                                feeTier = feeTier,
                                onFeeTierChange = { feeTier = it },
                                fees = fees,
                            )

                            Spacer(Modifier.height(20.dp))
                        }

                        SendButton(
                            enabled = canSend,
                            amountSats = amountSats,
                            splitWays = if (splitMode) onchainSplits.size else 0,
                            onClick = {
                                val amount = amountSats ?: return@SendButton
                                val feeRate = fees?.rateFor(feeTier) ?: return@SendButton
                                sending = true
                                scope.launch {
                                    val r =
                                        if (splitMode) {
                                            val shares =
                                                try {
                                                    OnchainZapSplitter.distribute(
                                                        totalSats = amount,
                                                        splits = onchainSplits,
                                                        dustThresholdSats = OnchainZapBuilder.DUST_THRESHOLD_SATS,
                                                    )
                                                } catch (e: DustRecipientException) {
                                                    sending = false
                                                    result =
                                                        OnchainZapSendResult.Failure(
                                                            stage = OnchainZapSendStage.BUILDING,
                                                            error = OnchainZapSendError.RECIPIENT_BELOW_DUST,
                                                            message = e.message ?: "A recipient share is below dust",
                                                            cause = e,
                                                        )
                                                    return@launch
                                                }
                                            accountViewModel.account.zaps.sendOnchainZapWithSplits(
                                                recipients = shares,
                                                feeRateSatPerVByte = feeRate,
                                                comment = comment.trim(),
                                                zappedEvent = zappedEvent,
                                            )
                                        } else {
                                            val recipient = resolvedRecipient ?: return@launch
                                            accountViewModel.account.zaps.sendOnchainZap(
                                                recipientPubKey = recipient,
                                                amountSats = amount,
                                                feeRateSatPerVByte = feeRate,
                                                comment = comment.trim(),
                                                zappedEvent = zappedEvent,
                                            )
                                        }
                                    sending = false
                                    result = r
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringRes(Res.string.onchain_send_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        )
        IconButton(onClick = onClose) {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.onchain_send_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipientSection(
    accountViewModel: AccountViewModel,
    recipientPubKey: HexKey?,
    userSuggestions: UserSuggestionState,
    selectedUser: User?,
    onSelectUser: (User) -> Unit,
    onClearUser: () -> Unit,
    searchInput: String,
    onSearchChange: (String) -> Unit,
) {
    SectionLabel(stringRes(Res.string.onchain_send_to))

    if (recipientPubKey != null) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(
                    userHex = recipientPubKey,
                    size = 32.dp,
                    accountViewModel = accountViewModel,
                    nav = EmptyNav(),
                )
                Text(
                    text = stringRes(Res.string.onchain_send_post_author),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        return
    }

    if (selectedUser != null) {
        SelectedRecipientChip(selectedUser, accountViewModel, onClearUser)
        return
    }

    var qrScanning by remember { mutableStateOf(false) }

    // Tight column: no extra parent spacing between the field and its
    // suggestion dropdown — the dropdown sits flush under the field.
    OutlinedTextField(
        value = searchInput,
        onValueChange = onSearchChange,
        label = { Text(stringRes(Res.string.onchain_send_recipient)) },
        placeholder = { Text(stringRes(Res.string.onchain_send_recipient_placeholder)) },
        singleLine = true,
        isError =
            searchInput.isNotBlank() &&
                searchInput.length > 50 &&
                decodePublicKeyAsHexOrNull(searchInput.trim()) == null,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { qrScanning = true }) {
                Icon(
                    painter = painterRes(Res.drawable.ic_qrcode, 3),
                    contentDescription = stringRes(id = Res.string.accessibility_scan_qr_code),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )

    if (qrScanning) {
        SimpleQrCodeScanner {
            qrScanning = false
            if (!it.isNullOrEmpty()) {
                // Profile QRs usually carry the nostr: URI prefix; the
                // resolver wants the bare npub / NIP-05.
                onSearchChange(it.removePrefix("nostr:"))
            }
        }
    }

    // Inline Namecoin lookup feedback. Local-cache suggestions can race
    // ahead of the on-chain resolution (especially when the user has
    // resolved a sibling `user@<host>.bit` earlier in the session and the
    // current query is the bare host), so we surface the in-flight state +
    // the eventual on-chain match in its own row, distinct from the
    // generic dropdown. Failures are surfaced here too.
    NamecoinResolutionRow(
        searchInput = searchInput,
        accountViewModel = accountViewModel,
        onUserResolved = onSelectUser,
    )

    if (searchInput.length > 2) {
        ShowUserSuggestionList(
            userSuggestions = userSuggestions,
            onSelect = onSelectUser,
            accountViewModel = accountViewModel,
            modifier = Modifier.heightIn(0.dp, 220.dp),
        )
    }
}

@Composable
private fun SelectedRecipientChip(
    user: User,
    accountViewModel: AccountViewModel,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserPicture(
                userHex = user.pubkeyHex,
                size = 36.dp,
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = user.toBestDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.pubkeyDisplayHex(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.onchain_send_change_recipient),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSection(
    amountInput: String,
    onAmountChange: (String) -> Unit,
    presetAmounts: List<Long>,
    belowMinimum: Boolean,
) {
    SectionLabel(stringRes(Res.string.onchain_send_amount))

    if (presetAmounts.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presetAmounts.forEach { amount ->
                SuggestionChip(
                    onClick = { onAmountChange(amount.toString()) },
                    label = { Text("⚡ ${showAmount(BigDecimal(amount))}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = amountInput,
        onValueChange = { onAmountChange(it.filter(Char::isDigit)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text("0") },
        suffix = { Text(stringRes(Res.string.onchain_send_sats_suffix), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        isError = belowMinimum,
        modifier = Modifier.fillMaxWidth(),
    )

    if (belowMinimum) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringRes(Res.string.onchain_send_min_warning, formatGrouped(MIN_ONCHAIN_ZAP_SATS)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeSection(
    feeTier: FeeTier,
    onFeeTierChange: (FeeTier) -> Unit,
    fees: FeeEstimates?,
) {
    SectionLabel(stringRes(Res.string.onchain_send_priority))

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeeTier.entries.forEach { tier ->
            val rate = fees?.rateFor(tier)
            FilterChip(
                selected = feeTier == tier,
                onClick = { onFeeTierChange(tier) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.bitcoinColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                label = {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = stringRes(tier.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text =
                                if (rate != null) {
                                    stringRes(Res.string.onchain_send_fee_rate_eta, formatRate(rate), stringRes(tier.etaLabelRes))
                                } else {
                                    stringRes(tier.etaLabelRes)
                                },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        }
    }

    if (fees == null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringRes(Res.string.onchain_send_loading_fees),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SendButton(
    enabled: Boolean,
    amountSats: Long?,
    splitWays: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.CurrencyBitcoin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        val sats = if (amountSats != null && amountSats > 0) formatGrouped(amountSats) else null
        Text(
            text =
                when {
                    sats != null && splitWays > 1 ->
                        pluralStringRes(Res.plurals.onchain_send_button_amount_split, splitWays, sats, splitWays)
                    sats != null -> stringRes(Res.string.onchain_send_button_amount, sats)
                    else -> stringRes(Res.string.send)
                },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SplitsRecipientSection(
    splits: List<Pair<HexKey, Double>>,
    previewShares: List<OnchainZapShare>?,
    skippedLnSplits: List<ZapSplitSetupLnAddress>,
    onDisable: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    SectionLabel(pluralStringRes(Res.plurals.onchain_send_splits_label, splits.size, splits.size))

    val totalWeight = splits.sumOf { it.second }
    // Index the preview by pubkey once — the splits list scan would otherwise
    // be O(N²) for the per-row sat amount lookup.
    val previewByPubKey =
        remember(previewShares) {
            previewShares?.associateBy { it.recipientPubKey }
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            splits.forEach { (pubKey, weight) ->
                val share = previewByPubKey?.get(pubKey)
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserPicture(
                        userHex = pubKey,
                        size = 28.dp,
                        accountViewModel = accountViewModel,
                        nav = EmptyNav(),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = formatWeight(weight, totalWeight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (share != null) {
                        val belowDust = share.sats < OnchainZapBuilder.DUST_THRESHOLD_SATS
                        Text(
                            text = stringRes(Res.string.reload_mint_sats_amount, formatGrouped(share.sats)),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (belowDust) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (skippedLnSplits.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        val skipped = skippedLnSplits.size
        Text(
            text = pluralStringRes(Res.plurals.onchain_send_splits_skipped, skipped, skipped),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onDisable) {
        Text(stringRes(Res.string.onchain_send_dont_split))
    }
}

private fun formatWeight(
    weight: Double,
    totalWeight: Double,
): String {
    val pct = (weight / totalWeight) * 100.0
    return if (pct >= 99.95) {
        "100%"
    } else {
        // Round to a tenth of a percent. Drop the trailing ".0" so whole
        // percentages render as "50%" instead of "50.0%".
        val tenths = (pct * 10).toLong()
        if (tenths % 10 == 0L) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
    }
}

@Composable
private fun DoneButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SendingState() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.bitcoinColor,
        )
        Text(
            text = stringRes(Res.string.onchain_send_sending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessBody(result: OnchainZapSendResult.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.bitcoinColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringRes(Res.string.onchain_send_success),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ResultRow(stringRes(Res.string.onchain_send_result_transaction), result.txid)
        ResultRow(
            stringRes(Res.string.onchain_send_result_fee),
            stringRes(Res.string.onchain_send_sats_amount, formatGrouped(result.feeSats)),
        )
        if (result.changeSats > 0) {
            ResultRow(
                stringRes(Res.string.onchain_send_result_change),
                stringRes(Res.string.onchain_send_sats_amount, formatGrouped(result.changeSats)),
            )
        }
    }
}

@Composable
private fun FailureBody(result: OnchainZapSendResult.Failure) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = result.userMessageText(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        result.technicalDetail()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.broadcastTxid?.let {
            Text(
                text = stringRes(Res.string.onchain_send_broadcast_no_receipt, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringRes(Res.string.onchain_send_failed_at, stringRes(result.stage.labelRes())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatRate(rate: Double): String = if (rate == rate.toLong().toDouble()) rate.toLong().toString() else ((rate * 10).toLong() / 10.0).toString()

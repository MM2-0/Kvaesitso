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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amount_in_sats
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.custom_zaps_add_a_message_example
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.resources.send_payment_amount
import com.vitorpamplona.amethyst.commons.resources.send_payment_done
import com.vitorpamplona.amethyst.commons.resources.send_payment_failed
import com.vitorpamplona.amethyst.commons.resources.send_payment_fixed_price
import com.vitorpamplona.amethyst.commons.resources.send_payment_from
import com.vitorpamplona.amethyst.commons.resources.send_payment_message_recipient
import com.vitorpamplona.amethyst.commons.resources.send_payment_method
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_cashu
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_clink
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_lightning
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_onchain
import com.vitorpamplona.amethyst.commons.resources.send_payment_no_methods
import com.vitorpamplona.amethyst.commons.resources.send_payment_receipt_section
import com.vitorpamplona.amethyst.commons.resources.send_payment_try_again
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as M3Icon

/**
 * The payment rails the profile Send Payment screen can drive. [routeKey] is
 * the stable string used in [com.vitorpamplona.amethyst.commons.model.navigation.Route.SendPayment]
 * to preselect a rail when navigating from a specific profile chip.
 */
enum class ProfilePaymentMethod(
    val routeKey: String,
) {
    LIGHTNING("lightning"),
    CLINK("clink"),
    ONCHAIN("onchain"),
    CASHU("cashu"),
    ;

    companion object {
        fun fromRouteKey(key: String?): ProfilePaymentMethod? = entries.firstOrNull { it.routeKey == key }
    }
}

/**
 * A selectable rail of the "Receive on" selector. [copyValue] is the rail's
 * destination (lightning address, noffer, bitcoin address, mint URL) —
 * long-pressing the chip copies it.
 */
@Immutable
data class PaymentMethodUi(
    val method: ProfilePaymentMethod,
    val copyValue: String? = null,
)

/** One entry of the zap-type selector (Public / Private / Anonymous / Non-Zap). */
@Immutable
data class ZapTypeOption(
    val type: ZapReceiptEvent.ZapType,
    val label: String,
    val explainer: String,
)

/**
 * One "Pay from" choice: an in-app wallet (NWC, CLINK debit, on-chain, cashu)
 * or the hand-off to another wallet app on the system ([isExternal]).
 * [isCashu] swaps the generic wallet icon for the Cashu mark.
 */
@Immutable
data class PaymentFromUi(
    val id: String,
    val name: String,
    val isExternal: Boolean = false,
    val isCashu: Boolean = false,
)

/**
 * In-screen payment lifecycle. The screen never closes itself on send: it walks
 * Editing → InProgress (invoice request, then payment) → Success/Failure, and
 * only the user's Done tap leaves the screen.
 */
@Immutable
sealed interface PaymentFlowStage {
    object Editing : PaymentFlowStage

    data class InProgress(
        val label: String,
        val progress: Float? = null,
    ) : PaymentFlowStage

    data class Success(
        val title: String,
        val detail: String? = null,
    ) : PaymentFlowStage

    data class Failure(
        val message: String,
    ) : PaymentFlowStage
}

private fun ProfilePaymentMethod.symbol(): MaterialSymbol =
    when (this) {
        ProfilePaymentMethod.LIGHTNING -> MaterialSymbols.Bolt
        ProfilePaymentMethod.CLINK -> MaterialSymbols.Paid
        ProfilePaymentMethod.ONCHAIN -> MaterialSymbols.CurrencyBitcoin
        ProfilePaymentMethod.CASHU -> MaterialSymbols.AccountBalanceWallet
    }

/**
 * FlowRow for the screen's chip groups with the same wrap gap as the
 * Receive-on pills. Material chips reserve a 48dp interactive height around
 * their 32dp visual, which inflates wrapped-row gaps to ~24dp; dropping the
 * enforcement keeps wrapped rows exactly 8dp apart while the chips stay
 * comfortably tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlowRow(content: @Composable FlowRowScope.() -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * Leading icon of a rail chip. Cashu uses its own vector mark (a monochrome
 * outline that must be tinted, like a Material Symbol) instead of the generic
 * wallet symbol; the other rails use Material Symbols.
 */
@Composable
private fun MethodIcon(
    method: ProfilePaymentMethod,
    tint: Color,
    modifier: Modifier,
) {
    if (method == ProfilePaymentMethod.CASHU) {
        M3Icon(
            imageVector = CustomHashTagIcons.Cashu,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    } else {
        Icon(
            symbol = method.symbol(),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfilePaymentMethod.label(): String =
    when (this) {
        ProfilePaymentMethod.LIGHTNING -> stringRes(Res.string.send_payment_method_lightning)
        ProfilePaymentMethod.CLINK -> stringRes(Res.string.send_payment_method_clink)
        ProfilePaymentMethod.ONCHAIN -> stringRes(Res.string.send_payment_method_onchain)
        ProfilePaymentMethod.CASHU -> stringRes(Res.string.send_payment_method_cashu)
    }

/**
 * Stateless body of the profile Send Payment screen. All data comes in as plain
 * values so the layout can be previewed without an account; the live screen
 * ([SendPaymentScreen]) owns the state and the actual payment calls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendPaymentContent(
    methods: ImmutableList<PaymentMethodUi>,
    selectedMethod: ProfilePaymentMethod?,
    onSelectMethod: (ProfilePaymentMethod) -> Unit,
    fromSources: ImmutableList<PaymentFromUi>?,
    selectedFromId: String?,
    onSelectFrom: (String) -> Unit,
    presetAmounts: ImmutableList<Long>,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    amountLocked: Boolean,
    amountSupportText: String?,
    amountIsError: Boolean,
    message: String,
    onMessageChange: (String) -> Unit,
    showMessageField: Boolean,
    messageLabel: String,
    zapTypes: ImmutableList<ZapTypeOption>?,
    selectedZapType: ZapReceiptEvent.ZapType,
    onZapTypeChange: (ZapReceiptEvent.ZapType) -> Unit,
    receiptNote: String?,
    stage: PaymentFlowStage,
    canSend: Boolean,
    sendLabel: String,
    onSend: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageRecipient: (() -> Unit)? = null,
    extraSection: (@Composable ColumnScope.() -> Unit)? = null,
    recipientHeader: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePaddingSafe()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        recipientHeader()

        if (methods.isEmpty()) {
            Text(
                text = stringRes(Res.string.send_payment_no_methods),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val editing = stage is PaymentFlowStage.Editing

        MethodSelector(
            methods = methods,
            selected = selectedMethod,
            enabled = editing,
            onSelect = onSelectMethod,
        )

        if (!fromSources.isNullOrEmpty()) {
            FromSelector(
                sources = fromSources,
                selectedId = selectedFromId,
                enabled = editing,
                onSelect = onSelectFrom,
            )
        }

        AmountSection(
            presetAmounts = presetAmounts,
            amountInput = amountInput,
            onAmountChange = onAmountChange,
            locked = amountLocked,
            enabled = editing,
            supportText = amountSupportText,
            isError = amountIsError,
        )

        if (showMessageField) {
            SpellCheckedOutlinedTextField(
                label = { Text(messageLabel) },
                value = message,
                onValueChange = onMessageChange,
                enabled = editing,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                placeholder = {
                    Text(
                        text = stringRes(Res.string.custom_zaps_add_a_message_example),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ReceiptSection(
            zapTypes = zapTypes,
            selectedZapType = selectedZapType,
            onZapTypeChange = onZapTypeChange,
            receiptNote = receiptNote,
            enabled = editing,
        )

        extraSection?.invoke(this)

        when (stage) {
            is PaymentFlowStage.Editing -> {
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    shape = ButtonBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = sendLabel, fontWeight = FontWeight.Bold)
                }
            }

            is PaymentFlowStage.InProgress -> ProgressCard(stage)

            is PaymentFlowStage.Success -> {
                ResultCard(
                    symbol = MaterialSymbols.CheckCircle,
                    tint = BitcoinOrange,
                    title = stage.title,
                    detail = stage.detail,
                )
                Button(
                    onClick = onDone,
                    shape = ButtonBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(Res.string.send_payment_done))
                }
            }

            is PaymentFlowStage.Failure -> {
                ResultCard(
                    symbol = MaterialSymbols.Error,
                    tint = MaterialTheme.colorScheme.error,
                    title = stringRes(Res.string.send_payment_failed),
                    detail = stage.message,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDone,
                        shape = ButtonBorder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringRes(Res.string.send_payment_done))
                    }
                    Button(
                        onClick = onRetry,
                        shape = ButtonBorder,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringRes(Res.string.send_payment_try_again))
                    }
                }
                if (onMessageRecipient != null) {
                    TextButton(
                        onClick = onMessageRecipient,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(Res.string.send_payment_message_recipient))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodSelector(
    methods: ImmutableList<PaymentMethodUi>,
    selected: ProfilePaymentMethod?,
    enabled: Boolean,
    onSelect: (ProfilePaymentMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(Res.string.send_payment_method))

        ChipFlowRow {
            methods.forEach { entry ->
                MethodChip(
                    entry = entry,
                    selected = selected == entry.method,
                    enabled = enabled,
                    onSelect = { onSelect(entry.method) },
                )
            }
        }
    }
}

/**
 * One "Receive on" chip. A custom pill rather than an M3 FilterChip because
 * the chip also carries the rail's destination: long-pressing copies
 * [PaymentMethodUi.copyValue] (lightning address, noffer, bitcoin address,
 * mint URL), and FilterChip has no long-press support.
 */
@Composable
private fun MethodChip(
    entry: PaymentMethodUi,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringRes(Res.string.copy_to_clipboard)
    val copiedMessage = stringRes(Res.string.copied_to_clipboard)

    val containerColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier =
            Modifier.combinedClickable(
                enabled = enabled,
                onClick = onSelect,
                onLongClick =
                    entry.copyValue?.let { value ->
                        {
                            scope.launch {
                                clipboard.setText(value)
                                shortNotice.show(copiedMessage)
                            }
                        }
                    },
                onLongClickLabel = entry.copyValue?.let { copyLabel },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            MethodIcon(
                method = entry.method,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = entry.method.label(),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * "Pay from" chips: the wallet that will be charged. A single entry renders
 * as a disabled chip — it still tells the user where the money comes from,
 * there's just nothing to choose. The external entry hands the invoice to
 * another wallet app on the system (which confirms on its own).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FromSelector(
    sources: ImmutableList<PaymentFromUi>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(Res.string.send_payment_from))

        ChipFlowRow {
            sources.forEach { source ->
                FilterChip(
                    selected = selectedId == source.id,
                    enabled = enabled && sources.size > 1,
                    onClick = { onSelect(source.id) },
                    leadingIcon = {
                        if (source.isCashu) {
                            M3Icon(
                                imageVector = CustomHashTagIcons.Cashu,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                symbol =
                                    if (source.isExternal) {
                                        MaterialSymbols.AutoMirrored.OpenInNew
                                    } else {
                                        MaterialSymbols.AccountBalanceWallet
                                    },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    label = { Text(source.name) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            disabledSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountSection(
    presetAmounts: ImmutableList<Long>,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    locked: Boolean,
    enabled: Boolean,
    supportText: String?,
    isError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(Res.string.send_payment_amount))

        if (!locked && presetAmounts.isNotEmpty()) {
            ChipFlowRow {
                presetAmounts.forEach { amount ->
                    SuggestionChip(
                        enabled = enabled,
                        onClick = { onAmountChange(amount.toString()) },
                        label = { Text("⚡ ${showAmount(BigDecimal(amount))}") },
                    )
                }
            }
        }

        OutlinedTextField(
            label = { Text(stringRes(Res.string.amount_in_sats)) },
            value = amountInput,
            onValueChange = { new -> onAmountChange(new.filter(Char::isDigit)) },
            enabled = enabled && !locked,
            isError = isError,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            suffix = { Text(stringRes(Res.string.sats)) },
            supportingText =
                when {
                    supportText != null -> {
                        { Text(supportText) }
                    }
                    locked -> {
                        { Text(stringRes(Res.string.send_payment_fixed_price)) }
                    }
                    else -> null
                },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptSection(
    zapTypes: ImmutableList<ZapTypeOption>?,
    selectedZapType: ZapReceiptEvent.ZapType,
    onZapTypeChange: (ZapReceiptEvent.ZapType) -> Unit,
    receiptNote: String?,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringRes(Res.string.send_payment_receipt_section))

        if (zapTypes != null) {
            ChipFlowRow {
                zapTypes.forEach { option ->
                    FilterChip(
                        selected = selectedZapType == option.type,
                        enabled = enabled,
                        onClick = { onZapTypeChange(option.type) },
                        label = { Text(option.label) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
            zapTypes.firstOrNull { it.type == selectedZapType }?.let { option ->
                Text(
                    text = option.explainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (receiptNote != null) {
            Text(
                text = receiptNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCard(stage: PaymentFlowStage.InProgress) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val progress = stage.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            Text(
                text = stage.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ResultCard(
    symbol: MaterialSymbol,
    tint: Color,
    title: String,
    detail: String?,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// Previews — fake data only; no account or network required.
// ---------------------------------------------------------------------------

private const val PREVIEW_MESSAGE_LABEL = "Add a public message"

private val previewMethods =
    persistentListOf(
        PaymentMethodUi(ProfilePaymentMethod.LIGHTNING, copyValue = "alice@walletofsatoshi.com"),
        PaymentMethodUi(ProfilePaymentMethod.CLINK, copyValue = "noffer1example"),
        PaymentMethodUi(ProfilePaymentMethod.ONCHAIN, copyValue = "bc1pexample"),
        PaymentMethodUi(ProfilePaymentMethod.CASHU, copyValue = "https://mint.example.com"),
    )

private val previewFromSources =
    persistentListOf(
        PaymentFromUi("nwc-1", "Alby Hub"),
        PaymentFromUi("debit-1", "Coinos Debit"),
        PaymentFromUi("external", "Another wallet app", isExternal = true),
    )

private val previewZapTypes =
    listOf(
        ZapTypeOption(ZapReceiptEvent.ZapType.PUBLIC, "Public", "Everybody can see the transaction and message"),
        ZapTypeOption(ZapReceiptEvent.ZapType.PRIVATE, "Private", "Sender and receiver can see each other and read the message"),
        ZapTypeOption(ZapReceiptEvent.ZapType.ANONYMOUS, "Anonymous", "Receiver does not know who sent the payment"),
        ZapTypeOption(ZapReceiptEvent.ZapType.NONZAP, "Non-Zap", "No trace on Nostr, only in Lightning"),
    ).toImmutableList()

@Composable
private fun PreviewRecipientHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.AccountBalanceWallet,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Modifier.size(40.dp),
        )
        Column {
            Text("Alice", fontWeight = FontWeight.Bold)
            Text(
                "alice@walletofsatoshi.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentLightningPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.LIGHTNING,
            onSelectMethod = {},
            fromSources = previewFromSources,
            selectedFromId = "nwc-1",
            onSelectFrom = {},
            presetAmounts = persistentListOf(100L, 500L, 1_000L, 5_000L),
            amountInput = "1000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "Thank you for all your work!",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = PREVIEW_MESSAGE_LABEL,
            zapTypes = previewZapTypes,
            selectedZapType = ZapReceiptEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = null,
            stage = PaymentFlowStage.Editing,
            canSend = true,
            sendLabel = "Pay 1,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentClinkFixedPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.CLINK,
            onSelectMethod = {},
            fromSources = previewFromSources,
            selectedFromId = "nwc-1",
            onSelectFrom = {},
            presetAmounts = persistentListOf(100L, 500L, 1_000L),
            amountInput = "2100",
            onAmountChange = {},
            amountLocked = true,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = false,
            messageLabel = "",
            zapTypes = null,
            selectedZapType = ZapReceiptEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "Direct Lightning payment — no zap receipt is published on Nostr.",
            stage = PaymentFlowStage.Editing,
            canSend = true,
            sendLabel = "Pay 2,100 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentInProgressPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.CASHU,
            onSelectMethod = {},
            fromSources = previewFromSources,
            selectedFromId = "nwc-1",
            onSelectFrom = {},
            presetAmounts = persistentListOf(100L, 500L),
            amountInput = "500",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = PREVIEW_MESSAGE_LABEL,
            zapTypes = null,
            selectedZapType = ZapReceiptEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "The nutzap event delivers the ecash itself, so it is always published.",
            stage = PaymentFlowStage.InProgress("Locking and sending ecash…", progress = 0.55f),
            canSend = false,
            sendLabel = "Pay 500 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentSuccessPreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.LIGHTNING,
            onSelectMethod = {},
            fromSources = previewFromSources,
            selectedFromId = "nwc-1",
            onSelectFrom = {},
            presetAmounts = persistentListOf(100L, 500L),
            amountInput = "1000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = PREVIEW_MESSAGE_LABEL,
            zapTypes = previewZapTypes,
            selectedZapType = ZapReceiptEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = null,
            stage = PaymentFlowStage.Success("Payment sent!", "1,000 sats to Alice"),
            canSend = false,
            sendLabel = "Pay 1,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentFailurePreview() {
    ThemeComparisonColumn {
        SendPaymentContent(
            methods = previewMethods,
            selectedMethod = ProfilePaymentMethod.ONCHAIN,
            onSelectMethod = {},
            fromSources = previewFromSources,
            selectedFromId = "nwc-1",
            onSelectFrom = {},
            presetAmounts = persistentListOf(10_000L, 50_000L),
            amountInput = "10000",
            onAmountChange = {},
            amountLocked = false,
            amountSupportText = null,
            amountIsError = false,
            message = "",
            onMessageChange = {},
            showMessageField = true,
            messageLabel = "Comment",
            zapTypes = null,
            selectedZapType = ZapReceiptEvent.ZapType.PUBLIC,
            onZapTypeChange = {},
            receiptNote = "An on-chain zap receipt is published on Nostr so the recipient can find the payment.",
            stage = PaymentFlowStage.Failure("Insufficient funds to cover the amount plus the network fee."),
            canSend = false,
            sendLabel = "Pay 10,000 sats",
            onSend = {},
            onDone = {},
            onRetry = {},
            recipientHeader = { PreviewRecipientHeader() },
        )
    }
}

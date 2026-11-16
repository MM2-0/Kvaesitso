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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.new_amount_in_sats
import com.vitorpamplona.amethyst.commons.resources.quick_zap_amounts
import com.vitorpamplona.amethyst.commons.resources.quick_zap_amounts_explainer
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.zap_payto_section
import com.vitorpamplona.amethyst.commons.resources.zap_payto_section_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_payto_toggle
import com.vitorpamplona.amethyst.commons.resources.zap_privacy_section
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_nonzap
import com.vitorpamplona.amethyst.commons.resources.zap_type_nonzap_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_private
import com.vitorpamplona.amethyst.commons.resources.zap_type_private_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_public
import com.vitorpamplona.amethyst.commons.resources.zap_type_public_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_type_section_explainer
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview(device = "spec:width=1900px,height=2340px,dpi=440")
fun UpdateZapAmountContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val vm: UpdateZapAmountViewModel = rememberViewModel { UpdateZapAmountViewModel() }
    vm.init(accountViewModel)

    ThemeComparisonRow {
        UpdateZapAmountContent(
            postViewModel = vm,
            onClose = {},
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateZapAmountContent(
    postViewModel: UpdateZapAmountViewModel,
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    trailingContent: @Composable ColumnScope.() -> Unit = {},
) {
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
        )

    val zapOptions =
        remember {
            zapTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
        }

    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .imePaddingSafe()
                .verticalScroll(rememberScrollState()),
    ) {
        // ── Section 1: Quick Zap Amounts ──────────────────────────────────────

        Text(
            text = stringRes(Res.string.quick_zap_amounts),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategoryFirstModifier,
        )
        Text(
            text = stringRes(Res.string.quick_zap_amounts_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Amount chips — long-press a chip to drag it to a new position; the X
        // removes it. animateContentSize smooths add/remove. Identity (and the
        // bounds map + drag target) is keyed by the amount value, not the slot
        // index, so reordering doesn't restart the in-flight drag gesture.
        var draggingAmount by remember { mutableStateOf<Long?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            postViewModel.amountSet.forEach { amountInSats ->
                key(amountInSats) {
                    val dragging = draggingAmount == amountInSats
                    ZapAmountPresetChip(
                        amountInSats = amountInSats,
                        onRemove = { postViewModel.removeAmount(amountInSats) },
                        modifier =
                            Modifier
                                .onGloballyPositioned { itemBounds[amountInSats] = it.boundsInParent() }
                                // pointerInput must sit OUTSIDE the graphicsLayer
                                // translation below — otherwise the layer moving under
                                // the finger distorts the per-frame drag deltas and the
                                // drag runs away / never settles.
                                .pointerInput(amountInSats) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingAmount = amountInSats
                                            dragOffset = Offset.Zero
                                        },
                                        onDragEnd = {
                                            draggingAmount = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggingAmount = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val from = draggingAmount ?: return@detectDragGesturesAfterLongPress
                                            val fromBounds = itemBounds[from] ?: return@detectDragGesturesAfterLongPress
                                            val pointer = fromBounds.center + dragOffset
                                            val targetAmount =
                                                itemBounds.entries
                                                    .firstOrNull { (amt, rect) -> amt != from && rect.contains(pointer) }
                                                    ?.key
                                            if (targetAmount != null) {
                                                val targetBounds = itemBounds[targetAmount]
                                                postViewModel.moveAmount(
                                                    postViewModel.amountSet.indexOf(from),
                                                    postViewModel.amountSet.indexOf(targetAmount),
                                                )
                                                // Keep the chip under the finger across the slot swap.
                                                if (targetBounds != null) dragOffset = pointer - targetBounds.center
                                            }
                                        },
                                    )
                                }.zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    if (dragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                    }
                                },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Add new amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                label = { Text(text = stringRes(Res.string.new_amount_in_sats)) },
                value = postViewModel.nextAmount,
                onValueChange = { postViewModel.nextAmount = it },
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
                trailingIcon = {
                    IconButton(
                        onClick = postViewModel::addAmount,
                        shape = ButtonBorder,
                        enabled = postViewModel.nextAmount.text.isNotBlank(),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AddCircle,
                            contentDescription = stringRes(Res.string.add),
                            modifier = Size20Modifier,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Section 2: Zap Privacy ────────────────────────────────────────────

        Text(
            text = stringRes(Res.string.zap_privacy_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategorySpacingModifier,
        )
        Text(
            text = stringRes(Res.string.zap_type_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSpinner(
                label = stringRes(id = Res.string.zap_type_explainer),
                placeholder =
                    zapTypes
                        .firstOrNull { it.first == accountViewModel.defaultZapType() }
                        ?.second
                        ?: zapTypes.firstOrNull()?.second
                        ?: "",
                options = zapOptions,
                onSelect = { postViewModel.selectedZapType = zapTypes[it].first },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Section 3: Pay-to hand-off ───────────────────────────────────────

        Text(
            text = stringRes(Res.string.zap_payto_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategorySpacingModifier,
        )
        Text(
            text = stringRes(Res.string.zap_payto_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { postViewModel.showPayToChip = !postViewModel.showPayToChip },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(Res.string.zap_payto_toggle),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = postViewModel.showPayToChip,
                onCheckedChange = { postViewModel.showPayToChip = it },
            )
        }

        trailingContent()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A quick-zap preset chip that mirrors the live feed chip: the amount with the
 * rails it could pay on — the amount-tier default rail in colour on the left,
 * the alternatives in monochrome — plus an X to remove it. No recipient context
 * here, so the rails are the ones a preset of this size could use (cashu,
 * Lightning, and on-chain at/above the minimum).
 */
@Composable
private fun ZapAmountPresetChip(
    amountInSats: Long,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rails = remember(amountInSats) { previewRailsFor(amountInSats) }
    // Mirrors the popup: one outlined track, the default rail + amount grouped in a
    // highlighted "thumb", the alternatives as quiet mono icons, then the remove X.
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZapRailIcon(rails.first(), colored = true)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = showAmount(BigDecimal(amountInSats)),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            rails.drop(1).forEach { rail ->
                Spacer(Modifier.width(8.dp))
                ZapRailIcon(rail, colored = false, size = 14.dp)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.note.creators.pow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pow_option_bits
import com.vitorpamplona.amethyst.commons.resources.pow_option_default_off
import com.vitorpamplona.amethyst.commons.resources.pow_option_default_on
import com.vitorpamplona.amethyst.commons.resources.pow_option_off
import com.vitorpamplona.amethyst.commons.resources.pow_settings_title
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.pow.deviceHashesPerSecond
import com.vitorpamplona.amethyst.commons.ui.pow.formatApproxDuration
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow

val POW_PRESETS = listOf(16, 20, 24, 28)

/**
 * Composer options-row button showing the NIP-13 difficulty this post will be
 * mined at: a manufacturing gear with the difficulty as a small badge when
 * mining is on, a dimmed gear when off. Tapping opens a menu to
 * raise/lower/disable mining for this post only — the account setting is
 * untouched.
 *
 * [effectiveDifficulty] is what will actually be used at send time (override
 * or account default); null/0 means the post publishes without PoW.
 * [defaultDifficulty] is what the account settings alone would produce, shown
 * in the "default" menu entry. [onSelect] receives null to follow the account
 * default, 0 to disable for this post, or a positive difficulty.
 */
@Composable
fun PowOverrideButton(
    effectiveDifficulty: Int?,
    defaultDifficulty: Int?,
    isOverridden: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = effectiveDifficulty != null && effectiveDifficulty > 0

    Box {
        HoverTooltip(stringRes(Res.string.pow_settings_title)) {
            IconButton(onClick = { expanded = true }) {
                Box(
                    Modifier
                        .height(20.dp)
                        .width(23.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Manufacturing,
                        contentDescription = stringRes(Res.string.pow_settings_title),
                        modifier = Modifier.size(18.dp).align(Alignment.BottomStart),
                        tint =
                            if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            },
                    )
                    if (isActive) {
                        Text(
                            text = effectiveDifficulty.toString(),
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // benchmarked on first open (~250 ms, cached after): each option
            // shows what it would cost on THIS device, e.g. "24 bits · ≈ 45 seconds".
            val hashRate by
                produceState<Double?>(initialValue = null) {
                    value = deviceHashesPerSecond()
                }

            @Composable
            fun eta(difficulty: Int): String? = hashRate?.let { formatApproxDuration(PoWEstimator.estimateSeconds(difficulty, it)) }

            @Composable
            fun withEta(
                label: String,
                difficulty: Int,
            ): String = eta(difficulty)?.let { "$label · $it" } ?: label

            DropdownMenuItem(
                text = {
                    Text(
                        if (defaultDifficulty != null && defaultDifficulty > 0) {
                            withEta(
                                pluralStringRes(Res.plurals.pow_option_default_on, defaultDifficulty, defaultDifficulty),
                                defaultDifficulty,
                            )
                        } else {
                            stringRes(Res.string.pow_option_default_off)
                        },
                        fontWeight = if (!isOverridden) FontWeight.Bold else null,
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringRes(Res.string.pow_option_off),
                        fontWeight = if (isOverridden && !isActive) FontWeight.Bold else null,
                    )
                },
                onClick = {
                    onSelect(0)
                    expanded = false
                },
            )
            POW_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Text(
                            withEta(pluralStringRes(Res.plurals.pow_option_bits, preset, preset), preset),
                            fontWeight = if (isOverridden && effectiveDifficulty == preset) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PowOverrideButtonPreview() {
    ThemeComparisonRow {
        Row {
            PowOverrideButton(
                effectiveDifficulty = 24,
                defaultDifficulty = 20,
                isOverridden = true,
                onSelect = {},
            )
            PowOverrideButton(
                effectiveDifficulty = null,
                defaultDifficulty = null,
                isOverridden = false,
                onSelect = {},
            )
        }
    }
}

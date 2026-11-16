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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.OtsSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.ots_explorer_api_label
import com.vitorpamplona.amethyst.commons.resources.ots_reset_to_auto_select
import com.vitorpamplona.amethyst.commons.resources.ots_settings_active_explorer
import com.vitorpamplona.amethyst.commons.resources.ots_settings_custom_url_hint
import com.vitorpamplona.amethyst.commons.resources.ots_settings_explorer_subtitle
import com.vitorpamplona.amethyst.commons.resources.ots_settings_explorer_title
import com.vitorpamplona.amethyst.commons.resources.ots_settings_known_explorers
import com.vitorpamplona.amethyst.commons.resources.ots_settings_mempool_hint
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.quartz.nip03Timestamp.BitcoinExplorerUrls

/**
 * Settings section for configuring the blockchain explorer used for OTS verification.
 *
 * Displays the currently active explorer and allows the user to provide a
 * custom Mempool-compatible REST API URL. When a custom URL is set, it takes
 * priority over the automatic Tor-aware selection.
 *
 * @param settings         Current [OtsSettings] state
 * @param isTorActive      Whether Tor is currently active (determines default explorer shown)
 * @param onSetCustomUrl   Called with the trimmed URL string when user saves a custom URL
 * @param onReset          Called when user clears the custom URL and reverts to auto-selection
 */
@Composable
fun OtsSettingsSection(
    settings: OtsSettings,
    isTorActive: Boolean,
    onSetCustomUrl: (String?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        SectionHeaderOts()

        Spacer(Modifier.height(12.dp))

        Text(
            "OpenTimestamps proofs are verified by querying a Bitcoin blockchain explorer. " +
                "By default, mempool.space is used when Tor is active, and blockstream.info otherwise. " +
                stringRes(Res.string.ots_settings_custom_url_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        ActiveExplorerDisplay(settings = settings, isTorActive = isTorActive)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        CustomExplorerInput(
            currentUrl = settings.customExplorerUrl,
            onSave = onSetCustomUrl,
        )

        if (settings.hasCustomExplorer) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) {
                    Icon(
                        MaterialSymbols.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringRes(Res.string.ots_reset_to_auto_select))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        KnownExplorersInfo()
    }
}

// ── Sub-composables ────────────────────────────────────────────────────

@Composable
private fun SectionHeaderOts() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MaterialSymbols.Search,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringRes(Res.string.ots_settings_explorer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringRes(Res.string.ots_settings_explorer_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveExplorerDisplay(
    settings: OtsSettings,
    isTorActive: Boolean,
) {
    val isCustom = settings.hasCustomExplorer
    val activeUrl =
        settings.normalizedUrl()
            ?: if (isTorActive) BitcoinExplorerUrls.MEMPOOL else BitcoinExplorerUrls.BLOCKSTREAM

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringRes(Res.string.ots_settings_active_explorer),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (isCustom) {
                Text(
                    "CUSTOM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BitcoinOrange,
                    modifier =
                        Modifier
                            .background(
                                BitcoinOrange.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    if (isTorActive) "AUTO (TOR)" else "AUTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = activeUrl,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CustomExplorerInput(
    currentUrl: String?,
    onSave: (String?) -> Unit,
) {
    var input by rememberSaveable(currentUrl) { mutableStateOf(currentUrl ?: "") }
    var validationError by remember(currentUrl) { mutableStateOf<String?>(null) }
    val kb = LocalSoftwareKeyboardController.current

    fun trySave() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            // Empty input means clear the custom URL
            validationError = null
            onSave(null)
            kb?.hide()
            return
        }
        if (!OtsSettings.isValidUrl(trimmed)) {
            validationError = "Must start with http:// or https://"
            return
        }
        validationError = null
        onSave(trimmed)
        kb?.hide()
    }

    Column {
        Text(
            "Custom explorer URL",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    validationError = null
                },
                label = { Text(stringRes(Res.string.ots_explorer_api_label)) },
                placeholder = { Text("https://mempool.space/api/") },
                singleLine = true,
                isError = validationError != null,
                supportingText =
                    validationError?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    },
                trailingIcon =
                    if (input.isNotBlank()) {
                        {
                            IconButton(onClick = {
                                input = ""
                                validationError = null
                            }) {
                                Icon(
                                    MaterialSymbols.Clear,
                                    contentDescription = stringRes(Res.string.clear),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { trySave() }),
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { trySave() },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(stringRes(Res.string.save))
            }
        }
    }
}

@Composable
private fun KnownExplorersInfo() {
    Column {
        Text(
            stringRes(Res.string.ots_settings_known_explorers),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OtsSettings.KNOWN_EXPLORERS.forEach { (url, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp),
                        ).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringRes(Res.string.ots_settings_mempool_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

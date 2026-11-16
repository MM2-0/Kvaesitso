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
package com.vitorpamplona.amethyst.commons.relays.health.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relays.health.RelayListKind
import com.vitorpamplona.amethyst.commons.relays.health.UnhealthyRelay

/**
 * Single row inside the unhealthy-relays sheet / popup. Carries three actions:
 *  - Remove (destructive, no confirmation)
 *  - Open in Relay Dashboard
 *  - Snooze 7d
 *
 * Caller resolves all human-readable strings (last-seen text, list-kind labels)
 * so platform-specific plural/i18n logic stays out of commons.
 */
@Composable
fun UnhealthyRelayRow(
    relay: UnhealthyRelay,
    lastSeenLabel: String,
    listKindLabel: (RelayListKind) -> String,
    removeLabel: String,
    openLabel: String,
    snoozeLabel: String,
    onRemove: () -> Unit,
    onOpenDashboard: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = relay.url.url,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = lastSeenLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (relay.lists.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                relay.lists.forEach { kind ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = listKindLabel(kind),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onSnooze) {
                Icon(
                    symbol = MaterialSymbols.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = snoozeLabel,
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onOpenDashboard) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = openLabel,
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = onRemove,
                colors =
                    androidx.compose.material3.ButtonDefaults
                        .textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = removeLabel,
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

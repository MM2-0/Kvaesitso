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
package com.vitorpamplona.amethyst.commons.cordn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_health_down
import com.vitorpamplona.amethyst.commons.resources.cordn_health_failures
import com.vitorpamplona.amethyst.commons.resources.cordn_health_ok
import com.vitorpamplona.amethyst.commons.resources.cordn_health_unknown
import com.vitorpamplona.cordn.client.CoordinatorHealth
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Whether a coordinator is answering.
 *
 * Losing a coordinator is not like losing a relay: relays are redundant and the
 * next one has the same events, while a coordinator is the single authority for
 * the groups it carries. So "not responding" means those conversations have
 * stopped, not that they are slower — which is why it gets a row of its own
 * rather than a dot.
 *
 * Three states, not two. [CoordinatorHealth.State.isUnknown] (nothing tried
 * yet) reads as neutral, because showing a fresh session a red marker for a
 * coordinator that is probably fine trains people to ignore the marker that
 * matters.
 */
@Composable
fun CoordinatorHealthRow(
    state: CoordinatorHealth.State,
    modifier: Modifier = Modifier,
) {
    val tint =
        when {
            state.isUnknown -> MaterialTheme.colorScheme.onSurfaceVariant
            state.isDown -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol =
                when {
                    state.isUnknown -> MaterialSymbols.HourglassEmpty
                    state.isDown -> MaterialSymbols.SyncProblem
                    else -> MaterialSymbols.CheckCircle
                },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Text(
            text =
                stringResource(
                    when {
                        state.isUnknown -> Res.string.cordn_health_unknown
                        state.isDown -> Res.string.cordn_health_down
                        else -> Res.string.cordn_health_ok
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
        // Only once it is actually down: one failed call is a network blip and
        // deserves no words at all.
        if (state.isDown) {
            Text(
                text = pluralStringResource(Res.plurals.cordn_health_failures, state.consecutiveFailures, state.consecutiveFailures),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

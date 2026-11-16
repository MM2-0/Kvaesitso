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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.collapse
import com.vitorpamplona.amethyst.commons.resources.limitations
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_supported_nips
import com.vitorpamplona.amethyst.commons.resources.relay_status_auth_failed
import com.vitorpamplona.amethyst.commons.resources.relay_status_auth_nip42
import com.vitorpamplona.amethyst.commons.resources.relay_status_auth_required
import com.vitorpamplona.amethyst.commons.resources.relay_status_authenticated
import com.vitorpamplona.amethyst.commons.resources.relay_status_authenticating
import com.vitorpamplona.amethyst.commons.resources.relay_status_buzz
import com.vitorpamplona.amethyst.commons.resources.relay_status_connected
import com.vitorpamplona.amethyst.commons.resources.relay_status_connected_authenticated
import com.vitorpamplona.amethyst.commons.resources.relay_status_connection
import com.vitorpamplona.amethyst.commons.resources.relay_status_dialect
import com.vitorpamplona.amethyst.commons.resources.relay_status_dialect_buzz
import com.vitorpamplona.amethyst.commons.resources.relay_status_dialect_vanilla
import com.vitorpamplona.amethyst.commons.resources.relay_status_disconnected
import com.vitorpamplona.amethyst.commons.resources.relay_status_expand_details
import com.vitorpamplona.amethyst.commons.resources.relay_status_host
import com.vitorpamplona.amethyst.commons.resources.relay_status_latency
import com.vitorpamplona.amethyst.commons.resources.relay_status_not_authenticated
import com.vitorpamplona.amethyst.commons.resources.relay_status_payment_required
import com.vitorpamplona.amethyst.commons.resources.relay_status_restricted_writes
import com.vitorpamplona.amethyst.commons.resources.software
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A compact, tappable header that makes **all** of one relay's live state observable in context:
 * connection (from `client.connectedRelaysFlow`), NIP-42 auth phase (from the auth coordinator),
 * the Buzz-dialect marker, and — expanded — the NIP-11 document (software, supported NIPs,
 * limitations, description) plus latency. Every signal here is a real `StateFlow`/`produceState`,
 * so the bar reflects reality as it changes rather than a one-shot snapshot.
 *
 * Reusable across the agent surfaces; the Jobs board pins it above the backlog.
 */
@Composable
fun RelayStatusBar(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
) {
    // These are global StateFlows (a change on ANY relay re-emits). Derive this relay's booleans
    // so the bar only recomposes when THIS relay's connection/auth/dialect actually changes.
    val connectedState =
        accountViewModel.account.client
            .connectedRelaysFlow()
            .collectAsStateWithLifecycle()
    val authState =
        accountViewModel.host.relayAuthState
            .collectAsStateWithLifecycle()
    val buzzState = BuzzRelayDialect.flow.collectAsStateWithLifecycle()
    val info by loadRelayInfo(relay)

    val isConnected by remember(relay) { derivedStateOf { relay in connectedState.value } }
    val phase by remember(relay) { derivedStateOf { authState.value[relay]?.phase ?: RelayAuthSnapshot.Phase.IDLE } }
    val isBuzz by remember(relay) { derivedStateOf { relay in buzzState.value } }
    val stat = remember(relay, isConnected) { accountViewModel.host.relayStats.get(relay) }

    val (dotColor, statusLabel) = health(isConnected, phase)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(dotColor, pulsing = phase == RelayAuthSnapshot.Phase.AUTHENTICATING || (isConnected && phase != RelayAuthSnapshot.Phase.AUTH_FAILED))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.name?.takeIf { it.isNotBlank() } ?: relay.url,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = dotColor,
                    )
                }
                if (isBuzz) Chip(stringRes(Res.string.relay_status_buzz), MaterialSymbols.Bolt)
                Icon(
                    symbol = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                    contentDescription = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) stringRes(Res.string.relay_status_authenticated) else stringRes(Res.string.relay_status_not_authenticated),
                    tint = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (expanded) stringRes(Res.string.collapse) else stringRes(Res.string.relay_status_expand_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoLine(stringRes(Res.string.relay_status_host), relay.url)
                    InfoLine(stringRes(Res.string.relay_status_connection), if (isConnected) stringRes(Res.string.relay_status_connected) else stringRes(Res.string.relay_status_disconnected))
                    InfoLine(stringRes(Res.string.relay_status_auth_nip42), phase.name.lowercase().replace('_', ' '))
                    InfoLine(stringRes(Res.string.relay_status_dialect), if (isBuzz) stringRes(Res.string.relay_status_dialect_buzz) else stringRes(Res.string.relay_status_dialect_vanilla))
                    if (stat.pingInMs > 0) InfoLine(stringRes(Res.string.relay_status_latency), "${stat.pingInMs} ms")
                    info.software?.takeIf { it.isNotBlank() }?.let { InfoLine(stringRes(Res.string.software), it + (info.version?.let { v -> " $v" } ?: "")) }
                    info.supported_nips?.takeIf { it.isNotEmpty() }?.let { InfoLine(stringRes(Res.string.relay_monitor_supported_nips), it.joinToString(", ")) }
                    val authRequiredLabel = stringRes(Res.string.relay_status_auth_required)
                    val paymentRequiredLabel = stringRes(Res.string.relay_status_payment_required)
                    val restrictedWritesLabel = stringRes(Res.string.relay_status_restricted_writes)
                    val limitationsLabel = stringRes(Res.string.limitations)
                    info.limitation?.let { lim ->
                        val flags =
                            buildList {
                                if (lim.auth_required == true) add(authRequiredLabel)
                                if (lim.payment_required == true) add(paymentRequiredLabel)
                                if (lim.restricted_writes == true) add(restrictedWritesLabel)
                            }
                        if (flags.isNotEmpty()) InfoLine(limitationsLabel, flags.joinToString(", "))
                    }
                    info.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDot(
    color: Color,
    pulsing: Boolean,
) {
    val alpha =
        if (pulsing) {
            val t = rememberInfiniteTransition(label = "relayDot")
            t
                .animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "relayDotAlpha",
                ).value
        } else {
            1f
        }
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun Chip(
    label: String,
    symbol: MaterialSymbol,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(13.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun health(
    isConnected: Boolean,
    phase: RelayAuthSnapshot.Phase,
): Pair<Color, String> =
    when {
        !isConnected -> MaterialTheme.colorScheme.error to stringRes(Res.string.relay_status_disconnected)
        phase == RelayAuthSnapshot.Phase.AUTH_FAILED -> MaterialTheme.colorScheme.error to stringRes(Res.string.relay_status_auth_failed)
        phase == RelayAuthSnapshot.Phase.AUTHENTICATING -> MaterialTheme.colorScheme.warningColor to stringRes(Res.string.relay_status_authenticating)
        phase == RelayAuthSnapshot.Phase.AUTHENTICATED -> MaterialTheme.colorScheme.allGoodColor to stringRes(Res.string.relay_status_connected_authenticated)
        else -> MaterialTheme.colorScheme.allGoodColor to stringRes(Res.string.relay_status_connected)
    }

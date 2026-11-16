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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_discovery_accepted_kinds
import com.vitorpamplona.amethyst.commons.resources.relay_discovery_geohash
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_ms
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_network
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_relay_type
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_requirements
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_open
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_read
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_write
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_supported_nips
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import org.jetbrains.compose.resources.stringResource

/**
 * Card for a NIP-66 relay discovery / monitor announcement: the relay URL, RTT
 * health chips (green/amber/red by latency), and its declared network, relay type,
 * requirements, supported NIPs, accepted kinds, topics, and geohashes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelayDiscoveryCard(noteEvent: RelayDiscoveryEvent) {
    val relayUrl = remember(noteEvent) { noteEvent.relay()?.displayUrl() }
    val rttOpen = remember(noteEvent) { noteEvent.rttOpen() }
    val rttRead = remember(noteEvent) { noteEvent.rttRead() }
    val rttWrite = remember(noteEvent) { noteEvent.rttWrite() }
    val networkTypes = remember(noteEvent) { noteEvent.networkTypes() }
    val networkValue = remember(networkTypes) { networkTypes.joinToString { it.code } }
    val relayTypes = remember(noteEvent) { noteEvent.relayTypes() }
    val relayTypeValue = remember(relayTypes) { relayTypes.joinToString() }
    val requirements = remember(noteEvent) { noteEvent.requirements() }
    val requirementsLocked = remember(requirements) { requirements.any { !it.negated } }
    val requirementsValue = remember(requirements) { requirements.joinToString { req -> if (req.negated) "!${req.value}" else req.value } }
    val supportedNips = remember(noteEvent) { noteEvent.supportedNips() }
    val supportedNipsValue = remember(supportedNips) { supportedNips.sorted().joinToString() }
    val acceptedKinds = remember(noteEvent) { noteEvent.acceptedKinds() }
    val acceptedKindsValue = remember(acceptedKinds) { acceptedKinds.joinToString { kind -> if (kind.negated) "!${kind.kind}" else "${kind.kind}" } }
    val topics = remember(noteEvent) { noteEvent.topics() }
    val geohashes = remember(noteEvent) { noteEvent.geohashes() }
    val geohashesValue = remember(geohashes) { geohashes.joinToString() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Relay URL header
        if (relayUrl != null) {
            Text(
                text = relayUrl,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // RTT metrics
        if (rttOpen != null || rttRead != null || rttWrite != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rttOpen?.let {
                    RttMetricChip(stringResource(Res.string.relay_monitor_rtt_open), it)
                }
                rttRead?.let {
                    RttMetricChip(stringResource(Res.string.relay_monitor_rtt_read), it)
                }
                rttWrite?.let {
                    RttMetricChip(stringResource(Res.string.relay_monitor_rtt_write), it)
                }
            }
        }

        // Network type
        if (networkTypes.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = MaterialSymbols.Language,
                label = stringResource(Res.string.relay_monitor_network),
                value = networkValue,
            )
        }

        // Relay type
        if (relayTypes.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = MaterialSymbols.Dns,
                label = stringResource(Res.string.relay_monitor_relay_type),
                value = relayTypeValue,
            )
        }

        // Requirements
        if (requirements.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = if (requirementsLocked) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                label = stringResource(Res.string.relay_monitor_requirements),
                value = requirementsValue,
            )
        }

        // Supported NIPs
        if (supportedNips.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = MaterialSymbols.Numbers,
                label = stringResource(Res.string.relay_monitor_supported_nips),
                value = supportedNipsValue,
            )
        }

        // Accepted kinds
        if (acceptedKinds.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = MaterialSymbols.Dns,
                label = stringResource(Res.string.relay_discovery_accepted_kinds),
                value = acceptedKindsValue,
            )
        }

        // Topics
        if (topics.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    MaterialSymbols.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    topics.forEach { topic ->
                        TopicChip(topic)
                    }
                }
            }
        }

        // Geohashes
        if (geohashes.isNotEmpty()) {
            DiscoveryInfoRow(
                icon = MaterialSymbols.TravelExplore,
                label = stringResource(Res.string.relay_discovery_geohash),
                value = geohashesValue,
            )
        }

        // Content description
        if (noteEvent.content.isNotBlank()) {
            Text(
                text = noteEvent.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RttMetricChip(
    label: String,
    ms: Long,
) {
    val color =
        when {
            ms < 200 -> MaterialTheme.colorScheme.allGoodColor
            ms < 500 -> MaterialTheme.colorScheme.warningColor
            else -> MaterialTheme.colorScheme.error
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.15f),
        ) {
            Text(
                text = stringResource(Res.string.relay_monitor_ms, ms.toInt()),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun TopicChip(topic: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "#$topic",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun DiscoveryInfoRow(
    icon: MaterialSymbol,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            text = value,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

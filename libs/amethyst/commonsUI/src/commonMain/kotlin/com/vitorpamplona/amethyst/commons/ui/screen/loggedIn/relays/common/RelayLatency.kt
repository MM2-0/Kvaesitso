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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.relays.health.LatencyMetric
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthStore
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencySnapshot
import com.vitorpamplona.amethyst.commons.relays.health.SlowReason
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_latency_first_result
import com.vitorpamplona.amethyst.commons.resources.relay_latency_median_of
import com.vitorpamplona.amethyst.commons.resources.relay_latency_ping
import com.vitorpamplona.amethyst.commons.resources.relay_latency_post
import com.vitorpamplona.amethyst.commons.resources.relay_latency_query
import com.vitorpamplona.amethyst.commons.resources.relay_latency_slow
import com.vitorpamplona.amethyst.commons.resources.relay_latency_slow_detail
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_ms
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource

/** The three response times shown, in the order a user thinks about them. */
private val SHOWN_METRICS = listOf(LatencyMetric.OK_ACK, LatencyMetric.EOSE, LatencyMetric.FIRST_RESULT)

private fun LatencyMetric.label(): StringResource =
    when (this) {
        LatencyMetric.OK_ACK -> Res.string.relay_latency_post
        LatencyMetric.EOSE -> Res.string.relay_latency_query
        LatencyMetric.FIRST_RESULT -> Res.string.relay_latency_first_result
        LatencyMetric.PING -> Res.string.relay_latency_ping
    }

@Composable
private fun latencyColor(ms: Int): Color =
    when {
        ms < 300 -> MaterialTheme.colorScheme.allGoodColor
        ms < 1000 -> MaterialTheme.colorScheme.warningColor
        else -> MaterialTheme.colorScheme.error
    }

/**
 * One line of median response times for a relay row ("Post 120 ms · Query 340 ms · First 210 ms"),
 * with a "Slow N×" chip when the relay lags the others. Draws nothing until there are samples.
 */
@Composable
fun RelayLatencyLine(
    relay: NormalizedRelayUrl,
    store: RelayHealthStore?,
    modifier: Modifier = Modifier,
) {
    if (store == null) return
    // Just this relay's entries: a list of relays must not recompose every row for each relay's update.
    val relaySnapshot by remember(store, relay) { store.latencySnapshots.map { it[relay] }.distinctUntilChanged() }
        .collectAsState(store.latencySnapshots.value[relay])
    val slowReason by remember(store, relay) { store.slowRelays.map { it[relay] }.distinctUntilChanged() }
        .collectAsState(store.slowRelays.value[relay])
    val snapshot = relaySnapshot ?: return
    val shown = SHOWN_METRICS.mapNotNull { metric -> snapshot.p50Of(metric)?.let { metric to it } }
    if (shown.isEmpty()) return

    val parts = shown.map { (metric, ms) -> stringRes(metric.label()) + " " + stringRes(Res.string.relay_monitor_ms, ms) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = parts.joinToString(" · "),
            fontSize = Font12SP,
            maxLines = 1,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.weight(1f, fill = false),
        )
        slowReason?.let { SlowChip(it) }
    }
}

@Composable
private fun SlowChip(reason: SlowReason) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
        Text(
            text = stringRes(Res.string.relay_latency_slow, formatDecimal(reason.multiplier, maxFractionDigits = 1)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The relay's median response times measured by this app, each with how many samples it rests
 * on, and why it counts as slow when it does. For the relay information screen.
 */
@Composable
fun RelayLatencyCard(
    snapshot: RelayLatencySnapshot,
    slow: SlowReason?,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SHOWN_METRICS.forEach { metric ->
                    val ms = snapshot.p50Of(metric) ?: return@forEach
                    LatencyChip(stringRes(metric.label()), ms, snapshot.countOf(metric))
                }
            }
            if (slow != null) {
                Text(
                    text =
                        stringRes(
                            Res.string.relay_latency_slow_detail,
                            stringRes(slow.metric.label()),
                            formatDecimal(slow.multiplier, maxFractionDigits = 1),
                            slow.relayP50Ms,
                            slow.cohortP50Ms,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LatencyChip(
    label: String,
    ms: Int,
    samples: Int,
) {
    val color = latencyColor(ms)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
            Text(
                text = stringRes(Res.string.relay_monitor_ms, ms),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Text(
            text = stringRes(Res.string.relay_latency_median_of, samples),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

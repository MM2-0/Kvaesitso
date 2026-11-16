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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.stale_relay_hint_icon_description
import com.vitorpamplona.amethyst.commons.resources.stale_relay_hint_label
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * NIP-66 stale-relay hint for replaceable/addressable events.
 *
 * Soft, read-only UX cue: when an addressable event was delivered exclusively
 * by relays whose latest NIP-66 `kind:30166` Relay Discovery monitor report is
 * older than [StaleRelayThresholds.DEFAULT_THRESHOLD_SECS] (14 days), render
 * an inline warning. The cache is populated by `RelayInfoNip66FilterSubAssembler`
 * (visited via the Relay Information screen). No network is fetched here.
 *
 * Out of scope: auto-refreshing or hiding stale content. Heuristics beyond the
 * "all delivering relays stale" check.
 */
object StaleRelayThresholds {
    /** 14 days, per the issue's behaviour spec. Hardcoded for v1. */
    const val DEFAULT_THRESHOLD_SECS: Long = 14L * TimeUtils.ONE_DAY
}

/**
 * Pure helper: given the latest monitor `created_at` timestamp for each
 * delivering relay (null when no kind:30166 has been observed for that relay),
 * decide whether the event should be flagged as stale.
 *
 * Returns true iff the set is non-empty AND every entry is null OR older than
 * `now - thresholdSecs`. The "all stale" rule keeps the hint quiet whenever at
 * least one delivering relay still has a fresh monitor heartbeat.
 */
fun isStaleByLatestMonitorReports(
    latestPerRelay: List<Long?>,
    now: Long,
    thresholdSecs: Long = StaleRelayThresholds.DEFAULT_THRESHOLD_SECS,
): Boolean {
    if (latestPerRelay.isEmpty()) return false
    val cutoff = now - thresholdSecs
    return latestPerRelay.all { it == null || it < cutoff }
}

@Composable
fun StaleRelayHint(
    baseNote: Note,
    modifier: Modifier = Modifier,
) {
    val event = baseNote.event ?: return
    if (event !is AddressableEvent) return

    // Reactively track the delivering relay set. Notes pick up extra relays as
    // they are observed elsewhere; reuse the same flow `RelayListBox` uses so
    // the hint converges on the latest set.
    val noteRelays by baseNote
        .flow()
        .relays.stateFlow
        .collectAsStateWithLifecycle()

    val relays = noteRelays.note.relays
    if (relays.isEmpty()) return

    val combinedFlow: Flow<List<Long?>> =
        remember(relays) {
            if (relays.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(relays.map { latestRelayDiscoveryCreatedAt(it) }) { values ->
                    values.toList()
                }
            }
        }

    val latestPerRelay by combinedFlow.collectAsState(initial = emptyList())

    if (latestPerRelay.isEmpty()) return

    val isStale = isStaleByLatestMonitorReports(latestPerRelay, TimeUtils.now())
    if (!isStale) return

    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = stringRes(id = Res.string.stale_relay_hint_icon_description),
            tint = MaterialTheme.colorScheme.warningColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringRes(id = Res.string.stale_relay_hint_label),
            color = MaterialTheme.colorScheme.warningColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Emits the most recent `kind:30166` `created_at` we have cached for [relay],
 * or `null` while none has been observed. The underlying `LocalCache` filter
 * subscription is shared with the Relay Information screen.
 */
private fun latestRelayDiscoveryCreatedAt(relay: NormalizedRelayUrl): Flow<Long?> {
    val filter =
        Filter(
            kinds = listOf(RelayDiscoveryEvent.KIND),
            tags = mapOf("d" to listOf(relay.url)),
            limit = 1,
        )
    return LocalCache.observeEvents<RelayDiscoveryEvent>(filter).map { events ->
        events.maxOfOrNull { it.createdAt }
    }
}

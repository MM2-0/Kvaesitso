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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accepts_up_to
import com.vitorpamplona.amethyst.commons.resources.access_control
import com.vitorpamplona.amethyst.commons.resources.admission
import com.vitorpamplona.amethyst.commons.resources.all_countries
import com.vitorpamplona.amethyst.commons.resources.all_languages
import com.vitorpamplona.amethyst.commons.resources.amount_in_bits
import com.vitorpamplona.amethyst.commons.resources.and_more
import com.vitorpamplona.amethyst.commons.resources.auth_required
import com.vitorpamplona.amethyst.commons.resources.connectivity
import com.vitorpamplona.amethyst.commons.resources.contact
import com.vitorpamplona.amethyst.commons.resources.content_size
import com.vitorpamplona.amethyst.commons.resources.countries
import com.vitorpamplona.amethyst.commons.resources.discards_older_than
import com.vitorpamplona.amethyst.commons.resources.duplicated_post
import com.vitorpamplona.amethyst.commons.resources.errors
import com.vitorpamplona.amethyst.commons.resources.event_retention
import com.vitorpamplona.amethyst.commons.resources.fees_and_payments
import com.vitorpamplona.amethyst.commons.resources.languages
import com.vitorpamplona.amethyst.commons.resources.limitations
import com.vitorpamplona.amethyst.commons.resources.manage
import com.vitorpamplona.amethyst.commons.resources.max_content_length
import com.vitorpamplona.amethyst.commons.resources.max_filters_per_sub
import com.vitorpamplona.amethyst.commons.resources.max_limit_events_returning
import com.vitorpamplona.amethyst.commons.resources.max_message_length
import com.vitorpamplona.amethyst.commons.resources.max_subid_length
import com.vitorpamplona.amethyst.commons.resources.max_subs
import com.vitorpamplona.amethyst.commons.resources.maximum_event_tags
import com.vitorpamplona.amethyst.commons.resources.minimum_pow
import com.vitorpamplona.amethyst.commons.resources.minimum_prefix
import com.vitorpamplona.amethyst.commons.resources.no
import com.vitorpamplona.amethyst.commons.resources.not_available_acronym
import com.vitorpamplona.amethyst.commons.resources.owner
import com.vitorpamplona.amethyst.commons.resources.payment_required
import com.vitorpamplona.amethyst.commons.resources.payments_url
import com.vitorpamplona.amethyst.commons.resources.policies_and_links
import com.vitorpamplona.amethyst.commons.resources.posting_policy
import com.vitorpamplona.amethyst.commons.resources.privacy_policy
import com.vitorpamplona.amethyst.commons.resources.publication
import com.vitorpamplona.amethyst.commons.resources.relay_active_subscriptions
import com.vitorpamplona.amethyst.commons.resources.relay_count_subscriptions
import com.vitorpamplona.amethyst.commons.resources.relay_error_messages
import com.vitorpamplona.amethyst.commons.resources.relay_filter_authors
import com.vitorpamplona.amethyst.commons.resources.relay_filter_ids
import com.vitorpamplona.amethyst.commons.resources.relay_filter_limit
import com.vitorpamplona.amethyst.commons.resources.relay_filter_since
import com.vitorpamplona.amethyst.commons.resources.relay_filter_until
import com.vitorpamplona.amethyst.commons.resources.relay_groups_button
import com.vitorpamplona.amethyst.commons.resources.relay_info_filter_count
import com.vitorpamplona.amethyst.commons.resources.relay_info_filter_number
import com.vitorpamplona.amethyst.commons.resources.relay_info_filter_search
import com.vitorpamplona.amethyst.commons.resources.relay_latency_title
import com.vitorpamplona.amethyst.commons.resources.relay_members
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_ms
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_network
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_relay_type
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_reports
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_requirements
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_open
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_read
import com.vitorpamplona.amethyst.commons.resources.relay_monitor_rtt_write
import com.vitorpamplona.amethyst.commons.resources.relay_notice
import com.vitorpamplona.amethyst.commons.resources.relay_outbox_events
import com.vitorpamplona.amethyst.commons.resources.relay_req_subscriptions
import com.vitorpamplona.amethyst.commons.resources.restricted_writes
import com.vitorpamplona.amethyst.commons.resources.see_relay_feed
import com.vitorpamplona.amethyst.commons.resources.self
import com.vitorpamplona.amethyst.commons.resources.software
import com.vitorpamplona.amethyst.commons.resources.spam
import com.vitorpamplona.amethyst.commons.resources.subscription
import com.vitorpamplona.amethyst.commons.resources.supported_grasps
import com.vitorpamplona.amethyst.commons.resources.supports
import com.vitorpamplona.amethyst.commons.resources.target_audience
import com.vitorpamplona.amethyst.commons.resources.terms_and_conditions
import com.vitorpamplona.amethyst.commons.resources.time_in_the_future
import com.vitorpamplona.amethyst.commons.resources.time_in_the_past
import com.vitorpamplona.amethyst.commons.resources.topics
import com.vitorpamplona.amethyst.commons.resources.used_by
import com.vitorpamplona.amethyst.commons.resources.version
import com.vitorpamplona.amethyst.commons.resources.yes
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.appendLink
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.graspLink
import com.vitorpamplona.amethyst.commons.ui.note.nipLink
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.KindChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayLatencyCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.datasource.RelayInfoNip66FilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Height25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size100dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.util.timeDiffAgoShortish
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.ErrorDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.IRelayDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.IRelayDebugMessageText
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.NoticeDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.SpamDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val HTTPS_PREFIX = "https://"

@Composable
fun RelayInformationScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let {
        RelayInformationScreen(
            relay = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayInformationScreen(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                actions = {},
                title = {
                    Text(
                        relay.displayUrl(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row {
                        Spacer(modifier = StdHorzSpacer)
                        BackButton(
                            onPress = nav::popBack,
                        )
                    }
                },
            )
        },
    ) { pad ->
        RelayInfoNip66FilterAssemblerSubscription(relay, accountViewModel)

        val relayInfo by loadRelayInfo(relay)

        val discoveryEvents by loadRelayDiscoveryEvents(relay)

        val messages =
            remember(relay) {
                accountViewModel.host.relayStats
                    .get(url = relay)
                    .messages
                    .snapshot()
                    .values
                    .sortedByDescending { it.time }
                    .toImmutableList()
            }

        RelayInformationBody(relay, relayInfo, discoveryEvents, accountViewModel.host.relayStats.get(relay), messages, pad, accountViewModel, nav)
    }
}

@Composable
fun RelayInformationBody(
    relay: NormalizedRelayUrl,
    relayInfo: Nip11RelayInformation,
    discoveryEvents: ImmutableList<RelayDiscoveryEvent>,
    relayStats: RelayStat,
    messages: ImmutableList<IRelayDebugMessage>,
    pad: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val activeReqs = remember(relay) { accountViewModel.account.client.activeRequests(relay) }
    val activeCounts = remember(relay) { accountViewModel.account.client.activeCounts(relay) }
    val activeOutbox = remember(relay) { accountViewModel.account.client.activeOutboxCache(relay) }

    val healthStore = accountViewModel.host.relayHealth
    val latencySnapshots = healthStore?.latencySnapshots?.collectAsState()
    val slowRelays = healthStore?.slowRelays?.collectAsState()
    val latency = latencySnapshots?.value?.get(relay)?.takeIf { it.samples.isNotEmpty() }
    val slow = slowRelays?.value?.get(relay)

    val usedBy =
        remember(relay) {
            accountViewModel.account.declaredFollowsPerUsingRelay.value[relay]?.mapNotNull { hex ->
                LocalCache.checkGetOrCreateUser(hex)
            } ?: emptyList()
        }

    LazyColumn(
        modifier =
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Header Section
        item {
            RelayHeader(relay, relayStats, relayInfo, accountViewModel, nav)
        }

        val targetAudience =
            relayInfo.tags != null ||
                relayInfo.language_tags != null ||
                relayInfo.relay_countries != null

        if (targetAudience) {
            item { SectionHeader(stringRes(Res.string.target_audience)) }
            item { TargetAudienceCard(relayInfo, nav) }
        }

        relayInfo.pubkey?.let {
            item {
                SectionHeader(stringRes(Res.string.owner))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        relayInfo.self?.let {
            item {
                SectionHeader(stringRes(Res.string.self))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        item { SectionHeader(stringRes(Res.string.policies_and_links)) }
        item { PoliciesCard(relayInfo) }

        relayInfo.fees?.let { fees ->
            item { SectionHeader(stringRes(Res.string.fees_and_payments)) }
            item { FeesCard(fees, relayInfo.payments_url) }
        }

        relayInfo.limitation?.let {
            item { SectionHeader(stringRes(Res.string.limitations)) }
            item { LimitationsCard(it) }
        }

        val atLeastOneSoftware =
            relayInfo.software != null ||
                relayInfo.version != null ||
                relayInfo.supported_grasps != null ||
                relayInfo.supported_nips != null

        if (atLeastOneSoftware) {
            item { SectionHeader(stringRes(Res.string.software)) }
            item { SoftwareCard(relayInfo) }
        }

        if (latency != null) {
            item { SectionHeader(stringRes(Res.string.relay_latency_title)) }
            item { RelayLatencyCard(latency, slow) }
        }

        if (discoveryEvents.isNotEmpty()) {
            item { SectionHeader(stringRes(Res.string.relay_monitor_reports)) }
            items(discoveryEvents, key = { it.addressTag() }) { event ->
                RelayMonitorReportCard(event, accountViewModel, nav)
            }
        }

        if (usedBy.isNotEmpty()) {
            item {
                SectionHeader(stringRes(Res.string.used_by))
            }
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    FlowRow(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                        usedBy.take(30).forEach {
                            UserPicture(
                                user = it,
                                size = Size25dp,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                        if (usedBy.size > 30) {
                            Box(contentAlignment = Alignment.Center, modifier = Height25Modifier) {
                                Text(
                                    text = stringRes(Res.string.and_more, usedBy.size - 30),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active subscriptions and outbox section
        val hasReqs = activeReqs.isNotEmpty()
        val hasCounts = activeCounts.isNotEmpty()
        val hasOutbox = activeOutbox.isNotEmpty()

        if (hasReqs || hasCounts || hasOutbox) {
            item { SectionHeader(stringRes(Res.string.relay_active_subscriptions)) }

            if (hasReqs) {
                item {
                    Text(
                        text = stringRes(Res.string.relay_req_subscriptions, activeReqs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(activeReqs.entries.toList(), key = { "req_${it.key}" }) { (subId, filters) ->
                    SubscriptionCard(subId = subId, filters = filters)
                    Spacer(modifier = StdVertSpacer)
                }
            }

            if (hasCounts) {
                item {
                    Text(
                        text = stringRes(Res.string.relay_count_subscriptions, activeCounts.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp, top = if (hasReqs) 8.dp else 0.dp),
                    )
                }
                items(activeCounts.entries.toList(), key = { "count_${it.key}" }) { (subId, filters) ->
                    SubscriptionCard(subId = subId, filters = filters)
                    Spacer(modifier = StdVertSpacer)
                }
            }

            if (hasOutbox) {
                item {
                    Text(
                        text = stringRes(Res.string.relay_outbox_events, activeOutbox.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 4.dp, top = if (hasReqs || hasCounts) 8.dp else 0.dp),
                    )
                }
                item {
                    OutboxEventsCard(eventIds = activeOutbox)
                }
            }
        }

        item {
            SectionHeader(stringRes(Res.string.relay_error_messages))
        }

        items(messages) { msg ->
            RenderDebugMessage(msg)

            Spacer(modifier = StdVertSpacer)
        }
    }
}

// ---------------------------------------------------------------------------
// Active subscriptions + outbox display
// ---------------------------------------------------------------------------

@Composable
private fun FilterAttributeChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
        )
    }
}

@Composable
private fun FilterVisual(
    filter: Filter,
    index: Int,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringRes(Res.string.relay_info_filter_number, index + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Kinds
                filter.kinds?.forEach { kind ->
                    KindChip(kind)
                }

                // Authors
                filter.authors?.let { authors ->
                    if (authors.isNotEmpty()) {
                        FilterAttributeChip(
                            text = "👤 ${stringRes(Res.string.relay_filter_authors, authors.size)}",
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                // IDs
                filter.ids?.let { ids ->
                    if (ids.isNotEmpty()) {
                        FilterAttributeChip(
                            text = "🆔 ${stringRes(Res.string.relay_filter_ids, ids.size)}",
                        )
                    }
                }

                // Tags
                filter.tags?.forEach { (tagName, values) ->
                    if (values.isNotEmpty()) {
                        FilterAttributeChip(
                            text =
                                when {
                                    values.size == 1 -> {
                                        if (values[0].length > 8) {
                                            "#$tagName:${values[0].take(8)}..."
                                        } else {
                                            "#$tagName:${values[0]}"
                                        }
                                    }

                                    else -> {
                                        "#$tagName ×${values.size}"
                                    }
                                },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // tagsAll
                filter.tagsAll?.forEach { (tagName, values) ->
                    if (values.isNotEmpty()) {
                        FilterAttributeChip(
                            text =
                                when {
                                    values.size == 1 -> {
                                        if (values[0].length > 8) {
                                            "&$tagName:${values[0].take(8)}..."
                                        } else {
                                            "&$tagName:${values[0]}"
                                        }
                                    }

                                    else -> {
                                        "&$tagName ×${values.size}"
                                    }
                                },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // Since
                filter.since?.let { since ->
                    FilterAttributeChip(
                        text = stringRes(Res.string.relay_filter_since, timeAgoNoDot(since)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Until
                filter.until?.let { until ->
                    FilterAttributeChip(
                        text = stringRes(Res.string.relay_filter_until, timeAgoNoDot(until)),
                    )
                }

                // Limit
                filter.limit?.let { limit ->
                    FilterAttributeChip(
                        text = stringRes(Res.string.relay_filter_limit, limit),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }

                // Search
                filter.search?.let { search ->
                    if (search.isNotEmpty()) {
                        FilterAttributeChip(
                            text = stringRes(Res.string.relay_info_filter_search, search.take(20) + if (search.length > 20) "…" else ""),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subId: String,
    filters: List<Filter>,
) {
    val displayId =
        if (subId.length > 28) {
            subId.take(22) + "…" + subId.takeLast(4)
        } else {
            subId
        }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayId,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringRes(Res.string.relay_info_filter_count, filters.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            filters.forEachIndexed { index, filter ->
                FilterVisual(filter = filter, index = index)
            }
        }
    }
}

@Composable
private fun OutboxEventsCard(eventIds: Set<HexKey>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            eventIds.forEach { eventId ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = eventId.take(8) + "…" + eventId.takeLast(4),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDebugMessage(msg: IRelayDebugMessage) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Timestamp with Monospace font for alignment
            Text(
                text = timeAgoNoDot(msg.time),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            // Type Tag
            Text(
                text =
                    when (msg) {
                        is ErrorDebugMessage -> stringRes(Res.string.errors)
                        is NoticeDebugMessage -> stringRes(Res.string.relay_notice)
                        is SpamDebugMessage -> stringRes(Res.string.spam)
                    },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                color =
                    when (msg) {
                        is ErrorDebugMessage -> MaterialTheme.colorScheme.error
                        is NoticeDebugMessage -> MaterialTheme.colorScheme.primary
                        is SpamDebugMessage -> MaterialTheme.colorScheme.outline
                    },
            )
        }

        when (msg) {
            is IRelayDebugMessageText -> {
                // ErrorDebugMessage and NoticeDebugMessage both carry plain-text bodies and
                // render identically here; the label/colour above already distinguishes them.
                SelectionContainer {
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            is SpamDebugMessage -> {
                SelectionContainer {
                    val uri = LocalUriHandler.current
                    val start = stringRes(Res.string.duplicated_post)
                    Text(
                        text =
                            remember {
                                buildAnnotatedString {
                                    append(start)
                                    append(" ")
                                    appendLink(msg.link1) {
                                        runCatching {
                                            uri.openUri(msg.link1)
                                        }
                                    }
                                    appendLink(msg.link2) {
                                        runCatching {
                                            uri.openUri(msg.link2)
                                        }
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayOwnerInformation(
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = userHex) { loadedUser ->
        CrossfadeIfEnabled(loadedUser) {
            if (it != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    UserCompose(
                        baseUser = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayHeader(
    relay: NormalizedRelayUrl,
    relayStats: RelayStat,
    relayInfo: Nip11RelayInformation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = SpacedBy10dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        RenderRelayIcon(
            displayUrl = relay.displayUrl(),
            iconUrl = relayInfo.icon,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            pingInMs = relayStats.pingInMs,
            iconModifier =
                Modifier
                    .size(Size100dp)
                    .clip(shape = CircleShape),
        )
        Text(
            text = relayInfo.description ?: relay.displayUrl(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            OutlinedButton(
                shape = ButtonBorder,
                onClick = { nav.nav(Route.RelayFeed(url = relay.url)) },
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Feed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringRes(Res.string.see_relay_feed), maxLines = 1)
            }

            if (supportsNip29(relayInfo.supported_nips)) {
                OutlinedButton(
                    onClick = { nav.nav(Route.RelayGroupServer(relay.url)) },
                    shape = ButtonBorder,
                ) {
                    Icon(
                        MaterialSymbols.Groups,
                        contentDescription = stringRes(Res.string.relay_groups_button),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(Res.string.relay_groups_button), maxLines = 1)
                }
            }

            if (supportsNip43(relayInfo.supported_nips)) {
                OutlinedButton(
                    onClick = { nav.nav(Route.RelayMembers(relay.url)) },
                    shape = ButtonBorder,
                ) {
                    Icon(
                        MaterialSymbols.People,
                        contentDescription = stringRes(Res.string.relay_members),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(Res.string.relay_members), maxLines = 1)
                }
            }

            if (Nip86Client.supportsNip86(relayInfo.supported_nips)) {
                OutlinedButton(
                    onClick = { nav.nav(Route.RelayManagement(relay.url)) },
                    shape = ButtonBorder,
                ) {
                    Icon(
                        MaterialSymbols.Settings,
                        contentDescription = stringRes(Res.string.manage),
                        modifier = Height25Modifier,
                    )
                }
            }
        }
    }
}

fun supportsNip29(supportedNips: List<String>?): Boolean = supportedNips?.any { it == "29" } == true

fun supportsNip43(supportedNips: List<String>?): Boolean = supportedNips?.any { it == "43" } == true

@Composable
fun FeesCard(
    fees: Nip11RelayInformation.RelayInformationFees,
    payUrl: String?,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            fees.admission?.forEach {
                FeeRow(stringRes(Res.string.admission), it)
            }
            fees.subscription?.forEach {
                FeeRow(stringRes(Res.string.subscription), it)
            }
            fees.publication?.forEach {
                FeeRow(stringRes(Res.string.publication), it)
            }
            payUrl?.let {
                val uri = LocalUriHandler.current
                ClickableInfoRow(MaterialSymbols.Payment, stringRes(Res.string.payments_url), it.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }
        }
    }
}

@Composable
fun LimitationsCard(lim: Nip11RelayInformation.RelayInformationLimitation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val atLeastOneAccessControl =
                lim.auth_required != null ||
                    lim.payment_required != null ||
                    lim.restricted_writes != null ||
                    lim.min_pow_difficulty != null ||
                    lim.min_prefix != null

            if (atLeastOneAccessControl) {
                Column {
                    Text(stringRes(Res.string.access_control), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val yes = stringRes(Res.string.yes)
                    val no = stringRes(Res.string.no)

                    lim.auth_required?.let {
                        InfoRow(MaterialSymbols.History, stringRes(Res.string.auth_required), if (it) yes else no)
                    }
                    lim.payment_required?.let {
                        InfoRow(MaterialSymbols.Lock, stringRes(Res.string.payment_required), if (it) yes else no)
                    }
                    lim.restricted_writes?.let {
                        InfoRow(MaterialSymbols.EditOff, stringRes(Res.string.restricted_writes), if (it) yes else no)
                    }

                    val minPoW = lim.min_pow_difficulty

                    if (minPoW != null && minPoW > 0) {
                        InfoRow(MaterialSymbols.Manufacturing, stringRes(Res.string.minimum_pow), stringRes(Res.string.amount_in_bits, minPoW))
                    } else {
                        lim.min_prefix?.let {
                            if (it > 0) {
                                InfoRow(MaterialSymbols.Key, stringRes(Res.string.minimum_prefix), stringRes(Res.string.amount_in_bits, it * 8))
                            }
                        }
                    }
                }
            }

            val atLeastOneConnectivity =
                lim.max_message_length.isNotNullAndNotZero() ||
                    lim.max_subscriptions.isNotNullAndNotZero() ||
                    lim.max_filters.isNotNullAndNotZero() ||
                    lim.max_limit.isNotNullAndNotZero() ||
                    lim.default_limit.isNotNullAndNotZero() ||
                    lim.max_subid_length.isNotNullAndNotZero()

            if (atLeastOneConnectivity) {
                Column {
                    Text(stringRes(Res.string.connectivity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_message_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Message, stringRes(Res.string.max_message_length), "${it / 1024} kb")
                    }
                    lim.max_subscriptions?.let {
                        InfoRow(MaterialSymbols.Dns, stringRes(Res.string.max_subs), it.toString())
                    }
                    lim.max_filters?.let {
                        InfoRow(MaterialSymbols.FilterAlt, stringRes(Res.string.max_filters_per_sub), it.toString())
                    }
                    lim.max_limit?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.List, stringRes(Res.string.max_limit_events_returning), it.toString())
                    }
                    lim.default_limit?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.List, stringRes(Res.string.max_limit_events_returning), it.toString())
                    }
                    lim.max_subid_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Label, stringRes(Res.string.max_subid_length), it.toString())
                    }
                }
            }

            val atLeastOneContentSize =
                lim.max_event_tags != null ||
                    lim.max_content_length != null

            if (atLeastOneContentSize) {
                Column {
                    Text(stringRes(Res.string.content_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_event_tags?.let {
                        InfoRow(MaterialSymbols.Tag, stringRes(Res.string.maximum_event_tags), it.toString())
                    }

                    lim.max_content_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Article, stringRes(Res.string.max_content_length), "${it / 1024} kb")
                    }
                }
            }

            val atLeastOneRestriction =
                lim.created_at_lower_limit.isNotNullAndNotZero() ||
                    lim.created_at_upper_limit.isNotNullAndNotZero()

            if (atLeastOneRestriction) {
                Column {
                    Text(stringRes(Res.string.event_retention), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.created_at_lower_limit?.let {
                        if (it > 0) {
                            InfoRow(MaterialSymbols.History, stringRes(Res.string.discards_older_than), stringRes(Res.string.time_in_the_past, timeDiffAgoShortish(it)))
                        }
                    }
                    lim.created_at_upper_limit?.let {
                        if (it > 0) {
                            InfoRow(MaterialSymbols.History, stringRes(Res.string.accepts_up_to), stringRes(Res.string.time_in_the_future, timeDiffAgoShortish(it)))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
fun Int?.isNotNullAndNotZero(): Boolean {
    contract {
        returns(true) implies (this@isNotNullAndNotZero != null)
    }

    return this != null && this != 0
}

@Composable
fun SoftwareCard(relayInfo: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relayInfo.software?.let {
                if (it.contains(HTTPS_PREFIX)) {
                    ClickableInfoRow(MaterialSymbols.Code, stringRes(Res.string.software), it.removePrefix("git+https://").removePrefix(HTTPS_PREFIX)) {
                        runCatching {
                            uri.openUri(it.removePrefix("git+"))
                        }
                    }
                } else {
                    InfoRow(MaterialSymbols.Code, stringRes(Res.string.software), it)
                }
            }

            relayInfo.version?.let {
                InfoRow(MaterialSymbols.Storage, stringRes(Res.string.version), it)
            }

            relayInfo.supported_nips?.let { nips ->
                Text(
                    stringRes(Res.string.supports),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nips.forEach { nip ->
                        val nipStr = nip.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(nipLink(nipStr))
                                }
                            },
                            label = {
                                Text(nipStr)
                            },
                        )
                    }
                }
            }

            relayInfo.supported_grasps?.let { grasps ->
                Text(
                    stringRes(Res.string.supported_grasps),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grasps.forEach { grasp ->
                        val graspStr = grasp.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(graspLink(graspStr))
                                }
                            },
                            label = {
                                Text(graspStr)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAudienceCard(
    relay: Nip11RelayInformation,
    nav: INav,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            relay.tags?.let { tags ->
                if (tags.size > 2) {
                    Column {
                        Text(stringRes(Res.string.topics), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { nav.nav(Route.Hashtag(tag)) },
                                    label = {
                                        Text(tag)
                                    },
                                )
                            }
                        }
                    }
                } else if (tags.isNotEmpty()) {
                    InfoRow(MaterialSymbols.Topic, stringRes(Res.string.topics), tags.joinToString())
                }
            }
            relay.relay_countries?.let { countries ->
                val allCountries = stringRes(Res.string.all_countries)
                if (countries.size > 2) {
                    Column {
                        Text(stringRes(Res.string.countries), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            countries.forEach { country ->
                                if (country == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allCountries)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(country)
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (countries.isNotEmpty()) {
                    InfoRow(
                        MaterialSymbols.Language,
                        stringRes(Res.string.countries),
                        countries.joinToString {
                            if (it == "*") allCountries else it
                        },
                    )
                }
            }
            relay.language_tags?.let { languages ->
                val allLang = stringRes(Res.string.all_languages)
                if (languages.size > 2) {
                    Column {
                        Text(stringRes(Res.string.languages), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languages.forEach { lang ->
                                if (lang == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allLang)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(lang)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (languages.isNotEmpty()) {
                    InfoRow(
                        MaterialSymbols.Translate,
                        stringRes(Res.string.languages),
                        languages.joinToString {
                            if (it == "*") allLang else it
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PoliciesCard(relay: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relay.contact?.let {
                if (it.contains("@")) {
                    ClickableInfoRow(MaterialSymbols.AutoMirrored.ContactSupport, stringRes(Res.string.contact), it) {
                        runCatching {
                            uri.openUri("mailto:$it")
                        }
                    }
                } else {
                    InfoRow(MaterialSymbols.AutoMirrored.ContactSupport, stringRes(Res.string.contact), it)
                }
            }

            relay.posting_policy?.let {
                ClickableInfoRow(MaterialSymbols.EditNote, stringRes(Res.string.posting_policy), it) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }

            val pp = relay.privacy_policy

            if (pp != null) {
                ClickableInfoRow(MaterialSymbols.PrivacyTip, stringRes(Res.string.privacy_policy), pp.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(pp)
                    }
                }
            } else {
                InfoRow(MaterialSymbols.PrivacyTip, stringRes(Res.string.privacy_policy), stringRes(Res.string.not_available_acronym))
            }

            val ts = relay.terms_of_service
            if (ts != null) {
                ClickableInfoRow(MaterialSymbols.Gavel, stringRes(Res.string.terms_and_conditions), ts.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(ts)
                    }
                }
            } else {
                InfoRow(MaterialSymbols.Gavel, stringRes(Res.string.terms_and_conditions), stringRes(Res.string.not_available_acronym))
            }
        }
    }
}

@Composable
fun loadRelayDiscoveryEvents(relay: NormalizedRelayUrl): State<ImmutableList<RelayDiscoveryEvent>> =
    remember(relay) {
        LocalCache
            .observeEvents<RelayDiscoveryEvent>(
                Filter(
                    kinds = listOf(RelayDiscoveryEvent.KIND),
                    tags = mapOf("d" to listOf(relay.url)),
                    since = TimeUtils.oneWeekAgo(),
                    limit = 3,
                ),
            ).map {
                it.toImmutableList()
            }
    }.collectAsStateWithLifecycle(persistentListOf())

@Composable
private fun RelayMonitorReportCard(
    event: RelayDiscoveryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Monitor author + timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LoadUser(baseUserHex = event.pubKey) { user ->
                    if (user != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            UserPicture(
                                user = user,
                                size = Size25dp,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                            UsernameDisplay(user, weight = Modifier.weight(1f), accountViewModel = accountViewModel)
                        }
                    }
                }

                Text(
                    text = timeAgoNoDot(event.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            HorizontalDivider()

            // RTT metrics
            val rttOpen = event.rttOpen()
            val rttRead = event.rttRead()
            val rttWrite = event.rttWrite()

            if (rttOpen != null || rttRead != null || rttWrite != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rttOpen?.let {
                        RttChip(stringRes(Res.string.relay_monitor_rtt_open), it)
                    }
                    rttRead?.let {
                        RttChip(stringRes(Res.string.relay_monitor_rtt_read), it)
                    }
                    rttWrite?.let {
                        RttChip(stringRes(Res.string.relay_monitor_rtt_write), it)
                    }
                }
            }

            // Network type
            val networkTypes = event.networkTypes()
            if (networkTypes.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Language,
                    stringRes(Res.string.relay_monitor_network),
                    networkTypes.joinToString { it.code },
                )
            }

            // Relay type
            val relayTypes = event.relayTypes()
            if (relayTypes.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Dns,
                    stringRes(Res.string.relay_monitor_relay_type),
                    relayTypes.joinToString(),
                )
            }

            // Requirements
            val requirements = event.requirements()
            if (requirements.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Lock,
                    stringRes(Res.string.relay_monitor_requirements),
                    requirements.joinToString { req ->
                        if (req.negated) "!${req.value}" else req.value
                    },
                )
            }
        }
    }
}

@Composable
private fun RttChip(
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
                text = stringRes(Res.string.relay_monitor_ms, ms.toInt()),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoRow(
    icon: MaterialSymbol,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
private fun ClickableInfoRow(
    icon: MaterialSymbol,
    label: String,
    value: String,
    onClickValue: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClickValue).weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
fun FeeRow(
    label: String,
    fee: Nip11RelayInformation.RelayInformationFee,
) {
    fee.amount?.let {
        val period = fee.period
        val combinedLabel =
            if (period != null) {
                label + " (${timeDiffAgoShortish(period)})"
            } else {
                label
            }

        if (fee.unit == "msats") {
            InfoRow(MaterialSymbols.AttachMoney, combinedLabel, "${it / 1000} sats")
        } else {
            InfoRow(MaterialSymbols.AttachMoney, combinedLabel, "$it ${fee.unit}")
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Nost.wine Relay Info", device = "spec:width=2160px,height=5640px,dpi=440")
fun RelayHeaderPreview() {
    ThemeComparisonRow {
        RelayInformationBody(
            relay = NormalizedRelayUrl("wss://nostr.wine/"),
            relayInfo =
                Nip11RelayInformation(
                    name = "Nostr.wine",
                    icon = "https://image.nostr.build/30acdce4a81926f386622a07343228ae99fa68d012d54c538c0b2129dffe400c.png",
                    description = "A paid nostr relay for wine enthusiasts and everyone else",
                    software = "https://nostr.wine",
                    contact = "wino@nostr.wine",
                    version = "0.3.3",
                    self = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    pubkey = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    payments_url = "https://nostr.wine/invoices",
                    privacy_policy = "https://nostr.wine/terms",
                    terms_of_service = "https://nostr.wine/terms",
                    tags = listOf("Bitcoin", "Amethyst"),
                    supported_nips = listOf("1", "2", "4", "9", "11", "40", "42", "50", "70", "77"),
                    relay_countries = listOf("*"),
                    language_tags = listOf("*"),
                    limitation =
                        Nip11RelayInformation.RelayInformationLimitation(
                            auth_required = false,
                            created_at_lower_limit = 94608000,
                            created_at_upper_limit = 300,
                            max_event_tags = 4000,
                            max_limit = 1000,
                            default_limit = 20,
                            max_message_length = 524288,
                            max_subid_length = 71,
                            max_subscriptions = 50,
                            min_pow_difficulty = 0,
                            payment_required = true,
                            restricted_writes = true,
                        ),
                    fees =
                        Nip11RelayInformation.RelayInformationFees(
                            admission =
                                listOf(
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 3000,
                                        unit = "msats",
                                        period = 2628003,
                                    ),
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 8000,
                                        unit = "sats",
                                        period = 7884009,
                                    ),
                                ),
                        ),
                    supported_grasps = listOf("GRASP-01"),
                ),
            discoveryEvents = persistentListOf(),
            pad = PaddingValues(0.dp),
            relayStats = RelayStat(),
            messages =
                persistentListOf(
                    NoticeDebugMessage(
                        time = TimeUtils.now(),
                        message = "Subscription closed: AccountNotificationsEoseFromRandomRelaysManagerugZU9o auth-required: At least one matching event requires AUTH",
                    ),
                    ErrorDebugMessage(
                        time = TimeUtils.now() - 24000,
                        message = "No such subscription",
                    ),
                    SpamDebugMessage(
                        time = TimeUtils.now() - 24000,
                        link1 = "https://test1.com",
                        link2 = "https://test2.com",
                    ),
                ),
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}

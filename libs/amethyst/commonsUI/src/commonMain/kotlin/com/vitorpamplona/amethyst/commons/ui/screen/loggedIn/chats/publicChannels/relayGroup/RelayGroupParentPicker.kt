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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.isRelaySignedRelayGroup
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_empty
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_label
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_none
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_none_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_pick_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_search
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_top_level_option
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_structure
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelaySubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.sortedBySnapshot
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Subgroup authoring: the "Parent group" section of the create/edit form. Shows the
 * currently-chosen parent (or top-level) as a tappable hero card and, on tap, opens a
 * bottom-sheet picker of the other groups on the same host relay. Selecting one nests
 * this group under it via the kind-9002 `parent` tag when the form is saved.
 */
@Composable
fun ParentGroupSection(
    viewModel: RelayGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
) {
    val relay = viewModel.relay ?: return
    var pickerOpen by remember { mutableStateOf(false) }

    Text(
        text = stringRes(Res.string.relay_group_section_structure),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringRes(Res.string.relay_group_parent_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )

    Spacer(Modifier.height(10.dp))

    ParentSelectorCard(
        parentId = viewModel.parentGroupId,
        relay = relay,
        accountViewModel = accountViewModel,
        onClick = { pickerOpen = true },
    )

    if (pickerOpen) {
        ParentGroupPickerSheet(
            selfGroupId = viewModel.groupId,
            selectedParentId = viewModel.parentGroupId,
            relay = relay,
            accountViewModel = accountViewModel,
            onSelect = {
                viewModel.setParent(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** The hero card in the form showing the current parent (or top-level) with a gradient badge. */
@Composable
private fun ParentSelectorCard(
    parentId: String?,
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    // Resolve the parent (get-or-create so it's never stuck null when the metadata isn't cached
    // yet), warm its single 39000, and observe it so the name/picture fill in as they arrive.
    val liveParent: RelayGroupChannel? =
        parentId?.let { id ->
            val channel = remember(id, relay) { LocalCache.getOrCreateRelayGroupChannel(GroupId(id, relay)) }
            RelayGroupCardWarmupSubscription(channel, accountViewModel.dataSources().relayGroupCardWarmup, accountViewModel)
            val state by channel
                .flow()
                .metadata.stateFlow
                .collectAsStateWithLifecycle()
            state.channel as? RelayGroupChannel ?: channel
        }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GradientBadge {
                if (liveParent != null) {
                    RobohashFallbackAsyncImage(
                        robot = liveParent.groupId.id,
                        model = liveParent.profilePicture(),
                        contentDescription = liveParent.toBestDisplayName(),
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                        loadRobohash = LocalDisplaySettings.current.loadRobohash,
                        autoPlayGif = false,
                    )
                } else {
                    Icon(
                        symbol = MaterialSymbols.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringRes(Res.string.relay_group_parent_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = liveParent?.toBestDisplayName() ?: stringRes(Res.string.relay_group_parent_none),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A circular badge with a primary→tertiary gradient fill, hosting an icon or avatar. */
@Composable
private fun GradientBadge(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Bottom-sheet picker of the other groups on this relay, plus a "top-level" choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentGroupPickerSheet(
    selfGroupId: String,
    selectedParentId: String?,
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Stream the relay's directory while the picker is open so candidates fill in live.
    RelayGroupsOnRelaySubscription(relay, accountViewModel.dataSources().relayGroupsOnRelay, accountViewModel)
    val relayInfo by loadRelayInfo(relay)

    var query by remember { mutableStateOf("") }

    // A group can't parent itself or any of its own descendants (would make a cycle the relay
    // rejects), so exclude them from the candidate set.
    val forbidden =
        remember(selfGroupId, relay) {
            descendantIdsOf(selfGroupId, relay) + selfGroupId
        }

    // Re-read the relay's genuine, relay-signed groups whenever a kind-39000 lands. The initial
    // value is empty (cheap) rather than an eager scan — a produceState initial arg is evaluated
    // on every recomposition (e.g. each search keystroke), so the scan lives only in the producer.
    val candidates by produceState(
        initialValue = emptyList<RelayGroupChannel>(),
        relay,
        relayInfo,
        forbidden,
    ) {
        value = pickCandidates(relay, relayInfo, forbidden)
        LocalCache
            .observeEvents<GroupMetadataEvent>(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .collect { value = pickCandidates(relay, relayInfo, forbidden) }
    }

    val filtered =
        remember(candidates, query) {
            if (query.isBlank()) candidates else candidates.filter { it.anyNameStartsWith(query.trim()) }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringRes(Res.string.relay_group_parent_pick_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        symbol = MaterialSymbols.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                placeholder = { Text(stringRes(Res.string.relay_group_parent_search)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    TopLevelRow(selected = selectedParentId == null) { onSelect(null) }
                }
                items(filtered, key = { it.groupId.id }) { channel ->
                    GroupPickRow(
                        channel = channel,
                        selected = channel.groupId.id == selectedParentId,
                        accountViewModel = accountViewModel,
                        onClick = { onSelect(channel.groupId.id) },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = stringRes(Res.string.relay_group_parent_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The "make this a top-level group" choice at the head of the picker. */
@Composable
private fun TopLevelRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    PickRowScaffold(selected = selected, onClick = onClick) {
        GradientBadge {
            Icon(
                symbol = MaterialSymbols.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringRes(Res.string.relay_group_parent_top_level_option),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringRes(Res.string.relay_group_parent_none_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionMark(selected)
    }
}

/** One candidate parent group row: avatar, name, member count and a selection mark. */
@Composable
private fun GroupPickRow(
    channel: RelayGroupChannel,
    selected: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val memberCount = channel.memberCount()
    PickRowScaffold(selected = selected, onClick = onClick) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier = Modifier.size(46.dp).clip(CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.toBestDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle =
                channel.summary()?.takeIf { it.isNotBlank() }
                    ?: if (memberCount > 0) {
                        pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount)
                    } else {
                        null
                    }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SelectionMark(selected)
    }
}

/** Shared row chrome: a tonal, rounded, clickable surface that highlights when selected. */
@Composable
private fun PickRowScaffold(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        label = "parentRowBg",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A filled check when selected, an empty ring otherwise. */
@Composable
private fun SelectionMark(selected: Boolean) {
    Icon(
        symbol = if (selected) MaterialSymbols.CheckCircle else MaterialSymbols.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.size(24.dp),
    )
}

/** Genuine, relay-signed groups on [relay], minus the forbidden (self + descendant) ids, name-sorted. */
private fun pickCandidates(
    relay: NormalizedRelayUrl,
    relayInfo: Nip11RelayInformation,
    forbidden: Set<String>,
): List<RelayGroupChannel> =
    LocalCache
        .getRelayGroupChannelsOnRelay(relay)
        .asSequence()
        .filter { it.groupId.id !in forbidden }
        .filter { it.event != null && isRelaySignedRelayGroup(it, relayInfo) }
        .toList()
        .sortedBySnapshot { it.toBestDisplayName().lowercase() }

/**
 * The set of group ids reachable as descendants of [rootId] on [relay], following each group's
 * advertised `child` links. Visited-guarded so a malformed cycle can't loop forever.
 */
private fun descendantIdsOf(
    rootId: String,
    relay: NormalizedRelayUrl,
): Set<String> {
    val result = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    LocalCache.getRelayGroupChannelIfExists(GroupId(rootId, relay))?.childGroupIds()?.let { queue.addAll(it) }
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!result.add(id)) continue
        LocalCache.getRelayGroupChannelIfExists(GroupId(id, relay))?.childGroupIds()?.let { queue.addAll(it) }
    }
    return result
}

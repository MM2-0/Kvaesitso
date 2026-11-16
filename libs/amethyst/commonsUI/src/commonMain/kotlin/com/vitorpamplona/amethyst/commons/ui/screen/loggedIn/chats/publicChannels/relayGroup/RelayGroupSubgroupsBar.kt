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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelaySubscription
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** Cap on how many child chips get their own warm-up subscription, to bound relay load. */
private const val MAX_SUBGROUP_CHIPS = 20

/**
 * Self-hiding NIP-29 subgroups bar shown under the group's top bar (below the pinned
 * bar). Renders nothing for a flat group; when the group is part of a hierarchy it shows
 * a breadcrumb chip up to the parent group and a horizontally-scrollable row of chips for
 * the direct child subgroups, in the relay's advertised `child` order. Tapping a chip
 * opens that group. Parent and children always live on the same host relay.
 */
@Composable
fun RelayGroupSubgroupsBar(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val parentId = channel.parentGroupId()
    val childIds = channel.childGroupIds()
    if (parentId == null && childIds.isEmpty()) return

    val relay = channel.groupId.relayUrl

    // Load the parent's + every child's kind-39000 (for their names) with ONE relay-directory
    // subscription rather than a warm-up per chip — a single REQ that streams metadata for all
    // of the relay's groups, versus up to N subscriptions that would each also pull content.
    RelayGroupsOnRelaySubscription(relay, accountViewModel.dataSources().relayGroupsOnRelay, accountViewModel)

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (parentId != null) {
                    SubgroupChip(
                        groupId = GroupId(parentId, relay),
                        leadingSymbol = MaterialSymbols.ArrowUpward,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
                childIds.take(MAX_SUBGROUP_CHIPS).forEach { childId ->
                    SubgroupChip(
                        groupId = GroupId(childId, relay),
                        leadingSymbol = MaterialSymbols.Group,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
                if (childIds.size > MAX_SUBGROUP_CHIPS) {
                    // Never claim to show all children when we don't: surface the remainder count.
                    Text(
                        text = "+${childIds.size - MAX_SUBGROUP_CHIPS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
        }
    }
}

/**
 * A single tappable group chip. Resolves the group by id on the shared host relay (its metadata
 * is streamed by the bar's one directory subscription) and navigates to that group when tapped.
 */
@Composable
private fun SubgroupChip(
    groupId: GroupId,
    leadingSymbol: MaterialSymbol,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadRelayGroupChannel(groupId) { child ->
        val childState by child
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val liveChild = childState.channel as? RelayGroupChannel ?: child

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable { nav.nav(Route.RelayGroup(groupId.id, groupId.relayUrl.url)) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    symbol = leadingSymbol,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = liveChild.toBestDisplayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

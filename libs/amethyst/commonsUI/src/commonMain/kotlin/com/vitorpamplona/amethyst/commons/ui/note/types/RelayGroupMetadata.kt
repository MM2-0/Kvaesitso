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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_invite_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_private
import com.vitorpamplona.amethyst.commons.resources.relay_group_card_open_join
import com.vitorpamplona.amethyst.commons.resources.relay_group_card_public
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_banner
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_hidden
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_restricted
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_open
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TonalChip
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustCardFrame
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * NIP-29 group metadata (kind 39000), as a card for the group it describes: picture, name, about,
 * who may read and join, the relay it lives on, and its member count when that relay's kind-39002
 * roster is already in the cache. NIP-29 events do not name their host; the host is the relay that
 * served them, so the card takes it from where the note was seen and, with one, opens the group.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderGroupMetadata(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event as? GroupMetadataEvent ?: return
    val groupId = event.groupId()
    val relay = remember(noteState) { noteState.note.relays.firstOrNull() }

    // The roster is relay-signed at the same coordinate shape: 39002:<relay key>:<group id>.
    val rosterNote = remember(event) { LocalCache.getOrCreateAddressableNote(Address(GroupMembersEvent.KIND, event.pubKey, groupId)) }
    val rosterState by rosterNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    // Counted once per roster version, not on every recomposition: a roster can list thousands.
    val rosterEvent = rosterState.note.event as? GroupMembersEvent
    val memberCount = remember(rosterEvent) { rosterEvent?.memberCount() }

    val name = event.name()?.ifBlank { null } ?: groupId
    val showPictures = LocalDisplaySettings.current.showProfilePictures
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()

    TrustCardFrame {
        event.banner()?.ifBlank { null }?.let { banner ->
            if (showPictures) {
                AsyncImage(
                    model = banner,
                    contentDescription = stringRes(Res.string.relay_group_field_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                if (relay != null) {
                    Modifier.fillMaxWidth().clickable { nav.nav(Route.RelayGroup(groupId, relay.url)) }
                } else {
                    Modifier.fillMaxWidth()
                },
        ) {
            RobohashFallbackAsyncImage(
                robot = groupId,
                model = event.picture()?.ifBlank { null },
                contentDescription = name,
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                loadProfilePicture = showPictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif = autoPlayGif,
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details =
                    listOfNotNull(
                        memberCount?.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.relay_group_member_count, it, it) },
                        relay?.displayUrl(),
                    )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (relay != null) {
                Icon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = stringRes(Res.string.relay_group_open),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        event.about()?.ifBlank { null }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TonalChip(stringRes(if (event.isPrivate()) Res.string.relay_group_badge_private else Res.string.relay_group_card_public))
            TonalChip(stringRes(if (event.isClosed()) Res.string.relay_group_badge_invite_only else Res.string.relay_group_card_open_join))
            if (event.isRestricted()) TonalChip(stringRes(Res.string.relay_group_flag_restricted))
            if (event.isHidden()) TonalChip(stringRes(Res.string.relay_group_flag_hidden))
        }
    }
}

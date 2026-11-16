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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.badge_award_image
import com.vitorpamplona.amethyst.commons.resources.badge_award_image_for
import com.vitorpamplona.amethyst.commons.resources.badge_untitled
import com.vitorpamplona.amethyst.commons.resources.profile_badges_header
import com.vitorpamplona.amethyst.commons.resources.profile_badges_title
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private val ProfileBadgeSize = 44.dp
private val ProfileBadgeShape = RoundedCornerShape(8.dp)
private const val VISIBLE_BADGE_LIMIT = 8

@Composable
fun DisplayBadges(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val oldDesign = AcceptedBadgeSetEvent.createAddress(baseUser.pubkeyHex)
    val newDesign = ProfileBadgesEvent.createAddress(baseUser.pubkeyHex)

    val oldNote = LocalCache.getOrCreateAddressableNote(oldDesign)
    val newNote = LocalCache.getOrCreateAddressableNote(newDesign)

    WatchAndRenderBadgeList(baseUser, oldNote, newNote, accountViewModel, nav)
}

@Composable
private fun WatchAndRenderBadgeList(
    baseUser: User,
    oldNote: AddressableNote,
    newNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    EventFinderFilterAssemblerSubscription(oldNote, accountViewModel)
    EventFinderFilterAssemblerSubscription(newNote, accountViewModel)

    val flow =
        remember(oldNote, newNote) {
            combine(
                oldNote.flow().metadata.stateFlow,
                newNote.flow().metadata.stateFlow,
            ) { oldNoteState, newNoteState ->
                val oldEvent = oldNoteState.note.event as? AcceptedBadgeSetEvent
                val newEvent = newNoteState.note.event as? ProfileBadgesEvent

                newEvent?.badgeAwardEvents()?.toImmutableList()
                    ?: oldEvent?.badgeAwardEvents()?.toImmutableList()
                    ?: persistentListOf()
            }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    val badgeList by flow.collectAsStateWithLifecycle(persistentListOf())

    if (badgeList.isEmpty()) return

    val isMe = baseUser.pubkeyHex == accountViewModel.userProfile().pubkeyHex
    RenderProfileBadgeStrip(badgeList, isMe, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderProfileBadgeStrip(
    list: ImmutableList<ETag>,
    isMe: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showAllSheet by rememberSaveable { mutableStateOf(false) }
    val visible = remember(list) { list.take(VISIBLE_BADGE_LIMIT) }
    val overflow = (list.size - VISIBLE_BADGE_LIMIT).coerceAtLeast(0)

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.profile_badges_header, list.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isMe) {
                IconButton(
                    onClick = { nav.nav(Route.ProfileBadges) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Settings,
                        contentDescription = stringRes(Res.string.profile_badges_title),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visible.forEach { eTag ->
                LoadDefinitionForAward(eTag, accountViewModel) { defNote ->
                    BadgeThumb(defNote, accountViewModel, nav)
                }
            }
            if (overflow > 0) {
                OverflowChip(overflow) { showAllSheet = true }
            }
        }
    }

    if (showAllSheet) {
        AllBadgesSheet(
            awards = list,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { showAllSheet = false },
        )
    }
}

@Composable
private fun OverflowChip(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(ProfileBadgeSize)
                .clip(ProfileBadgeShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllBadgesSheet(
    awards: ImmutableList<ETag>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringRes(Res.string.profile_badges_header, awards.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = awards, key = { it.eventId }) { eTag ->
                LoadDefinitionForAward(eTag, accountViewModel) { defNote ->
                    BadgeSheetRow(
                        defNote = defNote,
                        accountViewModel = accountViewModel,
                        onClick = {
                            val route = routeFor(defNote, accountViewModel.account)
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                route?.let { nav.nav(it) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeSheetRow(
    defNote: Note,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val event by observeNoteEvent<BadgeDefinitionEvent>(defNote, accountViewModel)
    val definition = event ?: return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RenderBadgeImage(
            id = definition.id,
            name = definition.name(),
            image =
                definition.thumb()?.ifBlank { null }
                    ?: definition.image()?.ifBlank { null },
            accountViewModel = accountViewModel,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition.name()?.ifBlank { null } ?: stringRes(Res.string.badge_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            definition.description()?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadDefinitionForAward(
    eTag: ETag,
    accountViewModel: AccountViewModel,
    content: @Composable (Note) -> Unit,
) {
    val awardNote =
        produceState(
            LocalCache.getNoteIfExists(eTag),
            eTag,
        ) {
            val newValue = LocalCache.checkGetOrCreateNote(eTag)
            if (newValue != value) {
                value = newValue
            }
        }

    awardNote.value?.let { note ->
        val awardEvent by observeNoteEvent<BadgeAwardEvent>(note, accountViewModel)
        awardEvent?.awardDefinition()?.firstOrNull()?.let { defAddr ->
            LoadAddressableNote(defAddr) { defNote ->
                defNote?.let { content(it) }
            }
        }
    }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Box(
        modifier =
            Modifier
                .size(ProfileBadgeSize)
                .clip(ProfileBadgeShape)
                .clickable(
                    onClick = {
                        nav.nav {
                            routeFor(baseNote, accountViewModel.account)
                        }
                    },
                ),
    ) {
        WatchAndRenderBadgeImage(baseNote, accountViewModel)
    }
}

@Composable
private fun WatchAndRenderBadgeImage(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event by observeNoteEvent<BadgeDefinitionEvent>(baseNote, accountViewModel)

    event?.let {
        val image =
            remember(event) {
                event?.thumb()?.ifBlank { null } ?: event?.image()?.ifBlank { null }
            }
        RenderBadgeImage(it.id, it.name(), image, accountViewModel)
    }
}

@Composable
private fun RenderBadgeImage(
    id: String,
    name: String?,
    image: String?,
    accountViewModel: AccountViewModel,
) {
    val description =
        if (name != null) {
            stringRes(id = Res.string.badge_award_image_for, name)
        } else {
            stringRes(id = Res.string.badge_award_image)
        }

    val modifier = Modifier.size(ProfileBadgeSize).clip(ProfileBadgeShape)

    if (image == null) {
        RobohashAsyncImage(
            robot = "badgenotfound",
            contentDescription = description,
            modifier = modifier,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
        )
    } else {
        RobohashFallbackAsyncImage(
            robot = id,
            model = image,
            contentDescription = description,
            modifier = modifier,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
        )
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_entry_hashtag
import com.vitorpamplona.amethyst.commons.resources.backup_entry_mint
import com.vitorpamplona.amethyst.commons.resources.backup_entry_nutzap_key
import com.vitorpamplona.amethyst.commons.resources.backup_entry_person
import com.vitorpamplona.amethyst.commons.resources.backup_entry_profile_field
import com.vitorpamplona.amethyst.commons.resources.backup_entry_thread
import com.vitorpamplona.amethyst.commons.resources.backup_entry_word
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_about
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_banner
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_birthday
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_bot
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_clink_offer
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_display_name
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_lud06
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_lud16
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_name
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_nip05
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_picture
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_pronouns
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_website
import com.vitorpamplona.amethyst.commons.resources.backup_review_demoted_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_dropped_count
import com.vitorpamplona.amethyst.commons.resources.backup_review_dropped_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_gone
import com.vitorpamplona.amethyst.commons.resources.backup_review_inbox
import com.vitorpamplona.amethyst.commons.resources.backup_review_inbox_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_joined
import com.vitorpamplona.amethyst.commons.resources.backup_review_kept_count
import com.vitorpamplona.amethyst.commons.resources.backup_review_key_removed
import com.vitorpamplona.amethyst.commons.resources.backup_review_key_replaced
import com.vitorpamplona.amethyst.commons.resources.backup_review_key_warning
import com.vitorpamplona.amethyst.commons.resources.backup_review_left
import com.vitorpamplona.amethyst.commons.resources.backup_review_left_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_names_and_more
import com.vitorpamplona.amethyst.commons.resources.backup_review_new
import com.vitorpamplona.amethyst.commons.resources.backup_review_new_count
import com.vitorpamplona.amethyst.commons.resources.backup_review_new_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_newly_muted
import com.vitorpamplona.amethyst.commons.resources.backup_review_not_set
import com.vitorpamplona.amethyst.commons.resources.backup_review_nothing_here
import com.vitorpamplona.amethyst.commons.resources.backup_review_outbox
import com.vitorpamplona.amethyst.commons.resources.backup_review_outbox_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_added_body
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_added_title
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_changed_body
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_changed_title
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_wiped_body
import com.vitorpamplona.amethyst.commons.resources.backup_review_private_wiped_title
import com.vitorpamplona.amethyst.commons.resources.backup_review_renamed
import com.vitorpamplona.amethyst.commons.resources.backup_review_renamed_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_saved
import com.vitorpamplona.amethyst.commons.resources.backup_review_search_people
import com.vitorpamplona.amethyst.commons.resources.backup_review_tab_dropped
import com.vitorpamplona.amethyst.commons.resources.backup_review_tab_edited
import com.vitorpamplona.amethyst.commons.resources.backup_review_tab_new
import com.vitorpamplona.amethyst.commons.resources.backup_review_units_tag
import com.vitorpamplona.amethyst.commons.resources.backup_review_unmuted
import com.vitorpamplona.amethyst.commons.resources.backup_review_unmuted_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_review_was_named
import com.vitorpamplona.amethyst.commons.resources.backup_review_words_and_hashtags
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListDiff
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoDiff
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import org.jetbrains.compose.resources.StringResource

private const val GRID_COLUMNS = 4
private const val PEOPLE_PREVIEW = 6

/** UI state of the review screen that must survive the lazy list recomposing. */
@Stable
class ReviewUiState {
    var tab by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var showAllPeople by mutableStateOf(false)
}

/**
 * The review list of a conflict, laid out for the kind of event that changed. Each layout
 * leads with one picture of the change (a shrink bar, a shield, two profiles side by side,
 * relay lanes, a key swap, group tiles) and lists the details below. All layouts contribute
 * lazy items, so lists with hundreds of entries scroll smoothly.
 */
internal fun LazyListScope.eventDiffItems(
    conflict: ReplaceableBackupConflict,
    presentation: DiffPresentation,
    ui: ReviewUiState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val diff = conflict.diff) {
        is ContactListDiff -> followListItems(diff, conflict, ui, accountViewModel, nav)
        is MuteListDiff -> muteListItems(diff, ui, accountViewModel, nav)
        is MetadataDiff -> profileItems(diff, conflict, presentation, accountViewModel, nav)
        is AdvertisedRelayListDiff -> nip65Items(conflict, nav)
        is NutzapInfoDiff -> nutzapItems(diff, conflict, presentation, accountViewModel, nav)
        is SimpleGroupListDiff -> groupItems(diff, conflict, accountViewModel, nav)
        else -> if (!listDiffItems(conflict, accountViewModel, nav)) genericDiffItems(buildRows(presentation), accountViewModel, nav)
    }
}

private val Pad = Modifier.padding(horizontal = 16.dp)

// ---------------------------------------------------------------------------------------
// Follow list: 523 → 120, a split bar, tabs and a searchable grid of faces.
// ---------------------------------------------------------------------------------------

private fun LazyListScope.followListItems(
    diff: ContactListDiff,
    conflict: ReplaceableBackupConflict,
    ui: ReviewUiState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val (saved, new) = conflict.followCounts
    val dropped = diff.follows.removed.size
    val gained = diff.follows.added.size
    val edited = diff.follows.changed.size

    item(key = "follow-hero", contentType = "hero") {
        val tones = conflictTones()
        Column(Pad.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroCounts(stringRes(Res.string.backup_review_saved), saved, stringRes(Res.string.backup_review_new), new)
            SplitBar(
                listOf(
                    BarSegment(saved - dropped, tones.kept),
                    BarSegment(dropped, tones.removed),
                    BarSegment(gained, tones.added),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(tones.kept, stringRes(Res.string.backup_review_kept_count, (saved - dropped).toString()))
                LegendItem(tones.removed, stringRes(Res.string.backup_review_dropped_count, dropped.toString()))
                LegendItem(tones.added, stringRes(Res.string.backup_review_new_count, gained.toString()))
            }
        }
    }

    item(key = "follow-tabs", contentType = "tabs") {
        ReviewTabs(
            labels =
                listOf(
                    stringRes(Res.string.backup_review_tab_dropped, dropped.toString()),
                    stringRes(Res.string.backup_review_tab_new, gained.toString()),
                    stringRes(Res.string.backup_review_tab_edited, edited.toString()),
                ),
            selected = ui.tab,
            onSelect = {
                // The search box only shows on long tabs; a query typed there must not keep
                // filtering a short tab that has no box to clear it from.
                ui.tab = it
                ui.query = ""
            },
            modifier = Pad.padding(top = 16.dp),
        )
    }

    val (people, badge) =
        when (ui.tab) {
            1 -> diff.follows.added.map { it.pubKey } to Badge.ADDED
            2 -> diff.follows.changed.map { it.after.pubKey } to Badge.CHANGED
            else -> diff.follows.removed.map { it.pubKey } to Badge.REMOVED
        }

    if (people.size > GRID_COLUMNS * 2) {
        item(key = "follow-search", contentType = "search") {
            PeopleSearch(ui.query, people.size, { ui.query = it }, Pad.padding(top = 12.dp))
        }
    }

    val query = ui.query.trim()
    val filtered = if (query.isEmpty()) people else people.filter { matchesPerson(it, query) }

    peopleGrid("follow-${ui.tab}", filtered, badge, accountViewModel, nav)

    if (filtered.isEmpty()) {
        item(key = "follow-empty", contentType = "note") {
            DetailText(stringRes(Res.string.backup_review_nothing_here), Pad.padding(vertical = 24.dp))
        }
    }
}

private fun matchesPerson(
    pubKey: HexKey,
    query: String,
): Boolean {
    val user = LocalCache.getUserIfExists(pubKey)
    val name = user?.toBestDisplayName() ?: pubKey
    return name.contains(query, ignoreCase = true) || pubKey.startsWith(query, ignoreCase = true)
}

private enum class Badge { REMOVED, ADDED, CHANGED }

/** Faces in rows of [GRID_COLUMNS], each row a lazy item. */
private fun LazyListScope.peopleGrid(
    keyPrefix: String,
    people: List<HexKey>,
    badge: Badge,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    items(people.chunked(GRID_COLUMNS), key = { keyPrefix + it.first() }, contentType = { "people-row" }) { row ->
        Row(Pad.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { PersonCell(it, badge, accountViewModel, nav, Modifier.weight(1f)) }
            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PersonCell(
    pubKey: HexKey,
    badge: Badge,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val tones = conflictTones()
    val (sign, color) =
        when (badge) {
            Badge.REMOVED -> "−" to tones.removed
            Badge.ADDED -> "+" to tones.added
            Badge.CHANGED -> "~" to tones.changed
        }
    LoadUser(pubKey) { user ->
        Column(
            modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.nav(Route.Profile(pubKey)) }.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                if (user != null) {
                    UserPicture(user, 56.dp, accountViewModel = accountViewModel, nav = nav)
                } else {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(sign, color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 14.sp)
                }
            }
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                if (user != null) {
                    UsernameDisplay(user, fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)
                } else {
                    Text(pubKey.toShortDisplay(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ReviewTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.placeholderText,
                )
            }
        }
    }
}

@Composable
private fun PeopleSearch(
    query: String,
    count: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(symbol = MaterialSymbols.Search, contentDescription = null) },
        placeholder = { Text(pluralStringRes(Res.plurals.backup_review_search_people, count, count.toString())) },
    )
}

// ---------------------------------------------------------------------------------------
// Mute list: a shield, per-kind tiles, faces, struck-through words, the private section.
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.muteListItems(
    diff: MuteListDiff,
    ui: ReviewUiState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val removed = diff.publicMutes.removed
    val added = diff.publicMutes.added
    val people = removed.filterIsInstance<UserTag>().map { it.pubKey }
    val words = removed.filterIsInstance<WordTag>().map { it.word }
    val hashtags = removed.filterIsInstance<HashtagTag>().map { "#" + it.hashtag }
    val threads = removed.filterIsInstance<EventTag>().map { it.eventId }

    item(key = "mute-hero", contentType = "hero") {
        val tones = conflictTones()
        TintedPanel(tones.removedContainer(), Pad.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(symbol = MaterialSymbols.Shield, contentDescription = null, tint = tones.removed, modifier = Modifier.size(44.dp))
                Column {
                    Text(
                        stringRes(Res.string.backup_review_unmuted, removed.size.toString()),
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = tones.removed,
                    )
                    Text(stringRes(Res.string.backup_review_unmuted_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                    if (added.isNotEmpty()) {
                        Text(stringRes(Res.string.backup_review_newly_muted, added.size.toString()), style = MaterialTheme.typography.bodySmall, color = tones.added)
                    }
                }
            }
        }
    }

    item(key = "mute-tiles", contentType = "tiles") {
        val tones = conflictTones()
        Row(Pad.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("−${people.size}", stringRes(Res.string.backup_entry_person), tones.removed, Modifier.weight(1f))
            StatTile("−${words.size}", stringRes(Res.string.backup_entry_word), tones.removed, Modifier.weight(1f))
            StatTile("−${hashtags.size}", stringRes(Res.string.backup_entry_hashtag), tones.removed, Modifier.weight(1f))
            StatTile("−${threads.size}", stringRes(Res.string.backup_entry_thread), tones.removed, Modifier.weight(1f))
        }
    }

    if (people.isNotEmpty()) {
        item(key = "mute-people", contentType = "facepile") {
            Column(Pad.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCaption(stringRes(Res.string.backup_entry_person))
                FacePile(people, accountViewModel) { ui.showAllPeople = !ui.showAllPeople }
            }
        }
        if (ui.showAllPeople) peopleGrid("mute-people-", people, Badge.REMOVED, accountViewModel, nav)
    }

    if (words.isNotEmpty() || hashtags.isNotEmpty()) {
        item(key = "mute-words", contentType = "pills") {
            val tones = conflictTones()
            Column(Pad.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCaption(stringRes(Res.string.backup_review_words_and_hashtags))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (words.map { "\"$it\"" } + hashtags).forEach { StruckPill(it, tones.removed) }
                }
            }
        }
    }

    if (threads.isNotEmpty()) {
        item(key = "mute-threads-caption", contentType = "caption") { SectionCaption(stringRes(Res.string.backup_entry_thread), Pad.padding(top = 18.dp)) }
        items(threads, key = { "mute-thread-$it" }, contentType = { "note" }) { id ->
            LoadNote(id) { note ->
                if (note != null) {
                    NoteCompose(note, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), makeItShort = true, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                } else {
                    TextEntry(id.toShortDisplay(), null)
                }
            }
        }
    }

    if (diff.privateItems != ContentChange.NONE) {
        item(key = "mute-private", contentType = "private") { PrivateItemsCard(diff.privateItems, Pad.padding(top = 18.dp)) }
    }
}

@Composable
private fun FacePile(
    people: List<HexKey>,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    // Names and faces are observed, so people this device hasn't loaded yet resolve from relays.
    val names = people.take(2).map { observedName(it, accountViewModel) }
    val summary =
        if (people.size > 2) {
            stringRes(Res.string.backup_review_names_and_more, names.joinToString(", "), (people.size - 2).toString())
        } else {
            names.joinToString(", ")
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(48.dp)) {
            people.take(PEOPLE_PREVIEW).forEachIndexed { index, pubKey ->
                Box(
                    Modifier
                        .padding(start = (index * 32).dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(3.dp),
                ) {
                    RobohashFallbackAsyncImage(
                        robot = pubKey,
                        model = observedPicture(pubKey, accountViewModel),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                        loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    )
                }
            }
        }
        Text(
            summary,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The user's metadata, observed: loads the [User] if needed, subscribes to their metadata
 * on relays and recomposes when it arrives or changes in LocalCache.
 */
@Composable
internal fun observedUserInfo(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
): UserMetadata? {
    var user by remember(pubKey) { mutableStateOf(LocalCache.getUserIfExists(pubKey)) }
    if (user == null) {
        LaunchedEffect(pubKey) { user = LocalCache.checkGetOrCreateUser(pubKey) }
    }
    val loaded = user ?: return null
    val info by observeUserInfo(loaded, accountViewModel)
    return info?.info
}

@Composable
internal fun observedName(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
): String = observedUserInfo(pubKey, accountViewModel)?.bestName() ?: pubKey.toShortDisplay()

@Composable
internal fun observedPicture(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
): String? = observedUserInfo(pubKey, accountViewModel)?.profilePicture()

@Composable
private fun StruckPill(
    text: String,
    color: Color,
) {
    Text(
        text,
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        color = color,
        textDecoration = TextDecoration.LineThrough,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun PrivateItemsCard(
    change: ContentChange,
    modifier: Modifier = Modifier,
) {
    val tones = conflictTones()
    val (title, body) =
        when (change) {
            ContentChange.CLEARED -> Res.string.backup_review_private_wiped_title to Res.string.backup_review_private_wiped_body
            ContentChange.ADDED -> Res.string.backup_review_private_added_title to Res.string.backup_review_private_added_body
            else -> Res.string.backup_review_private_changed_title to Res.string.backup_review_private_changed_body
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tones.changedContainer()), contentAlignment = Alignment.Center) {
            Icon(symbol = MaterialSymbols.Lock, contentDescription = null, tint = tones.changed)
        }
        Column(Modifier.weight(1f)) {
            Text(stringRes(title), style = MaterialTheme.typography.titleSmall)
            Text(stringRes(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Profile: both versions side by side, then field-by-field inline diffs.
// ---------------------------------------------------------------------------------------

private class ProfileField(
    val labelRes: StringResource,
    val change: ValueChange<String>,
    val isImage: Boolean = false,
)

private fun profileFields(diff: MetadataDiff): List<ProfileField> =
    listOfNotNull(
        diff.picture?.let { ProfileField(Res.string.backup_profile_field_picture, it, isImage = true) },
        diff.banner?.let { ProfileField(Res.string.backup_profile_field_banner, it, isImage = true) },
        diff.name?.let { ProfileField(Res.string.backup_profile_field_name, it) },
        diff.displayName?.let { ProfileField(Res.string.backup_profile_field_display_name, it) },
        diff.about?.let { ProfileField(Res.string.backup_profile_field_about, it) },
        diff.nip05?.let { ProfileField(Res.string.backup_profile_field_nip05, it) },
        diff.website?.let { ProfileField(Res.string.backup_profile_field_website, it) },
        diff.lud16?.let { ProfileField(Res.string.backup_profile_field_lud16, it) },
        diff.lud06?.let { ProfileField(Res.string.backup_profile_field_lud06, it) },
        diff.clinkOffer?.let { ProfileField(Res.string.backup_profile_field_clink_offer, it) },
        diff.pronouns?.let { ProfileField(Res.string.backup_profile_field_pronouns, it) },
        diff.bot?.let { ProfileField(Res.string.backup_profile_field_bot, ValueChange(it.before?.toString(), it.after?.toString())) },
        diff.birthday?.let {
            ProfileField(
                Res.string.backup_profile_field_birthday,
                ValueChange(
                    it.before?.let { b -> listOfNotNull(b.year, b.month, b.day).joinToString("-") },
                    it.after?.let { a -> listOfNotNull(a.year, a.month, a.day).joinToString("-") },
                ),
            )
        },
    )

private fun LazyListScope.profileItems(
    diff: MetadataDiff,
    conflict: ReplaceableBackupConflict,
    presentation: DiffPresentation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val saved = (conflict.saved as? MetadataEvent)?.contactMetaData() ?: UserMetadata()
    val incoming = (conflict.incoming as? MetadataEvent)?.contactMetaData() ?: UserMetadata()
    val pubKey = conflict.incoming.pubKey

    item(key = "profile-cards", contentType = "profile-cards") {
        val tones = conflictTones()
        Row(Pad.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniProfileCard(saved, pubKey, stringRes(Res.string.backup_review_saved), MaterialTheme.colorScheme.outlineVariant, accountViewModel, Modifier.weight(1f))
            MiniProfileCard(incoming, pubKey, stringRes(Res.string.backup_review_new), tones.removed.copy(alpha = 0.5f), accountViewModel, Modifier.weight(1f))
        }
    }

    val fields = profileFields(diff)
    if (fields.isNotEmpty()) {
        item(key = "profile-fields", contentType = "profile-fields") {
            Column(
                Pad
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                fields.forEach { FieldDiffRow(it, accountViewModel) }
            }
        }
    }
    // Linked identities and fields this app doesn't model use the generic sections.
    genericDiffItems(buildRows(presentation, skipGroups = setOf(Res.string.backup_entry_profile_field)), accountViewModel, nav)
}

@Composable
private fun MiniProfileCard(
    meta: UserMetadata,
    pubKey: HexKey,
    label: String,
    borderColor: Color,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        BannerImage(meta.banner, Modifier.fillMaxWidth().height(64.dp), accountViewModel)
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier
                    .offset(y = (-26).dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(3.dp),
            ) {
                RobohashFallbackAsyncImage(
                    robot = pubKey,
                    model = meta.picture,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(CircleShape),
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                )
            }
            Column(Modifier.offset(y = (-20).dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(meta.bestName() ?: pubKey.toShortDisplay(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta.nip05?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.placeholderText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                meta.about?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                StatusTag(label, MaterialTheme.colorScheme.placeholderText, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun FieldDiffRow(
    field: ProfileField,
    accountViewModel: AccountViewModel,
) {
    val tones = conflictTones()
    val before = field.change.before
    val after = field.change.after
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringRes(field.labelRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.placeholderText)
        if (field.isImage) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImageThumb(before, tones.removed, accountViewModel)
                Text("→", color = MaterialTheme.colorScheme.placeholderText)
                ImageThumb(after, tones.added, accountViewModel)
            }
        } else {
            FlowRowDiff(before, after)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowDiff(
    before: String?,
    after: String?,
) {
    val tones = conflictTones()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (before != null) DiffChip(clip(before), tones.removed, struck = true)
        when {
            after == null -> StatusTag(stringRes(Res.string.backup_review_gone), tones.removed)
            before == null -> {
                DiffChip(clip(after), tones.added, struck = false)
                StatusTag(stringRes(Res.string.backup_review_new_tag), tones.added)
            }
            else -> {
                Text("→", color = MaterialTheme.colorScheme.placeholderText)
                DiffChip(clip(after), tones.added, struck = false)
            }
        }
    }
}

@Composable
private fun DiffChip(
    text: String,
    color: Color,
    struck: Boolean,
) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        textDecoration = if (struck) TextDecoration.LineThrough else null,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ImageThumb(
    url: String?,
    color: Color,
    accountViewModel: AccountViewModel,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(72.dp)
            .clip(shape)
            .border(2.dp, color.copy(alpha = 0.6f), shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Text(stringRes(Res.string.backup_review_not_set), style = MaterialTheme.typography.labelSmall, color = color)
        } else {
            MyAsyncImage(
                imageUrl = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.size(72.dp),
                loadedImageModifier = Modifier.size(72.dp),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = { Text(clip(url), style = MaterialTheme.typography.labelSmall, maxLines = 3) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// NIP-65: reach numbers, then outbox and inbox lanes with every relay's fate.
// ---------------------------------------------------------------------------------------

private enum class LaneState { KEPT, DROPPED, ADDED, DEMOTED }

private class LaneRow(
    val url: String,
    val state: LaneState,
)

private fun lane(
    saved: Map<String, AdvertisedRelayType>,
    new: Map<String, AdvertisedRelayType>,
    uses: (AdvertisedRelayType) -> Boolean,
): List<LaneRow> {
    val rows = mutableListOf<LaneRow>()
    saved.forEach { (url, type) ->
        if (!uses(type)) return@forEach
        val now = new[url]
        rows.add(
            LaneRow(
                url,
                when {
                    now == null -> LaneState.DROPPED
                    uses(now) -> LaneState.KEPT
                    else -> LaneState.DEMOTED
                },
            ),
        )
    }
    new.forEach { (url, type) ->
        if (uses(type) && saved[url]?.let(uses) != true) rows.add(LaneRow(url, LaneState.ADDED))
    }
    return rows.sortedBy { it.state.ordinal }
}

private fun LazyListScope.nip65Items(
    conflict: ReplaceableBackupConflict,
    nav: INav,
) {
    val saved = (conflict.saved as? AdvertisedRelayListEvent)?.relays()?.associate { it.relayUrl.url to it.type } ?: emptyMap()
    val new = (conflict.incoming as? AdvertisedRelayListEvent)?.relays()?.associate { it.relayUrl.url to it.type } ?: emptyMap()
    val writes = { t: AdvertisedRelayType -> t != AdvertisedRelayType.READ }
    val reads = { t: AdvertisedRelayType -> t != AdvertisedRelayType.WRITE }
    val outbox = lane(saved, new, writes)
    val inbox = lane(saved, new, reads)

    laneItems("outbox", stringRes = Res.string.backup_review_outbox, subRes = Res.string.backup_review_outbox_explainer, rows = outbox, nav = nav)
    laneItems("inbox", stringRes = Res.string.backup_review_inbox, subRes = Res.string.backup_review_inbox_explainer, rows = inbox, nav = nav)
}

private fun LazyListScope.laneItems(
    key: String,
    stringRes: StringResource,
    subRes: StringResource,
    rows: List<LaneRow>,
    nav: INav,
) {
    if (rows.isEmpty()) return
    item(key = "lane-$key", contentType = "lane-header") {
        Row(Pad.padding(top = 18.dp, bottom = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(stringRes(stringRes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringRes(subRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
        }
    }
    items(rows, key = { "lane-$key-${it.url}" }, contentType = { "lane-row" }) { LaneRelayRow(it, nav) }
}

@Composable
private fun LaneRelayRow(
    row: LaneRow,
    nav: INav,
) {
    val tones = conflictTones()
    val (color, tag) =
        when (row.state) {
            LaneState.KEPT -> tones.kept to null
            LaneState.DROPPED -> tones.removed to Res.string.backup_review_dropped_tag
            LaneState.ADDED -> tones.added to Res.string.backup_review_new_tag
            LaneState.DEMOTED -> tones.changed to Res.string.backup_review_demoted_tag
        }
    Row(
        Pad
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { nav.nav(Route.RelayInfo(row.url)) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(if (row.state == LaneState.DROPPED) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color)),
        )
        Text(
            row.url.removePrefix("wss://").removeSuffix("/"),
            modifier = Modifier.weight(1f),
            color = if (row.state == LaneState.KEPT) MaterialTheme.colorScheme.onSurface else color,
            textDecoration = if (row.state == LaneState.DROPPED) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        tag?.let { StatusTag(stringRes(it), color) }
    }
}

// ---------------------------------------------------------------------------------------
// Nutzap info: the receiving key as a fingerprint swap, then mints.
// ---------------------------------------------------------------------------------------

private fun LazyListScope.nutzapItems(
    diff: NutzapInfoDiff,
    conflict: ReplaceableBackupConflict,
    presentation: DiffPresentation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val key = diff.p2pkPubkey
    if (key != null) {
        item(key = "nutzap-key", contentType = "key-swap") {
            val tones = conflictTones()
            Column(
                Pad
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(symbol = MaterialSymbols.Key, contentDescription = null, tint = tones.changed)
                    Text(stringRes(if (key.after == null) Res.string.backup_review_key_removed else Res.string.backup_review_key_replaced), style = MaterialTheme.typography.titleSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    KeyColumn(key.before, stringRes(Res.string.backup_review_saved), tones.kept)
                    Text("→", fontSize = 24.sp, color = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.padding(horizontal = 18.dp))
                    KeyColumn(key.after, stringRes(Res.string.backup_review_new), tones.changed)
                }
                if (key.after != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tones.changedContainer())
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(symbol = MaterialSymbols.Warning, contentDescription = null, tint = tones.changed, modifier = Modifier.size(18.dp))
                        Text(stringRes(Res.string.backup_review_key_warning), style = MaterialTheme.typography.bodySmall, color = tones.changed)
                    }
                }
            }
        }
    }

    // Matched the way the diff matches them, and once per mint: a list can repeat a mint,
    // and a repeated row key crashes the LazyColumn.
    val mintKey = { m: NutzapMintTag -> m.mintUrl.trimEnd('/').lowercase() }
    val saved = (conflict.saved as? NutzapInfoEvent)?.mints().orEmpty().distinctBy(mintKey)
    val removedUrls =
        diff.mints.removed
            .map(mintKey)
            .toSet()
    val changedUrls = diff.mints.changed.associateBy { mintKey(it.before) }
    val mintRows =
        saved.map { m ->
            when (mintKey(m)) {
                in removedUrls -> Triple(m.mintUrl, m.units.joinToString(), LaneState.DROPPED)
                in changedUrls -> Triple(m.mintUrl, changedUrls.getValue(mintKey(m)).let { it.before.units.joinToString() + " → " + it.after.units.joinToString() }, LaneState.DEMOTED)
                else -> Triple(m.mintUrl, m.units.joinToString(), LaneState.KEPT)
            }
        } + diff.mints.added.map { Triple(it.mintUrl, it.units.joinToString(), LaneState.ADDED) }

    if (mintRows.isNotEmpty()) {
        item(key = "nutzap-mints", contentType = "caption") { SectionCaption(stringRes(Res.string.backup_entry_mint), Pad.padding(top = 18.dp, bottom = 6.dp)) }
        items(mintRows, key = { "mint-" + it.first }, contentType = { "mint" }) { (url, units, state) -> MintRow(url, units, state) }
    }
    genericDiffItems(buildRows(presentation, skipGroups = setOf(Res.string.backup_entry_mint, Res.string.backup_entry_nutzap_key)), accountViewModel, nav)
}

@Composable
private fun KeyColumn(
    hex: String?,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hex != null) {
            KeyFingerprint(hex, color)
        } else {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, color, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text(stringRes(Res.string.backup_review_not_set), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
        StatusTag(label, color)
    }
}

@Composable
private fun MintRow(
    url: String,
    units: String,
    state: LaneState,
) {
    val tones = conflictTones()
    val (color, tag) =
        when (state) {
            LaneState.KEPT -> MaterialTheme.colorScheme.onSurface to null
            LaneState.DROPPED -> tones.removed to Res.string.backup_review_dropped_tag
            LaneState.ADDED -> tones.added to Res.string.backup_review_new_tag
            LaneState.DEMOTED -> tones.changed to Res.string.backup_review_units_tag
        }
    Row(
        Pad
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(symbol = MaterialSymbols.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                url.removePrefix("https://").removeSuffix("/"),
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (state == LaneState.DROPPED) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (units.isNotEmpty()) Text(units, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.placeholderText)
        }
        tag?.let { StatusTag(stringRes(it), color) }
    }
}

// ---------------------------------------------------------------------------------------
// Groups: a tile per group, faded when you left it, badged when new or renamed.
// ---------------------------------------------------------------------------------------

private class GroupTile(
    val group: GroupTag,
    val state: LaneState,
    val oldName: String? = null,
)

private fun LazyListScope.groupItems(
    diff: SimpleGroupListDiff,
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Matched the way the diff matches them, and once per group: a repeated row key crashes
    // the LazyColumn.
    val key = { g: GroupTag -> g.groupId + "@" + (RelayUrlNormalizer.normalizeOrNull(g.relayUrl)?.url ?: g.relayUrl) }
    val removed =
        diff.groups.removed
            .map(key)
            .toSet()
    val changed = diff.groups.changed.associateBy { key(it.before) }
    val saved = (conflict.saved as? SimpleGroupListEvent)?.publicGroups().orEmpty().distinctBy(key)
    val tiles =
        saved.map { g ->
            when (key(g)) {
                in removed -> GroupTile(g, LaneState.DROPPED)
                in changed -> changed.getValue(key(g)).let { GroupTile(it.after, LaneState.DEMOTED, it.before.name) }
                else -> GroupTile(g, LaneState.KEPT)
            }
        } + diff.groups.added.map { GroupTile(it, LaneState.ADDED) }

    item(key = "group-stats", contentType = "tiles") {
        val tones = conflictTones()
        Row(Pad.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                diff.groups.removed.size
                    .toString(),
                stringRes(Res.string.backup_review_left),
                tones.removed,
                Modifier.weight(1f),
            )
            StatTile(
                diff.groups.added.size
                    .toString(),
                stringRes(Res.string.backup_review_joined),
                tones.added,
                Modifier.weight(1f),
            )
            StatTile(
                diff.groups.changed.size
                    .toString(),
                stringRes(Res.string.backup_review_renamed),
                tones.changed,
                Modifier.weight(1f),
            )
        }
    }
    items(tiles.chunked(2), key = { "group-" + key(it.first().group) }, contentType = { "group-row" }) { row ->
        Row(Pad.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { GroupTileCard(it, accountViewModel, nav, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun GroupTileCard(
    tile: GroupTile,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val relay = RelayUrlNormalizer.normalizeOrNull(tile.group.relayUrl)
    if (relay == null) {
        GroupTileContent(tile, tile.group.name ?: tile.group.groupId, null, accountViewModel, modifier, null)
        return
    }
    LoadRelayGroupChannel(GroupId(tile.group.groupId, relay)) { channel ->
        // Subscribes to the group's relay-signed metadata (even when not joined) and recomposes
        // when its name or picture arrives.
        val state by observeChannel(channel, accountViewModel)
        val current = (state?.channel as? RelayGroupChannel) ?: channel
        val name = current.event?.name() ?: tile.group.name ?: tile.group.groupId
        GroupTileContent(tile, name, current.profilePicture(), accountViewModel, modifier) { nav.nav(routeFor(current)) }
    }
}

@Composable
private fun GroupTileContent(
    tile: GroupTile,
    name: String,
    picture: String?,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val tones = conflictTones()
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Column(
            Modifier.alpha(if (tile.state == LaneState.DROPPED) 0.45f else 1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RobohashFallbackAsyncImage(
                robot = tile.group.groupId + "@" + tile.group.relayUrl,
                model = picture,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
            )
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    tile.oldName?.let { stringRes(Res.string.backup_review_was_named, it) } ?: tile.group.relayUrl
                        .removePrefix("wss://")
                        .removeSuffix("/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (tile.state) {
            LaneState.DROPPED -> StatusTag(stringRes(Res.string.backup_review_left_tag), tones.removed, Modifier.align(Alignment.TopEnd))
            LaneState.ADDED -> StatusTag(stringRes(Res.string.backup_review_new_tag), tones.added, Modifier.align(Alignment.TopEnd))
            LaneState.DEMOTED -> StatusTag(stringRes(Res.string.backup_review_renamed_tag), tones.changed, Modifier.align(Alignment.TopEnd))
            LaneState.KEPT -> {}
        }
    }
}

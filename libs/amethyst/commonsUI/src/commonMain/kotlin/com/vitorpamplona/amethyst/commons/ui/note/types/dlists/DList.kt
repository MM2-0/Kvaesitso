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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLink
import com.vitorpamplona.amethyst.commons.decentralizedLists.ResolvedConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.communityCoordinateOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.itemLinks
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.DListConceptView
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.conceptViewFor
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.dlists.DLIST_ITEMS_LIMIT
import com.vitorpamplona.amethyst.commons.relayClient.dlists.DListItemsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.dlists.dListItemKinds
import com.vitorpamplona.amethyst.commons.relayClient.dlists.dListItemPointers
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dlist_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_item_in
import com.vitorpamplona.amethyst.commons.resources.dlist_items
import com.vitorpamplona.amethyst.commons.resources.dlist_items_count
import com.vitorpamplona.amethyst.commons.resources.dlist_items_count_more
import com.vitorpamplona.amethyst.commons.resources.dlist_list_of
import com.vitorpamplona.amethyst.commons.resources.dlist_missing_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_unaffiliated
import com.vitorpamplona.amethyst.commons.resources.dlist_undefined
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers.BookView
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers.GitHubAccountView
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers.PlaceView
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.AddressMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.BookmarkMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.PagedMemberColumn
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.CappedChips
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TonalChip
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustSectionLabel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldRequirement
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemAddressIds
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemEventIds
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemKeys
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemStrings
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Item previews a header card shows before "open the list". */
private const val PREVIEW_ITEMS = 3

/** Field names on a header, or words on an item, shown before a "+N more" chip. */
private const val MAX_CHIPS = 16

/** What a D-list item lists, people first, then notes, then addresses, as the card draws them. */
@Immutable
private sealed interface DListMember {
    @Immutable
    class Person(
        val pubKey: HexKey,
    ) : DListMember

    @Immutable
    class NoteId(
        val id: HexKey,
    ) : DListMember

    @Immutable
    class Coordinate(
        val address: Address,
    ) : DListMember
}

private fun dListItemMembers(event: Event): List<DListMember> =
    buildList {
        event.tags.itemKeys().forEach { add(DListMember.Person(it)) }
        event.tags.itemEventIds().forEach { add(DListMember.NoteId(it)) }
        event.tags.itemAddressIds().forEach { id -> Address.parse(id)?.let { add(DListMember.Coordinate(it)) } }
    }

/** A D-list item that is not itself a header: what the thread view draws as the item's own page. */
fun isDListItemPage(event: Event?): Boolean = (event is ListItemEvent && !event.declaresList()) || (event is AddressableListItemEvent && !event.declaresList())

/**
 * Tier three's reading of an item among many (Brainstorm's `TierThreeProvider`): a GitHub account's
 * handle and avatar, a book's cover, title and author. With no view for the concept, or one that
 * doesn't apply, the hints' reading stands.
 */
internal fun presentAmongMany(
    item: Event,
    communityCoordinate: String?,
    definition: DListHeaderView,
    shown: DListItemView,
): DListItemView = conceptViewFor(communityCoordinate, item.tags, definition)?.present(item.tags, definition, shown) ?: shown

/**
 * A Decentralized List header (kinds 9998 / 39998): its picture, its names, what it holds, the
 * fields each item carries, and how many items relays have filed under it, with the first few.
 * Mirrors Brainstorm's `DListHeaderHero`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderDListHeader(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event ?: return
    val header = remember(event) { dListHeaderView(event.tags, event.dTagOrNull()) }

    TrustCardFrame {
        DListKicker(MaterialSymbols.AutoMirrored.List, stringRes(Res.string.dlist_list_of, header.plural), null, null)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DListPicture(null, header.image, 56.dp, accountViewModel)
            Column(Modifier.weight(1f)) {
                Text(
                    text = header.plural,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                header.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (header.unaffiliated) {
            TonalChip(stringRes(Res.string.dlist_unaffiliated))
        }

        if (header.fields.isNotEmpty()) {
            TrustSectionLabel(stringRes(Res.string.dlist_fields))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CappedChips(header.fields, MAX_CHIPS) { field ->
                    TonalChip(field.name, emphasized = field.requirement == FieldRequirement.REQUIRED)
                }
            }
        }

        HeaderItems(baseNote, event, header, accountViewModel, nav)
    }
}

/** The items filed under [header], as they arrive in the cache; [subscribe] also asks relays for them. */
@Composable
internal fun observeListItems(
    headerNote: Note,
    header: Event,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): List<Note> {
    if (subscribe) DListItemsFilterAssemblerSubscription(headerNote, accountViewModel.dataSources().dListItems)

    val items by remember(header) {
        val filter = Filter(kinds = dListItemKinds(header), tags = mapOf("z" to dListItemPointers(header)))
        // Off the main thread: the first emission is a scan of the whole cache.
        LocalCache.observeNotes(filter).map { notes -> notes.filter { it.event != null } }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(emptyList())
    return items
}

/** The items filed under this header, loaded from the relays it was seen on while the card shows. */
@Composable
private fun HeaderItems(
    baseNote: Note,
    event: Event,
    header: DListHeaderView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items = observeListItems(baseNote, event, accountViewModel)
    if (items.isEmpty()) return

    val community = remember(event, header) { communityCoordinateOf((event as? AddressableEvent)?.addressTag(), header) }

    TrustSectionLabel(
        if (items.size >= DLIST_ITEMS_LIMIT) {
            stringRes(Res.string.dlist_items_count_more, items.size)
        } else {
            pluralStringRes(Res.plurals.dlist_items_count, items.size, items.size)
        },
    )

    Column {
        items.take(PREVIEW_ITEMS).forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(thickness = DividerThickness)
            DListItemRow(item, header, community, null, accountViewModel, nav)
        }
    }
}

/** An item as one line among others: its picture, its title and one grey line (Brainstorm's `ItemLine`). */
@Composable
internal fun DListItemRow(
    item: Note,
    definition: DListHeaderView,
    communityCoordinate: String?,
    line: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = item.event ?: return
    val view =
        remember(event, definition, communityCoordinate) {
            presentAmongMany(event, communityCoordinate, definition, dListItemView(event.tags, event.content, definition))
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(item, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(vertical = 6.dp),
    ) {
        DListPicture(view.image, definition.image, 36.dp, accountViewModel, view.imageShape)
        Column(Modifier.weight(1f)) {
            DListItemTitle(view.title, definition.singular, FontWeight.SemiBold)
            (line ?: view.summary)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A Decentralized List item (kinds 9999 / 39999), in the tier its concept earns (Brainstorm ADR 0007):
 *
 * 1. raw, when no definition can be read: the tags as they are;
 * 2. dynamic, from the governing definition's fields, hints and URL templates: everywhere else, and
 *    in a feed, where a tier-three concept's items still read its way (a GitHub account's avatar,
 *    a book's cover) through [presentAmongMany];
 * 3. custom, on the item's own page ([isPage], the thread view), for a community concept with a
 *    view of its own ([DListConceptView]) that applies.
 */
@Composable
fun RenderDListItem(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    isPage: Boolean = false,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event ?: return

    ResolveDListConcept(event, accountViewModel) { resolved, listedNote, listName ->
        val governing = resolved?.governing
        val templates = observeUrlTemplates(governing?.links ?: emptyList(), accountViewModel)
        val hinted = remember(event, governing) { dListItemView(event.tags, event.content, governing) }
        val links =
            remember(hinted, governing, templates) {
                val built = governing?.let { itemLinks(event.tags, it.links, templates) } ?: emptyList()
                (listOfNotNull(hinted.link) + built).distinctBy { it.href }
            }

        val custom = remember(resolved, event) { resolved?.let { conceptViewFor(it.communityCoordinate, event.tags, it.governing) } }
        val openList: (() -> Unit)? =
            listedNote?.let { note ->
                {
                    routeFor(note, accountViewModel.account)?.let { route -> nav.nav(route) }
                }
            }

        if (isPage && resolved != null && custom != null) {
            TrustCardFrame {
                when (custom) {
                    DListConceptView.GITHUB_ACCOUNT -> GitHubAccountView(event, resolved, hinted, links, openList, accountViewModel)
                    DListConceptView.PLACE -> PlaceView(event, resolved, hinted, links, listedNote, openList, accountViewModel, nav)
                    DListConceptView.BOOK -> BookView(event, resolved, hinted, links, listedNote, openList, accountViewModel, nav)
                }
            }
        } else {
            val shown =
                remember(hinted, resolved) {
                    if (resolved != null) presentAmongMany(event, resolved.communityCoordinate, resolved.governing, hinted) else hinted
                }
            RenderDListItemCard(event, resolved, shown, links, listName, openList, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** Tiers one and two: the card every concept gets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderDListItemCard(
    event: Event,
    resolved: ResolvedConcept?,
    view: DListItemView,
    links: List<DListLink>,
    listName: String?,
    openList: (() -> Unit)?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val header = resolved?.governing
    val members = remember(event) { dListItemMembers(event) }
    val words = remember(event) { event.tags.itemStrings() }
    val singular = header?.singular?.ifBlank { null }

    TrustCardFrame {
        val listLabel = header?.plural?.ifBlank { null } ?: listName
        DListKicker(MaterialSymbols.AutoMirrored.Label, singular, listLabel?.let { stringRes(Res.string.dlist_item_in, it) }, openList)

        // Tier one with nothing to call the item has no heading: the tags below say what it is.
        if (view.defined || view.title != null || view.summary != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (view.image != null || header?.image != null) {
                    DListPicture(view.image, header?.image, 56.dp, accountViewModel, view.imageShape)
                }
                Column(Modifier.weight(1f)) {
                    DListItemTitle(view.title, singular, FontWeight.Bold, large = true)
                    view.summary?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        DListLinkButtons(links)

        DListFacts(view.facts)

        view.media?.let { DListMediaBlock(it, view.title ?: singular, view.summary, view.image ?: header?.image, accountViewModel) }

        view.location?.let { DListLocationMap(it) }

        if (view.missing.isNotEmpty()) {
            TonalChip(stringRes(Res.string.dlist_missing_fields, view.missing.joinToString(", ")))
        }

        if (words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CappedChips(words, MAX_CHIPS) { TonalChip(it) }
            }
        }

        if (members.isNotEmpty()) {
            TrustSectionLabel(stringRes(Res.string.dlist_items))
            // An item can list thousands of people or notes: they page in like a list card's members.
            PagedMemberColumn(members, backgroundColor) { member ->
                when (member) {
                    is DListMember.Person -> UserMemberRow(member.pubKey, accountViewModel, nav)
                    is DListMember.NoteId -> BookmarkMemberRow(EventBookmark(member.id), quotesLeft, backgroundColor, accountViewModel, nav)
                    is DListMember.Coordinate -> AddressMemberRow(member.address, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }

        if (resolved == null && view.undeclared.isNotEmpty()) {
            // Tier one: no definition to read the tags by, so they are shown as they are.
            Text(
                text = stringRes(Res.string.dlist_undefined),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DListRawFields(view.undeclared)
        } else {
            // A definition was read: what it doesn't show waits in the fold, outside-the-definition
            // tags apart (all of them, for a header that declares nothing).
            DListMoreFields(view.moreFields, view.undeclared, singular)
        }

        resolved?.let { DListDefinitionLine(it) }
    }
}

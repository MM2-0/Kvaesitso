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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListFact
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListImageShape
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLink
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLocation
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListMedia
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListMediaKind
import com.vitorpamplona.amethyst.commons.decentralizedLists.DefinitionAgreement
import com.vitorpamplona.amethyst.commons.decentralizedLists.DefinitionDifference
import com.vitorpamplona.amethyst.commons.decentralizedLists.DefinitionSource
import com.vitorpamplona.amethyst.commons.decentralizedLists.LinkRef
import com.vitorpamplona.amethyst.commons.decentralizedLists.ResolvedConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.UrlTemplate
import com.vitorpamplona.amethyst.commons.decentralizedLists.communityCoordinateOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.copyCandidates
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.fieldWords
import com.vitorpamplona.amethyst.commons.decentralizedLists.resolveConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.urlTemplateOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.dlist_agreement_unknown
import com.vitorpamplona.amethyst.commons.resources.dlist_agrees
import com.vitorpamplona.amethyst.commons.resources.dlist_difference_description
import com.vitorpamplona.amethyst.commons.resources.dlist_difference_display
import com.vitorpamplona.amethyst.commons.resources.dlist_difference_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_difference_links
import com.vitorpamplona.amethyst.commons.resources.dlist_difference_names
import com.vitorpamplona.amethyst.commons.resources.dlist_differs
import com.vitorpamplona.amethyst.commons.resources.dlist_more_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_not_in_definition
import com.vitorpamplona.amethyst.commons.resources.dlist_not_in_the_definition
import com.vitorpamplona.amethyst.commons.resources.dlist_open_in_osm
import com.vitorpamplona.amethyst.commons.resources.dlist_show_on_map
import com.vitorpamplona.amethyst.commons.resources.dlist_show_on_map_from
import com.vitorpamplona.amethyst.commons.resources.dlist_shown_as_assistant
import com.vitorpamplona.amethyst.commons.resources.dlist_shown_as_community
import com.vitorpamplona.amethyst.commons.resources.dlist_shown_as_listed
import com.vitorpamplona.amethyst.commons.resources.dlist_shown_as_personal
import com.vitorpamplona.amethyst.commons.resources.dlist_untitled
import com.vitorpamplona.amethyst.commons.resources.dlist_untitled_item
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TonalChip
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.publicDListAssistant
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.parentLists
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent

internal fun Event.dTagOrNull() = (this as? AddressableEvent)?.dTag()

internal val PictureShape = RoundedCornerShape(12.dp)

/** Facts a card shows before it asks to be expanded. */
private const val COLLAPSED_FACTS = 6

/** The header [note] holds, read as a definition; null until it arrives. */
@Composable
internal fun observeHeaderView(
    note: Note,
    accountViewModel: AccountViewModel,
): DListHeaderView? {
    val event by observeNoteEvent<Event>(note, accountViewModel)
    return remember(event) { event?.let { dListHeaderView(it.tags, it.dTagOrNull()) } }
}

@Composable
private fun observeHeaderViewAt(
    address: Address,
    accountViewModel: AccountViewModel,
): DListHeaderView? {
    val note = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
    return observeHeaderView(note, accountViewModel)
}

/**
 * The governing definition of [event]'s concept for this reader (Brainstorm ADR 0004): the item's
 * `z` names a header; that header names its community concept; the reader's own copy of that
 * concept, else their assistant's, else the community header, else the header the item named,
 * draws the item. Every header is asked of relays while the item is on screen.
 */
@Composable
internal fun ResolveDListConcept(
    event: Event,
    accountViewModel: AccountViewModel,
    content: @Composable (resolved: ResolvedConcept?, listedNote: Note?, listName: String?) -> Unit,
) {
    val parent = remember(event) { event.tags.parentLists().firstOrNull() }
    when (parent) {
        is ParentList.Coordinate ->
            LoadAddressableNote(parent.address) { listedNote ->
                if (listedNote == null) {
                    content(null, null, null)
                } else {
                    ResolveFromListed(listedNote, parent.address.toValue(), accountViewModel, content)
                }
            }

        is ParentList.EventId ->
            LoadNote(parent.eventId) { listedNote ->
                if (listedNote == null) {
                    content(null, null, null)
                } else {
                    ResolveFromListed(listedNote, null, accountViewModel, content)
                }
            }

        is ParentList.Name -> content(null, null, parent.name)

        null -> content(null, null, null)
    }
}

@Composable
private fun ResolveFromListed(
    listedNote: Note,
    listedCoordinate: String?,
    accountViewModel: AccountViewModel,
    content: @Composable (resolved: ResolvedConcept?, listedNote: Note?, listName: String?) -> Unit,
) {
    val listed = observeHeaderView(listedNote, accountViewModel)
    val community = remember(listed, listedCoordinate) { listed?.let { communityCoordinateOf(listedCoordinate, it) } ?: listedCoordinate }
    val communityAddress = remember(community, listedCoordinate) { community?.takeIf { it != listedCoordinate }?.let { Address.parse(it) } }
    val communityView =
        if (communityAddress != null) {
            key(communityAddress) { observeHeaderViewAt(communityAddress, accountViewModel) }
        } else {
            listed
        }

    val account = accountViewModel.account
    // Observed, not read once: the reader's kind 10040 often arrives after the card has composed.
    val trustProviders by account.trustProviderList
        .getTrustProviderListFlow()
        .collectAsStateWithLifecycle()
    val assistant = remember(trustProviders) { (trustProviders.note.event as? TrustProviderListEvent)?.publicDListAssistant()?.assistant }
    val candidates = remember(community, account.pubKey, assistant) { copyCandidates(community, account.pubKey, assistant) }
    val copies = candidates.map { (source, address) -> key(address) { source to observeHeaderViewAt(address, accountViewModel) } }

    val resolved = remember(listed, communityView, copies) { resolveConcept(listedCoordinate, listed, communityView, copies) }
    content(resolved, listedNote, null)
}

@Composable
private fun observeUrlTemplate(
    ref: LinkRef,
    accountViewModel: AccountViewModel,
): UrlTemplate? {
    val note =
        remember(ref) {
            // The header says where its template lives; the loader asks hinted relays too.
            ref.relay?.let { RelayUrlNormalizer.normalizeOrNull(it) }?.let { LocalCache.relayHints.addEvent(ref.templateId, it) }
            LocalCache.getOrCreateNote(ref.templateId)
        }
    val event by observeNoteEvent<Event>(note, accountViewModel)
    return remember(event) { event?.let { urlTemplateOf(it.id, it.tags) } }
}

/** The URL templates a definition pins, by id, fetched while the item is on screen. */
@Composable
internal fun observeUrlTemplates(
    refs: List<LinkRef>,
    accountViewModel: AccountViewModel,
): Map<HexKey, UrlTemplate> {
    val templates = refs.map { ref -> key(ref.templateId) { observeUrlTemplate(ref, accountViewModel) } }
    return remember(templates) { templates.filterNotNull().associateBy { it.id } }
}

@Composable
internal fun DListKicker(
    symbol: MaterialSymbol,
    singular: String?,
    listLabel: String?,
    onClick: (() -> Unit)?,
) {
    if (singular == null && listLabel == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        singular?.let {
            Text(text = it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        listLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DListItemTitle(
    title: String?,
    singular: String?,
    fontWeight: FontWeight?,
    large: Boolean = false,
) {
    val style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    if (title != null) {
        Text(text = title, style = style, fontWeight = fontWeight, maxLines = 2, overflow = TextOverflow.Ellipsis)
    } else {
        Text(
            text = singular?.ifBlank { null }?.let { stringRes(Res.string.dlist_untitled_item, it.lowercase()) } ?: stringRes(Res.string.dlist_untitled),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The item's picture, else its list's, else the plain list mark. A face or a logo is round, as a
 * person's is; a cover is a portrait tile, as on a shelf; the list's own image sits in a square.
 */
@Composable
internal fun DListPicture(
    image: String?,
    fallback: String?,
    size: Dp,
    accountViewModel: AccountViewModel,
    shape: DListImageShape = DListImageShape.ROUND,
    mark: MaterialSymbol = MaterialSymbols.AutoMirrored.List,
) {
    val url = image ?: fallback
    val portrait = shape == DListImageShape.PORTRAIT
    val clip =
        when {
            portrait -> RoundedCornerShape(4.dp)
            image != null -> CircleShape
            else -> PictureShape
        }
    Box(
        modifier =
            Modifier
                .then(if (portrait) Modifier.width(size * 0.7f).height(size) else Modifier.size(size))
                .clip(clip)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            MyAsyncImage(
                imageUrl = url,
                contentDescription = null,
                contentScale = if (image != null) ContentScale.Crop else ContentScale.Fit,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = if (image != null) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(size / 6),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = { DListMark(size, mark) },
            )
        } else {
            DListMark(size, mark)
        }
    }
}

@Composable
internal fun DListMark(
    size: Dp,
    mark: MaterialSymbol = MaterialSymbols.AutoMirrored.List,
) {
    Icon(symbol = mark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
}

/** The ways out an item's definition builds: its own link and its templates', the first one filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DListLinkButtons(links: List<DListLink>) {
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEachIndexed { index, link ->
            val label = "${link.label} · ${link.host}"
            if (index == 0) {
                Button(onClick = { runCatching { uriHandler.openUri(link.href) } }) {
                    Icon(symbol = MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = label, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { runCatching { uriHandler.openUri(link.href) } }) {
                    Icon(symbol = MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = label, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One "label: value" row; a typed value opens what its type spells. */
@Composable
internal fun DListFactRow(
    label: String,
    value: String,
    href: String?,
    extra: Int,
    monospaceLabel: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospaceLabel) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.35f),
        )
        Row(Modifier.weight(0.65f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (href != null) {
                // The value as written ("+1 346 493 2858"); the link is what its type spells
                // ("tel:+13464932858"), handed to the platform: a browser, a dialer or a mail app.
                ClickableUrl(url = href, displayText = value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (extra > 0) {
                Text(
                    text = pluralStringRes(Res.plurals.chips_n_more, extra, extra),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The definition's `fact` fields, labelled, in the header's order. */
@Composable
internal fun DListFacts(facts: List<DListFact>) {
    if (facts.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) facts else facts.take(COLLAPSED_FACTS)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { DListFactRow(it.label ?: fieldWords(it.field), it.value, it.href, it.extra) }
        if (facts.size > COLLAPSED_FACTS && !expanded) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShowMoreButton { expanded = true }
            }
        }
    }
}

/** Tags as they are, by their own names: tier one, and the part of the fold outside the definition. */
@Composable
internal fun DListRawFields(fields: List<DListFact>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        fields.forEach { DListFactRow(it.field, it.value, it.href, it.extra, monospaceLabel = true) }
    }
}

/**
 * What the definition doesn't show: declared fields no role, fact or link uses, then the tags the
 * item carries that the definition never declared, apart and labelled so. Folded away; an item
 * the definition fully covers shows nothing.
 */
@Composable
internal fun DListMoreFields(
    moreFields: List<DListFact>,
    undeclared: List<DListFact>,
    singular: String?,
) {
    if (moreFields.isEmpty() && undeclared.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable { open = !open },
        ) {
            Icon(
                symbol = if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringRes(Res.string.dlist_more_fields, moreFields.size + undeclared.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            DListRawFields(moreFields)
            if (undeclared.isNotEmpty()) {
                Text(
                    text = singular?.ifBlank { null }?.let { stringRes(Res.string.dlist_not_in_definition, it) } ?: stringRes(Res.string.dlist_not_in_the_definition),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DListRawFields(undeclared)
            }
        }
    }
}

/** The item's playable file: the app's own player, with the item's title and picture as the track's. */
@Composable
internal fun DListMediaBlock(
    media: DListMedia,
    title: String?,
    artist: String?,
    cover: String?,
    accountViewModel: AccountViewModel,
) {
    val content =
        remember(media, title, artist, cover) {
            MediaUrlVideo(
                url = media.url,
                description = title,
                artworkUri = cover,
                authorName = artist,
                mimeType = if (media.kind == DListMediaKind.AUDIO) "audio/mpeg" else null,
            )
        }
    ZoomableContentView(content = content, roundedCorner = true, contentScale = ContentScale.FillWidth, accountViewModel = accountViewModel)
}

/**
 * Where the item is (Brainstorm's `ItemMap`): a map only when the reader asks, so showing the card
 * contacts no tile server. Then the platform's map ([LocationPreviewMap], OpenStreetMap tiles), a
 * spot pinned and closer in than an area, with the place on openstreetmap.org a tap away, which is
 * all a platform without a map draws.
 */
@Composable
internal fun DListLocationMap(location: DListLocation) {
    var open by rememberSaveable(location.geohash) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (open) {
            LocationPreviewMap(
                latitude = location.lat,
                longitude = location.lon,
                modifier = Modifier.clip(PictureShape),
                zoom = if (location.isSpot) SPOT_ZOOM else AREA_ZOOM,
                aspectRatio = MAP_ASPECT_RATIO,
            )
            Text(
                text = stringRes(Res.string.dlist_open_in_osm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(location.osmUrl()) } },
            )
        } else {
            OutlinedButton(onClick = { open = true }) {
                Icon(symbol = MaterialSymbols.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(text = stringRes(Res.string.dlist_show_on_map_from, stringRes(Res.string.dlist_show_on_map), "openstreetmap.org"), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

internal const val SPOT_ZOOM = 16.0
private const val AREA_ZOOM = 12.0
internal const val MAP_ASPECT_RATIO = 2f

/** Last and quietest: which definition drew the item, and whether it agrees with the community's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DListDefinitionLine(resolved: ResolvedConcept) {
    val name = resolved.governing.plural
    val line =
        when (resolved.source) {
            DefinitionSource.PERSONAL -> stringRes(Res.string.dlist_shown_as_personal, name)
            DefinitionSource.ASSISTANT -> stringRes(Res.string.dlist_shown_as_assistant, name)
            DefinitionSource.COMMUNITY -> stringRes(Res.string.dlist_shown_as_community, name)
            DefinitionSource.LISTED -> stringRes(Res.string.dlist_shown_as_listed, name)
        }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        when (resolved.agreement) {
            DefinitionAgreement.AGREES -> TonalChip(stringRes(Res.string.dlist_agrees))
            DefinitionAgreement.DIFFERS -> TonalChip(stringRes(Res.string.dlist_differs, differenceWords(resolved.differences)), emphasized = true)
            DefinitionAgreement.UNKNOWN -> if (resolved.source != DefinitionSource.LISTED) TonalChip(stringRes(Res.string.dlist_agreement_unknown))
            DefinitionAgreement.NO_LOCAL_COPY -> Unit
        }
    }
}

@Composable
private fun differenceWords(differences: List<DefinitionDifference>): String =
    differences
        .map {
            when (it) {
                DefinitionDifference.NAMES -> stringRes(Res.string.dlist_difference_names)
                DefinitionDifference.DESCRIPTION -> stringRes(Res.string.dlist_difference_description)
                DefinitionDifference.FIELDS -> stringRes(Res.string.dlist_difference_fields)
                DefinitionDifference.DISPLAY -> stringRes(Res.string.dlist_difference_display)
                DefinitionDifference.LINKS -> stringRes(Res.string.dlist_difference_links)
            }
        }.joinToString(", ")

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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLink
import com.vitorpamplona.amethyst.commons.decentralizedLists.ResolvedConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.httpHost
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.BitcoinPayment
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.OpeningStatus
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.Place
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.PlaceFields
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.Week
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.Weekday
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.addressLines
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.bitcoinPaymentOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.categoryWords
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.clockWords
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.directionsUrl
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.distanceKm
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.kindWords
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.mapUrl
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.openingStatus
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.parseOpeningHours
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.placeLocalTime
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.placeOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.spansWords
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dlist_day_friday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_monday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_saturday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_sunday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_thursday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_tuesday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_wednesday
import com.vitorpamplona.amethyst.commons.resources.dlist_distance_km
import com.vitorpamplona.amethyst.commons.resources.dlist_distance_m
import com.vitorpamplona.amethyst.commons.resources.dlist_open_in_osm
import com.vitorpamplona.amethyst.commons.resources.dlist_place_accepts_bitcoin
import com.vitorpamplona.amethyst.commons.resources.dlist_place_accepts_bitcoin_both
import com.vitorpamplona.amethyst.commons.resources.dlist_place_accepts_bitcoin_lightning
import com.vitorpamplona.amethyst.commons.resources.dlist_place_accepts_bitcoin_onchain
import com.vitorpamplona.amethyst.commons.resources.dlist_place_call
import com.vitorpamplona.amethyst.commons.resources.dlist_place_closed
import com.vitorpamplona.amethyst.commons.resources.dlist_place_closed_opens_on
import com.vitorpamplona.amethyst.commons.resources.dlist_place_closed_opens_today
import com.vitorpamplona.amethyst.commons.resources.dlist_place_directions
import com.vitorpamplona.amethyst.commons.resources.dlist_place_hours
import com.vitorpamplona.amethyst.commons.resources.dlist_place_listed_on
import com.vitorpamplona.amethyst.commons.resources.dlist_place_location
import com.vitorpamplona.amethyst.commons.resources.dlist_place_menu
import com.vitorpamplona.amethyst.commons.resources.dlist_place_nearby
import com.vitorpamplona.amethyst.commons.resources.dlist_place_open_always
import com.vitorpamplona.amethyst.commons.resources.dlist_place_open_until
import com.vitorpamplona.amethyst.commons.resources.dlist_place_osm_license
import com.vitorpamplona.amethyst.commons.resources.dlist_place_reserve
import com.vitorpamplona.amethyst.commons.resources.dlist_place_seeded_btcmap
import com.vitorpamplona.amethyst.commons.resources.website
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListDefinitionLine
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListItemRow
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListKicker
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListMoreFields
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.MAP_ASPECT_RATIO
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.PictureShape
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.SPOT_ZOOM
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.observeListItems
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustSectionLabel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.util.DecimalPatternFormatter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

private const val NEARBY = 3

private val OpenColor = Color(0xFF2E7D32)
private val ClosedColor = Color(0xFFB26A00)

/**
 * A food and drink place on its own page (tier three, Brainstorm `components/items/PlaceView`), the
 * shape a reader knows from a maps app: the name, what it is as words ("Fast food · Pizza") and its
 * price, whether it is open now in the place's local time (estimated from its longitude), the ways
 * out (Directions, Call, Website, Menu, Reserve: only the ones with data), the address and contact
 * details beside the whole week of hours, today first, the nearest places from the same list, and
 * the attribution the data's ODbL licence asks for.
 *
 * Every value is read through the governing definition's fields, by the community concept's names
 * for them. The page leads with a map, as Brainstorm's does; its reviews, recommendations and the
 * website's own picture are left out. (A card in a feed keeps its map behind a button, so a
 * scrolling feed contacts no map server; on the place's own page the map is the point.)
 */
@Composable
internal fun PlaceView(
    event: Event,
    resolved: ResolvedConcept,
    hinted: DListItemView,
    links: List<DListLink>,
    listedNote: Note?,
    openList: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val definition = resolved.governing
    val place = remember(event, definition) { placeOf(event.tags, definition) } ?: return
    val week = remember(place) { parseOpeningHours(place.hours) }
    val placeLon = place.lon
    // Ticks once a minute, so "Open now" turns to "Closed" while the page stays open; nothing to
    // tick for without hours and a longitude to read the place's clock by.
    val now by produceState(TimeUtils.now(), week, placeLon) {
        if (week == null || placeLon == null) return@produceState
        while (true) {
            delay(60_000)
            value = TimeUtils.now()
        }
    }
    val local = remember(placeLon, now / 60) { placeLon?.let { placeLocalTime(it, now) } }
    val status = remember(week, local) { if (week != null && local != null) local.first to openingStatus(week, local.first, local.second) else null }

    DListKicker(MaterialSymbols.Restaurant, definition.singular, null, openList)

    // The map leads, as on Brainstorm's page: on a place's own page it is the point, so it shows
    // outright. A platform without a map draws nothing here; the link further down stays.
    val lat = place.lat
    val lon = place.lon
    if (lat != null && lon != null) {
        LocationPreviewMap(
            latitude = lat,
            longitude = lon,
            modifier = Modifier.clip(PictureShape),
            zoom = SPOT_ZOOM,
            aspectRatio = MAP_ASPECT_RATIO,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val kind = place.kindWords()
        if (kind != null || place.priceRange != null) {
            Text(
                text = listOfNotNull(kind, place.priceRange?.let { "$".repeat(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        status?.let { (today, open) -> OpenStatus(open, today) }
    }

    place.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

    Actions(place)

    HorizontalDivider(thickness = DividerThickness)

    Details(place, week, local?.first)

    place.mapUrl()?.let { url ->
        val uriHandler = LocalUriHandler.current
        Text(
            text = stringRes(Res.string.dlist_open_in_osm),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
        )
    }

    if (listedNote != null && place.lat != null && place.lon != null) {
        NearbyPlaces(event, place, listedNote, resolved, accountViewModel, nav)
    }

    Attribution(place, links)

    val used = remember(hinted) { hinted.usedFields + PlaceFields.ALL }
    DListMoreFields(hinted.moreFields.filter { it.field !in used }, hinted.undeclared, definition.singular)
    DListDefinitionLine(resolved)
}

private fun Weekday.dayName(): StringResource =
    when (this) {
        Weekday.MO -> Res.string.dlist_day_monday
        Weekday.TU -> Res.string.dlist_day_tuesday
        Weekday.WE -> Res.string.dlist_day_wednesday
        Weekday.TH -> Res.string.dlist_day_thursday
        Weekday.FR -> Res.string.dlist_day_friday
        Weekday.SA -> Res.string.dlist_day_saturday
        Weekday.SU -> Res.string.dlist_day_sunday
    }

@Composable
private fun OpenStatus(
    status: OpeningStatus,
    today: Weekday,
) {
    val text =
        when (status) {
            is OpeningStatus.Open -> status.until?.let { stringRes(Res.string.dlist_place_open_until, clockWords(it)) } ?: stringRes(Res.string.dlist_place_open_always)
            is OpeningStatus.Closed -> {
                val at = status.opensAt
                val day = status.opensDay
                when {
                    at == null || day == null -> stringRes(Res.string.dlist_place_closed)
                    day == today -> stringRes(Res.string.dlist_place_closed_opens_today, clockWords(at))
                    else -> stringRes(Res.string.dlist_place_closed_opens_on, stringRes(day.dayName()), clockWords(at))
                }
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val color = if (status is OpeningStatus.Open) OpenColor else ClosedColor
        Icon(symbol = MaterialSymbols.Schedule, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** The ways out, a maps app's way: round buttons with the label under, the one primary action filled. */
@Composable
private fun Actions(place: Place) {
    val actions =
        listOfNotNull(
            place.directionsUrl()?.let { Triple(it, Res.string.dlist_place_directions, MaterialSymbols.AltRoute) },
            place.call?.let { Triple(it, Res.string.dlist_place_call, MaterialSymbols.Call) },
            place.website?.let { Triple(it, Res.string.website, MaterialSymbols.Language) },
            place.menu?.let { Triple(it, Res.string.dlist_place_menu, MaterialSymbols.MenuBook) },
            place.reservations?.let { Triple(it, Res.string.dlist_place_reserve, MaterialSymbols.EventAvailable) },
        )
    if (actions.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth()) {
        actions.forEachIndexed { index, (href, label, symbol) ->
            val primary = index == 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { runCatching { uriHandler.openUri(href) } }
                        .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        symbol = symbol,
                        contentDescription = null,
                        tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(text = stringRes(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Location and hours: where and how to reach it, then the week, today first and bold. */
@Composable
private fun Details(
    place: Place,
    week: Week?,
    today: Weekday?,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TrustSectionLabel(stringRes(Res.string.dlist_place_location))
        val lines = place.addressLines()
        val where = lines.ifEmpty { listOfNotNull(place.country) }
        if (where.isNotEmpty()) {
            val directions = place.directionsUrl()
            DetailRow(MaterialSymbols.LocationOn, onClick = directions?.let { { runCatching { uriHandler.openUri(it) } } }) {
                Column {
                    where.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                    val country = place.country
                    if (lines.isNotEmpty() && country != null && lines.none { it.contains(country) }) {
                        Text(text = country.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        place.phone?.let { phone ->
            DetailRow(MaterialSymbols.Call, onClick = place.call?.let { { runCatching { uriHandler.openUri(it) } } }) {
                Text(text = phone, style = MaterialTheme.typography.bodyMedium, color = if (place.call != null) MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        }
        place.website?.let { site ->
            DetailRow(MaterialSymbols.Language, onClick = { runCatching { uriHandler.openUri(site) } }) {
                Text(text = httpHost(site), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        // How it pays is a detail beside the address, not a badge: every place on a BTC Map list takes Bitcoin.
        bitcoinPaymentOf(place.bitcoin)?.let { payment ->
            DetailRow(MaterialSymbols.CurrencyBitcoin) {
                Text(
                    text =
                        stringRes(
                            when (payment) {
                                BitcoinPayment.LIGHTNING -> Res.string.dlist_place_accepts_bitcoin_lightning
                                BitcoinPayment.ONCHAIN -> Res.string.dlist_place_accepts_bitcoin_onchain
                                BitcoinPayment.BOTH -> Res.string.dlist_place_accepts_bitcoin_both
                                BitcoinPayment.UNSPECIFIED -> Res.string.dlist_place_accepts_bitcoin
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (week != null && today != null) {
            TrustSectionLabel(stringRes(Res.string.dlist_place_hours))
            val order = remember(today) { (0 until 7).map { today.plus(it) } }
            order.forEach { day ->
                val isToday = day == today
                Row {
                    Text(
                        text = stringRes(day.dayName()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                        modifier = Modifier.width(120.dp),
                    )
                    val spans = week[day].orEmpty()
                    Text(
                        text = if (spans.isEmpty()) stringRes(Res.string.dlist_place_closed) else spansWords(spans),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    symbol: MaterialSymbol,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        content()
    }
}

@Composable
private fun distanceWords(km: Double): String {
    val meters = (km * 1000).roundToInt()
    return if (meters < 1000) {
        stringRes(Res.string.dlist_distance_m, meters)
    } else {
        // In the reader's own decimal separator.
        val format = remember { DecimalPatternFormatter("0.0") }
        stringRes(Res.string.dlist_distance_km, format.format(km))
    }
}

/** The nearest places from the same list: the one thing a list can give that a review site can't. */
@Composable
private fun NearbyPlaces(
    event: Event,
    place: Place,
    listedNote: Note,
    resolved: ResolvedConcept,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val header by observeNoteEvent<Event>(listedNote, accountViewModel)
    val headerEvent = header ?: return
    val items = observeListItems(listedNote, headerEvent, accountViewModel)
    val definition = resolved.governing
    // Every item of the list is read here, up to the relay cap: off the main thread, once per change.
    val nearest by produceState(emptyList<Triple<Note, Place, Double>>(), items, place, definition) {
        value =
            withContext(Dispatchers.Default) {
                items
                    .mapNotNull { note ->
                        val other = note.event ?: return@mapNotNull null
                        if (other.id == event.id) return@mapNotNull null
                        val p = placeOf(other.tags, definition) ?: return@mapNotNull null
                        val lat = p.lat ?: return@mapNotNull null
                        val lon = p.lon ?: return@mapNotNull null
                        if (p.name == place.name && p.address == place.address) return@mapNotNull null
                        Triple(note, p, distanceKm(place.lat!!, place.lon!!, lat, lon))
                    }.sortedBy { it.third }
                    .take(NEARBY)
            }
    }
    if (nearest.isEmpty()) return

    TrustSectionLabel(stringRes(Res.string.dlist_place_nearby))
    Column {
        nearest.forEach { (note, p, km) ->
            DListItemRow(
                note,
                definition,
                resolved.communityCoordinate,
                listOfNotNull(distanceWords(km), categoryWords(p.category)).joinToString(" · "),
                accountViewModel,
                nav,
            )
        }
    }
}

/** Where the record came from, each listing a link, and the licence its data asks to be named under. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attribution(
    place: Place,
    links: List<DListLink>,
) {
    // The definition's templates (BTC Map's listing, say), apart from the website the details already show.
    val listings = links.filter { it.href != place.website }
    val style = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (listings.isNotEmpty()) {
            Text(text = stringRes(Res.string.dlist_place_listed_on), style = style, color = muted)
            listings.forEach { ClickableUrl(url = it.href, displayText = it.label, style = style) }
        } else if (place.source == "btcmap") {
            Text(text = stringRes(Res.string.dlist_place_seeded_btcmap), style = style, color = muted)
        }
        Text(text = stringRes(Res.string.dlist_place_osm_license), style = style, color = muted)
    }
}

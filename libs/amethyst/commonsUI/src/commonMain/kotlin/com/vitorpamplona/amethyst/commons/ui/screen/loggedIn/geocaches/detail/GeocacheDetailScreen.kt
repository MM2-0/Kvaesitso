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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.geocache_add_note
import com.vitorpamplona.amethyst.commons.resources.geocache_add_to_hunt
import com.vitorpamplona.amethyst.commons.resources.geocache_archived_notice
import com.vitorpamplona.amethyst.commons.resources.geocache_didnt_find_it
import com.vitorpamplona.amethyst.commons.resources.geocache_ftf_available
import com.vitorpamplona.amethyst.commons.resources.geocache_i_found_it
import com.vitorpamplona.amethyst.commons.resources.geocache_loading
import com.vitorpamplona.amethyst.commons.resources.geocache_logs_section
import com.vitorpamplona.amethyst.commons.resources.geocache_mission_section
import com.vitorpamplona.amethyst.commons.resources.geocache_navigate
import com.vitorpamplona.amethyst.commons.resources.geocache_needs_maintenance
import com.vitorpamplona.amethyst.commons.resources.geocache_no_logs
import com.vitorpamplona.amethyst.commons.resources.geocache_photos_section
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.route_geocache_detail
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheChips
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheDetailHero
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheSpoilerHint
import com.vitorpamplona.amethyst.commons.ui.note.forListing
import com.vitorpamplona.amethyst.commons.ui.note.geocacheEmoji
import com.vitorpamplona.amethyst.commons.ui.note.geocachePoint
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.note.specSummary
import com.vitorpamplona.amethyst.commons.ui.note.types.distanceToCache
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier

/**
 * The page every geocaching entry point lands on: a feed card, an `naddr` deep link, a
 * notification that someone found your cache, a search hit, a pin on the map.
 *
 * Laid out in the order a player standing outside actually needs it — what is it, how hard,
 * how far, what does it look like, what did the last person say — with the find actions pinned
 * to the bottom because that is the whole reason they opened the screen.
 *
 * Two NIP-CC rules shape the action bar rather than the body:
 *
 * - An **archived** cache, or one whose **first-to-find claim is taken by someone else**, hides
 *   the find affordances and shows a status strip. The listing stays fully readable — a claimed
 *   cache is still a place worth visiting, it is simply no longer a prize.
 * - The **owner** gets a different bar entirely. Editing, archiving and locking in the winner
 *   are the owner's lifecycle, and offering "I found it" on your own cache is nonsense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocacheDetailScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GeocachesFilterAssemblerSubscription(accountViewModel)

    val targetAddress = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val targetNote = remember(targetAddress) { LocalCache.getOrCreateAddressableNote(targetAddress) }

    // observeNote both makes this reactive to a newer revision of the listing — 37516 is
    // replaceable, so an edit, an archive or an `F` lock-in arrives as a replacement at the same
    // address — and issues the targeted relay fetch that makes deep links work when the event is
    // not cached yet.
    val noteState by observeNote(targetNote, accountViewModel)
    val listing = noteState.note.event as? GeocacheListingEvent
    val logs = rememberGeocacheLogs(noteState)

    var sheet by remember { mutableStateOf<GeocacheLogSheetType?>(null) }
    var addingToHunt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.route_geocache_detail)) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    if (listing != null) {
                        GeocacheShareAction(targetAddress)
                        GeocacheOwnerActions(listing, targetAddress, logs, accountViewModel, nav)
                    }
                },
            )
        },
        bottomBar = {
            if (listing != null) {
                GeocacheActionBar(
                    listing = listing,
                    address = targetAddress,
                    logs = logs,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onOpenSheet = { sheet = it },
                    onAddToHunt = { addingToHunt = true },
                )
            }
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            if (listing == null) {
                LoadingPlaceholder()
            } else {
                GeocacheDetailBody(listing, logs, accountViewModel, nav)
            }
        }
    }

    if (addingToHunt) {
        GeocacheAddToHuntSheet(
            cache = targetAddress,
            accountViewModel = accountViewModel,
            onNewHunt = { nav.nav(Route.NewGeocacheHunt(seedCache = targetAddress.toValue())) },
            onDismiss = { addingToHunt = false },
        )
    }

    sheet?.let { type ->
        if (listing != null) {
            GeocacheLogSheet(
                type = type,
                listing = listing,
                accountViewModel = accountViewModel,
                onDismiss = { sheet = null },
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringRes(Res.string.geocache_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GeocacheDetailBody(
    listing: GeocacheListingEvent,
    logs: GeocacheLogs,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val palette = rememberGeocachePalette()
    val showImages = accountViewModel.settings.showImages()
    val name = remember(listing) { listing.cacheName()?.trim().orEmpty() }
    val point = remember(listing) { listing.geocachePoint() }
    val type = remember(listing) { listing.cacheType() }
    val hint = remember(listing) { listing.hintOnWire()?.trim()?.ifBlank { null } }
    val mission = remember(listing) { listing.mission()?.trim()?.ifBlank { null } }
    val images = remember(listing) { listing.images().mapNotNull { it.trim().ifBlank { null } } }
    val claimed = logs.winner != null
    val isOver = listing.isArchived() || claimed
    val accent = palette.forListing(listing, claimed)

    GeocacheDetailHero(
        name = name,
        photo = if (showImages) images.firstOrNull() else null,
        point = point,
        accent = accent,
        emoji = type.geocacheEmoji(),
        distance = distanceToCache(listing, accountViewModel),
        isOver = isOver,
        subtitle = {
            Text(
                text = listing.specSummary(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
        },
        image = { url, modifier ->
            MyAsyncImage(
                imageUrl = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier,
                loadedImageModifier = modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = null,
            )
        },
    ) { latitude, longitude, pinColor, pinEmoji, pinAlpha, aspectRatio ->
        LocationPreviewMap(
            latitude = latitude,
            longitude = longitude,
            aspectRatio = aspectRatio,
            pinColor = pinColor,
            pinEmoji = pinEmoji,
            pinAlpha = pinAlpha,
        )
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GeocacheOwnerRow(listing, accountViewModel, nav)

        GeocacheChips(
            claimed = claimed,
            isArchived = listing.isArchived(),
            isFirstToFind = listing.isFirstToFind(),
            isArt = listing.hasTypeModifier(TypeModifier.ART),
            hasMission = mission != null,
            needsVerification = listing.requiresVerification(),
        )

        if (listing.content.isNotBlank()) {
            Text(
                text = listing.content.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        hint?.let { GeocacheSpoilerHint(it) }

        mission?.let {
            GeocacheAccentPanel(palette.proven) {
                SectionLabel(stringRes(Res.string.geocache_mission_section))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        point?.let { (latitude, longitude) ->
            GeocacheNavigateRow(latitude, longitude, name)
        }

        // The hero already spends one photo; the rest belong in a strip rather than stacked, so
        // a cache with six pictures does not push its own logs off the screen.
        if (showImages && images.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringRes(Res.string.geocache_photos_section))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images.drop(1)) { url ->
                        MyAsyncImage(
                            imageUrl = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            mainImageModifier = Modifier,
                            loadedImageModifier = Modifier.size(150.dp).clip(RoundedCornerShape(10.dp)),
                            accountViewModel = accountViewModel,
                            onLoadingBackground = null,
                            onError = null,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(stringRes(Res.string.geocache_logs_section) + " · " + logs.all.size)

            if (logs.all.isEmpty()) {
                Text(
                    text = stringRes(Res.string.geocache_no_logs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logs.all.forEach { log ->
                    GeocacheLogRow(log, listing, accountViewModel, nav)
                }
            }
        }
    }
}

/**
 * A panel that carries one of the palette's colours as a left rule.
 *
 * Used where a block is a different *kind* of thing rather than merely the next paragraph — a
 * Key Quest mission, a proof notice. The rule costs 3dp and says "this one is special" without
 * spending a card, a border and a shadow to do it.
 */
@Composable
internal fun GeocacheAccentPanel(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // IntrinsicSize.Min so the rule's fillMaxHeight has a height to match. In a
            // wrap-content Row it would otherwise measure against an unbounded constraint and
            // collapse.
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(accent.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * "Navigate" hands the coordinates to whatever map app the user actually uses, rather than
 * pretending a feed client is a navigator. The `geo:` URI carries a `q` label so the pin in
 * their map is named after the cache.
 */
@Composable
private fun GeocacheNavigateRow(
    latitude: Double,
    longitude: Double,
    name: String,
) {
    val uriHandler = LocalUriHandler.current
    val unnamedLabel = stringRes(Res.string.geocache_unnamed)
    OutlinedButton(onClick = {
        val label = name.ifBlank { unnamedLabel }
        runCatching {
            uriHandler.openUri("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
        }
    }) {
        Icon(
            symbol = MaterialSymbols.MyLocation,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(6.dp))
        Text(stringRes(Res.string.geocache_navigate))
    }
}

/**
 * The bottom bar. Which one you get is the whole first-to-find and archive rule:
 * a cache that is over, or someone else's prize, offers a status line instead of a button.
 */
@Composable
private fun GeocacheActionBar(
    listing: GeocacheListingEvent,
    address: Address,
    logs: GeocacheLogs,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpenSheet: (GeocacheLogSheetType) -> Unit,
    onAddToHunt: () -> Unit,
) {
    val me = accountViewModel.userProfile().pubkeyHex
    val winner = logs.winner
    val outOfPlay = listing.isArchived() || (winner != null && winner != me)

    val palette = rememberGeocachePalette()

    Surface(tonalElevation = 3.dp) {
        Column(
            // navigationBarsPadding on the *content* rather than the Surface: the bar's
            // background still runs to the bottom of the screen, while the buttons sit above
            // the 3-button navigation instead of underneath it. Material3's Scaffold passes
            // its window insets to the content slot only -- a bottomBar is expected to handle
            // its own, which this one was not doing.
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The status line replaces the find buttons rather than sitting above them, so the
            // bar says one thing at a time: either this cache is yours to go and get, or here is
            // why it is not.
            when {
                listing.isArchived() ->
                    GeocacheAccentPanel(palette.over) {
                        StatusStrip(stringRes(Res.string.geocache_archived_notice))
                    }

                winner != null && winner != me ->
                    GeocacheAccentPanel(palette.over) {
                        GeocacheWinnerStrip(winner, accountViewModel)
                    }

                listing.isFirstToFind() ->
                    GeocacheAccentPanel(palette.prize) {
                        StatusStrip("🥇  " + stringRes(Res.string.geocache_ftf_available))
                    }
            }

            if (!outOfPlay && listing.pubKey != me) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { nav.nav(Route.LogGeocacheFind(address)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(Res.string.geocache_i_found_it)) }

                    OutlinedButton(
                        onClick = { onOpenSheet(GeocacheLogSheetType.DNF) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(Res.string.geocache_didnt_find_it)) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SmallAction(stringRes(Res.string.geocache_add_note)) { onOpenSheet(GeocacheLogSheetType.NOTE) }
                SmallAction(stringRes(Res.string.geocache_needs_maintenance)) { onOpenSheet(GeocacheLogSheetType.MAINTENANCE) }
                SmallAction(stringRes(Res.string.geocache_add_to_hunt)) { onAddToHunt() }
            }
        }
    }
}

@Composable
private fun StatusStrip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SmallAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_use_my_location
import com.vitorpamplona.amethyst.commons.resources.geohash_level_block
import com.vitorpamplona.amethyst.commons.resources.geohash_level_building
import com.vitorpamplona.amethyst.commons.resources.geohash_level_city
import com.vitorpamplona.amethyst.commons.resources.geohash_level_continent
import com.vitorpamplona.amethyst.commons.resources.geohash_level_neighborhood
import com.vitorpamplona.amethyst.commons.resources.geohash_level_province
import com.vitorpamplona.amethyst.commons.resources.geohash_level_region
import com.vitorpamplona.amethyst.commons.resources.geohash_new_enter_geohash
import com.vitorpamplona.amethyst.commons.resources.geohash_new_enter_hint
import com.vitorpamplona.amethyst.commons.resources.geohash_new_near_me
import com.vitorpamplona.amethyst.commons.resources.geohash_new_near_me_hint
import com.vitorpamplona.amethyst.commons.resources.geohash_new_pick_on_map
import com.vitorpamplona.amethyst.commons.resources.geohash_new_your_channels
import com.vitorpamplona.amethyst.commons.resources.geohash_teleport_title
import com.vitorpamplona.amethyst.commons.resources.join
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.new_geohash_chat_input_hint
import com.vitorpamplona.amethyst.commons.resources.new_geohash_chat_locating
import com.vitorpamplona.amethyst.commons.resources.new_geohash_chat_location_unavailable
import com.vitorpamplona.amethyst.commons.resources.new_geohash_chat_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberCoarseLocationPermission
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Builder for joining a Bitchat-interoperable geohash location channel. Ways in: a
 * level from the device's current location (region → building), a geohash typed by
 * hand, or a point picked on the map (teleport). Joining adds the cell to the
 * kind-10081 geohash list ([AccountViewModel.followGeohash]) so it shows up in
 * Messages and the location-channels list, then opens the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGeohashChatScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    fun joinAndOpen(geohash: String) {
        val cell = geohash.trim().lowercase()
        if (GeoHash.decode(cell) == null) return
        accountViewModel.followGeohash(cell)
        nav.popBack()
        nav.nav(Route.GeohashChat(cell))
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(Res.string.new_geohash_chat_title)) },
                nav = nav,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .padding(horizontal = 16.dp)
                // consume the scaffold insets so the trailing imePadding() only adds the part of
                // the IME the nav-bar padding above doesn't already cover (union, not a sum).
                .consumeWindowInsets(pad)
                .verticalScroll(rememberScrollState())
                .imePaddingSafe(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.padding(top = 8.dp))
            YourChannelsSection(accountViewModel, nav)
            NearMeSection(onOpen = ::joinAndOpen)
            ManualEntrySection(onOpen = ::joinAndOpen)
            TeleportCard(onClick = { nav.nav(Route.GeohashTeleport) })
            Box(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** A section title with a small tinted icon, e.g. "Near me". */
@Composable
private fun SectionHeader(
    icon: MaterialSymbol,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A location pin inside a soft tinted circle — the visual signature of a location channel. */
@Composable
private fun LocationChip(size: Int = 40) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SymbolIcon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

/** A tappable location card: pin chip, a bold title, a city/geohash subtitle, and a chevron or trailing slot. */
@Composable
private fun LocationCard(
    title: String,
    subtitle: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocationChip()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                subtitle()
            }
            if (trailing != null) {
                trailing()
            } else {
                SymbolIcon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubtitleText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun YourChannelsSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val joined by accountViewModel.account.geohashList.flow
        .collectAsStateWithLifecycle()
    if (joined.isEmpty()) return

    Column {
        SectionHeader(MaterialSymbols.Groups, stringRes(Res.string.geohash_new_your_channels))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            joined.sorted().forEach { cell ->
                LocationCard(
                    title = "#$cell",
                    subtitle = { LoadCityName(geohashStr = cell) { SubtitleText(it) } },
                    onClick = { nav.nav(Route.GeohashChat(cell)) },
                    trailing = {
                        TextButton(onClick = { accountViewModel.unfollowGeohash(cell) }) { Text(stringRes(Res.string.leave)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ManualEntrySection(onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = remember(text) { GeoHash.decode(text.trim().lowercase()) != null }

    Column {
        SectionHeader(MaterialSymbols.TravelExplore, stringRes(Res.string.geohash_new_enter_geohash))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringRes(Res.string.geohash_new_enter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringRes(Res.string.new_geohash_chat_input_hint)) },
                        isError = text.isNotBlank() && !valid,
                    )
                    FilledTonalButton(
                        enabled = valid,
                        onClick = { onOpen(text) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringRes(Res.string.join))
                    }
                }
            }
        }
    }
}

@Composable
private fun NearMeSection(onOpen: (String) -> Unit) {
    Column {
        SectionHeader(MaterialSymbols.Explore, stringRes(Res.string.geohash_new_near_me))

        val permission = rememberCoarseLocationPermission()
        val locationSource = LocalAppServices.current.deviceLocation
        LaunchedEffect(permission.isGranted) {
            locationSource.setLocationPermission(permission.isGranted)
        }

        if (!permission.isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringRes(Res.string.geohash_new_near_me_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    FilledTonalButton(onClick = { permission.launchRequest() }) {
                        SymbolIcon(symbol = MaterialSymbols.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.geocache_use_my_location))
                    }
                }
            }
            return
        }

        val location by LocalAppServices.current.deviceLocation.preciseGeohashStateFlow
            .collectAsStateWithLifecycle()
        when (val loc = location) {
            is LocationResult.Success -> {
                val fix = loc.geoHash.toString()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeohashChannelLevel.ordered.forEach { level ->
                        level.cellFor(fix)?.let { cell ->
                            LocationCard(
                                title = level.label(),
                                subtitle = { LoadCityName(geohashStr = cell) { SubtitleText("$it · #$cell") } },
                                onClick = { onOpen(cell) },
                            )
                        }
                    }
                }
            }

            LocationResult.Loading ->
                Text(stringRes(Res.string.new_geohash_chat_locating), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LocationResult.LackPermission ->
                Text(stringRes(Res.string.new_geohash_chat_location_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TeleportCard(onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SymbolIcon(
                        symbol = MaterialSymbols.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringRes(Res.string.geohash_teleport_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringRes(Res.string.geohash_new_pick_on_map),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            SymbolIcon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun GeohashChannelLevel.label(): String = stringRes(labelRes())

fun GeohashChannelLevel.labelRes(): StringResource =
    when (this) {
        GeohashChannelLevel.CONTINENT -> Res.string.geohash_level_continent
        GeohashChannelLevel.REGION -> Res.string.geohash_level_region
        GeohashChannelLevel.PROVINCE -> Res.string.geohash_level_province
        GeohashChannelLevel.CITY -> Res.string.geohash_level_city
        GeohashChannelLevel.NEIGHBORHOOD -> Res.string.geohash_level_neighborhood
        GeohashChannelLevel.BLOCK -> Res.string.geohash_level_block
        GeohashChannelLevel.BUILDING -> Res.string.geohash_level_building
    }

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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_empty_description
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_empty_title
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_explore_nearby
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_intro_description
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_intro_title
import com.vitorpamplona.amethyst.commons.resources.geohash_chats_open
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.location_channels
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The dedicated top-level list of the user's joined Bitchat-interoperable location channels — the
 * geohash-chat analogue of [com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.publicChats.PublicChatsScreen]
 * (NIP-28) and the Relay Groups / Concord home screens. Reached from the drawer's "Feeds" section and
 * pinnable as a bottom-bar tab. Each row opens the cell's chat; the "+" opens [NewGeohashChatScreen] to
 * join more (near me / manual / teleport).
 */
@Composable
fun GeohashChatsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            // No seed: this lists every location channel, so there is no one geohash to carry.
            UserDrawerSearchTopBar(accountViewModel, nav, null) {
                Text(stringRes(Res.string.location_channels), fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            AppBottomBar(Route.GeohashChats, nav, accountViewModel) { route ->
                nav.navBottomBar(route)
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(
                    onClick = { nav.nav(Route.NewGeohashChat) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    SymbolIcon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.location_channels),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) { pad ->
        val joined by accountViewModel.account.geohashList.flow
            .collectAsStateWithLifecycle()
        val cells = remember(joined) { joined.sorted() }

        if (cells.isEmpty()) {
            EmptyLocationChannels(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
                onExplore = { nav.nav(Route.NewGeohashChat) },
            )
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = pad.calculateTopPadding() + 12.dp,
                        bottom = pad.calculateBottomPadding() + 88.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero") { LocationChannelsHero() }
                items(cells, key = { it }) { cell ->
                    LocationChannelRow(
                        cell = cell,
                        onOpen = { nav.nav(Route.GeohashChat(cell)) },
                        onLeave = { accountViewModel.unfollowGeohash(cell) },
                    )
                }
            }
        }
    }
}

/** A gradient banner that gives the screen its identity: what location channels are, in one glance. */
@Composable
private fun LocationChannelsHero() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SymbolIcon(
                        symbol = MaterialSymbols.Public,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    stringRes(Res.string.geohash_chats_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    stringRes(Res.string.geohash_chats_intro_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** One joined cell: gradient location pin, "#cell", its precision level + resolved city, and an overflow menu. */
@Composable
private fun LocationChannelRow(
    cell: String,
    onOpen: () -> Unit,
    onLeave: () -> Unit,
) {
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocationPin()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "#$cell",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                LevelAndCity(cell)
            }
            LocationChannelMenu(onOpen = onOpen, onLeave = onLeave)
        }
    }
}

/** The circular gradient avatar with a location pin — the visual signature of a location channel. */
@Composable
private fun LocationPin() {
    Box(
        Modifier
            .size(48.dp)
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
    ) {
        SymbolIcon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A subtle "City · Palo Alto" subtitle: the named precision level (from the cell length) then the resolved place. */
@Composable
private fun LevelAndCity(cell: String) {
    val level = GeohashChannelLevel.forChars(cell.length)?.label()
    LoadCityName(geohashStr = cell) { city ->
        val text = if (level != null) "$level · $city" else city
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LocationChannelMenu(
    onOpen: () -> Unit,
    onLeave: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            SymbolIcon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.geohash_chats_open)) },
                onClick = {
                    expanded = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.leave)) },
                onClick = {
                    expanded = false
                    onLeave()
                },
            )
        }
    }
}

/** Shown before the user has joined any cell: an inviting call to explore nearby. */
@Composable
private fun EmptyLocationChannels(
    modifier: Modifier,
    onExplore: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
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
        ) {
            SymbolIcon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringRes(Res.string.geohash_chats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringRes(Res.string.geohash_chats_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onExplore) {
            SymbolIcon(symbol = MaterialSymbols.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.geohash_chats_explore_nearby))
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.live

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * A "live near you" bubble for a geohash location channel with recent activity.
 * Anonymous by design — no participant avatars, just the cell and a location pin.
 */
@Composable
fun RenderGeohashBubble(
    channel: GeohashChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    FilledTonalButton(
        contentPadding = PaddingValues(start = 8.dp, end = 10.dp, bottom = 0.dp, top = 0.dp),
        onClick = { nav.nav(Route.GeohashChat(channel.geohash)) },
    ) {
        SymbolIcon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterVertically).size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(StdHorzSpacer)
        // Users care about the place, not the geohash code — resolve the cell to a city name, falling
        // back to the "#geohash" display name while the reverse-geocode is in flight (or unavailable).
        LoadCityName(
            geohashStr = channel.geohash,
            onLoading = { Text(channel.toBestDisplayName()) },
        ) { city ->
            Text(city)
        }
    }
}

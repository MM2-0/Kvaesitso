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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geohash_teleport_action
import com.vitorpamplona.amethyst.commons.resources.geohash_teleport_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerContent
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Teleport: pick a point on the map to join a remote geohash cell (at a chosen
 * precision level) you are not physically in. Joining follows the cell (kind
 * 10081) and opens the chat with the teleport flag set, so outgoing messages
 * carry the ["t","teleport"] marker.
 *
 * The map/search/precision UI is the shared [GeohashLocationPickerContent]; this
 * screen only supplies the top bar and the teleport-specific confirm action.
 */
@Composable
fun GeohashTeleportScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(Res.string.geohash_teleport_title)) },
                nav = nav,
            )
        },
    ) { pad ->
        GeohashLocationPickerContent(
            initialGeohash = null,
            confirmLabel = stringRes(Res.string.geohash_teleport_action),
            onConfirm = { cell ->
                accountViewModel.followGeohash(cell)
                nav.popBack()
                nav.nav(Route.GeohashChat(cell, teleported = true))
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
        )
    }
}

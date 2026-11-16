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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.routstr_card_use
import com.vitorpamplona.amethyst.commons.resources.routstr_mints_count
import com.vitorpamplona.amethyst.commons.resources.routstr_onion_only
import com.vitorpamplona.amethyst.commons.routstr.RoutstrNode
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent

/**
 * A Routstr node announcement (kind 38421): its name, about and endpoints, and "Use for The
 * Observer", which opens it in the Routstr setup. Nothing here contacts the node.
 */
@Composable
fun RenderRoutstrNode(
    baseNote: Note,
    nav: INav,
) {
    val event = baseNote.event as? RoutstrNodeEvent ?: return
    val node = remember(event) { RoutstrNode.from(event) }
    val endpoints =
        remember(event) {
            listOfNotNull(
                node.httpsUrl?.let { AgentProviderPresets.hostOf(it) },
                node.onionUrl?.let { (AgentProviderPresets.hostOf(it)?.take(12) ?: "") + "….onion" },
            )
        }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = node.name ?: kindName(event.kind),
        subtitle = node.about,
    ) {
        KindCardChips(endpoints + pluralStringRes(Res.plurals.routstr_mints_count, node.mints.size, node.mints.size))
        if (node.hasHttps) {
            OutlinedButton(onClick = { nav.nav(Route.RoutstrSetup(node = node.address)) }) {
                Text(stringRes(Res.string.routstr_card_use))
            }
        } else {
            Text(stringRes(Res.string.routstr_onion_only))
        }
    }
}

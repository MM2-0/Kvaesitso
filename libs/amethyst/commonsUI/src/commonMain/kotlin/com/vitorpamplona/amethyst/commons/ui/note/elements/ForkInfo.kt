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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.forked_from
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.QuietMark
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent

@Composable
fun ShowForkInformation(
    noteEvent: IForkableEvent,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val forkedAddress = remember(noteEvent) { noteEvent.forkFromAddress() }
    val forkedEvent = remember(noteEvent) { noteEvent.forkFromVersion() }
    if (forkedAddress != null) {
        LoadAddressableNote(forkedAddress) { addressableNote ->
            if (addressableNote != null) {
                ForkMark(addressableNote, modifier, accountViewModel, nav)
            }
        }
    } else if (forkedEvent != null) {
        LoadNote(forkedEvent) { event ->
            if (event != null) {
                ForkMark(event, modifier, accountViewModel, nav)
            }
        }
    }
}

/** Fork-right icon marking a forked note; tapping opens the original version. */
@Composable
fun ForkMark(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(originalVersion, accountViewModel)
    val note = noteState.note
    val route = remember(note) { routeFor(note, accountViewModel.account) }

    if (route != null) {
        QuietMark(
            symbol = MaterialSymbols.ForkRight,
            contentDescription = stringRes(id = Res.string.forked_from),
            modifier = modifier,
            onClick = { nav.nav(route) },
        )
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.redirect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteLocally
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.looking_for_event
import com.vitorpamplona.amethyst.commons.resources.looking_for_event_title
import com.vitorpamplona.amethyst.commons.resources.looking_for_private_event
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * The waiting room for an event we can only name: hold here until it arrives, then replace
 * this entry with wherever it actually belongs.
 *
 * It is a real screen, not a bare label. A notification tap on a cold process lands here
 * (LocalCache is empty until the event is re-fetched), and with nothing but a line of text
 * on `colorScheme.background` — black on the dark theme — the wait read as the app opening
 * to a black screen, with no top bar and no way back other than the system gesture.
 */
@Composable
fun LoadRedirectScreen(
    eventId: String?,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (eventId == null) return

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(Res.string.looking_for_event_title)) },
                nav = nav,
            )
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()

            LoadNote(eventId) { note ->
                Text(
                    // A private id is not something to show a user, and it must not be
                    // copyable off the screen either.
                    text =
                        if (isPrivate) {
                            stringRes(Res.string.looking_for_private_event)
                        } else {
                            stringRes(Res.string.looking_for_event, note?.idHex ?: eventId)
                        },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )

                note?.let {
                    WatchAndRedirect(
                        baseNote = it,
                        isPrivate = isPrivate,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/**
 * Renders nothing: it only watches [baseNote] and pops this entry for the real destination
 * the moment the event lands.
 *
 * A public note is watched through [observeNote], which also holds a relay subscription open
 * so the note is actually fetched. A private one is watched through [observeNoteLocally],
 * which asks no relay: a rumor id must never appear in a REQ, and it does not need to — the
 * envelope carrying it is re-fetched by the always-on gift-wrap tail, and unwrapping it fires
 * this flow.
 */
@Composable
private fun WatchAndRedirect(
    baseNote: Note,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by if (isPrivate) observeNoteLocally(baseNote) else observeNote(baseNote, accountViewModel)

    LaunchedEffect(key1 = noteState) {
        val event = noteState.note.event
        if (event != null) {
            withContext(Dispatchers.IO) {
                routeFor(event, accountViewModel.account)?.let { route ->
                    nav.popUpTo(route, Route.EventRedirect::class)
                }
            }
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pinnednotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.pinnednotes.dal.PinnedNotesFeedViewModel
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pinned_notes
import com.vitorpamplona.amethyst.commons.ui.components.DeletedItemsBanner
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PinnedNotesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pinnedNotesFeedViewModel: PinnedNotesFeedViewModel =
        rememberViewModel(
            key = "NostrPinnedNotesFeedViewModel",
            factory = PinnedNotesFeedViewModel.Factory(accountViewModel.account),
        )

    val pinState by accountViewModel.account.pinState.pinnedNotesList
        .collectAsStateWithLifecycle(null)

    LaunchedEffect(pinState) {
        pinnedNotesFeedViewModel.invalidateData()
    }

    // Preload all pinned events so they don't load one-by-one when scrolling
    PreloadPinnedEvents(pinState, accountViewModel)

    RenderPinnedNotesScreen(pinnedNotesFeedViewModel, pinState, accountViewModel, nav)
}

@Composable
private fun RenderPinnedNotesScreen(
    pinnedNotesFeedViewModel: PinnedNotesFeedViewModel,
    pinState: List<Note>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var bannerDismissed by remember { mutableStateOf(false) }
    val deletedPins =
        remember(pinState) {
            pinState
                ?.filter { note ->
                    note.event?.let(accountViewModel.account.cache::hasBeenDeleted) == true
                }.orEmpty()
        }

    LaunchedEffect(deletedPins) {
        if (deletedPins.isEmpty()) bannerDismissed = false
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.pinned_notes), nav)
        },
        accountViewModel = accountViewModel,
    ) { paddingValues ->
        // Feed fills the screen so items scroll behind the top bar (via
        // rememberFeedContentPadding); banner overlays at the top, offset down
        // by the scaffold's top padding so it sits below the bar.
        Box(modifier = Modifier.fillMaxSize()) {
            RefresheableFeedView(
                pinnedNotesFeedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            if (!bannerDismissed && deletedPins.isNotEmpty()) {
                DeletedItemsBanner(
                    count = deletedPins.size,
                    onRemove = {
                        accountViewModel.removeDeletedPins(deletedPins.toSet())
                        bannerDismissed = true
                    },
                    onDismiss = { bannerDismissed = true },
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                )
            }
        }
    }
}

@Composable
private fun PreloadPinnedEvents(
    pinState: List<Note>?,
    accountViewModel: AccountViewModel,
) {
    val eventFinder = accountViewModel.dataSources().eventFinder
    val account = accountViewModel.account

    val queries =
        remember(pinState) {
            pinState
                .orEmpty()
                .filter { it.event == null }
                .map { EventFinderQueryState(it, account) }
        }

    DisposableEffect(queries) {
        eventFinder.subscribe(queries)
        onDispose {
            eventFinder.unsubscribe(queries)
        }
    }
}

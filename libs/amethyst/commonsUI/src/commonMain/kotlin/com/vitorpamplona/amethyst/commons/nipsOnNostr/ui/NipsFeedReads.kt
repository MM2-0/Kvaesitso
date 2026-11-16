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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nipsOnNostr.definesKind
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.datasource.NipsDefiningKindSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent

/** A feed's notes as they stand, and whether it is still loading its first page. */
@Composable
internal fun feedNotes(feedState: FeedContentState): Pair<List<Note>, Boolean> {
    val state by feedState.feedContent.collectAsStateWithLifecycle()
    return when (val current = state) {
        is FeedState.Loaded -> {
            val loaded by current.feed.collectAsStateWithLifecycle()
            loaded.list to false
        }
        is FeedState.Loading -> emptyList<Note>() to true
        else -> emptyList<Note>() to false
    }
}

/**
 * The NIPs on Nostr that define [kind], newest first, as the account's NIPs feed holds them: by
 * authors it doesn't hide and past its spam rules. Every caller shares that one feed, so a screen
 * full of unknown events reads one list instead of watching the cache once per card. Keeps the
 * kind's definitions arriving from the relays while it is on screen.
 */
@Composable
fun rememberNipsDefining(
    kind: Int,
    accountViewModel: AccountViewModel,
): List<Note> {
    NipsDefiningKindSubscription(kind, accountViewModel)
    val feedState = accountViewModel.feedStates.nipsFeed
    // The feed loads when its screen opens; read here first, it is loaded once for every reader.
    LaunchedEffect(feedState) {
        if (feedState.feedContent.value is FeedState.Loading) feedState.invalidateData(true)
    }
    val (nips, _) = feedNotes(feedState)
    return remember(nips, kind) {
        nips.filter { note -> (note.event as? NipTextEvent)?.let { definesKind(it.tags, kind) } == true }
    }
}

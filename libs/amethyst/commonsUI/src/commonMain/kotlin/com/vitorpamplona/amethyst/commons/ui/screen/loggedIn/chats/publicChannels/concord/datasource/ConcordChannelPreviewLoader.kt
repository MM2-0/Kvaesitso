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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.delay

/**
 * One-shot warm of the last-message preview of **every channel in one community** when its screen is
 * open, so the channel list fills in without the user tapping into each channel.
 *
 * Not a live subscription: it drains one `limit:1` REQ per channel once (the wraps ingest through the
 * normal cache path and the always-on plane subscription keeps them fresh afterward — see
 * [com.vitorpamplona.amethyst.commons.model.Account.warmConcordChannelPreviews]). It re-runs when a fold
 * changes the channel set, debounced so the cold-boot burst of fold revisions coalesces into one warm.
 */
@Composable
fun ConcordChannelPreviewLoader(
    communityId: String,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()

    LaunchedEffect(communityId, revision) {
        // Let a burst of folds settle before draining, and coalesce repeated revisions into one warm.
        delay(800)
        val entry =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId } ?: return@LaunchedEffect
        account.concord.warmConcordChannelPreviews(listOf(entry))
    }
}

/**
 * Always-on account-level warm of channel previews for **every subscribed community**, mounted once
 * high in the logged-in tree from `ConcordChannelPreload`. This is why the Messages inbox shows a last
 * message for channels the user never opened — one drain covers all joined communities at once.
 *
 * A single [com.vitorpamplona.amethyst.commons.model.Account.warmConcordChannelPreviews] call groups every
 * community's per-channel `limit:1` filters by relay, so there is one REQ per relay (not one live
 * subscription per community). Debounced on the fold revision so the cold-boot burst warms once.
 */
@Composable
fun ConcordChannelPreviewAccountPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()

    LaunchedEffect(communities, revision) {
        // Debounce the cold-boot burst of fold revisions (and any join/leave churn) into one drain.
        delay(1500)
        account.concord.warmConcordChannelPreviews(communities)
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.OnlineStatusCache
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadLiveActivityChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header.LiveActivityTopBar
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Composable
fun LiveActivityChannelScreen(
    channelId: Address?,
    draftId: HexKey? = null,
    replyToId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    val draft = remember(draftId) { draftId?.let { LocalCache.getNoteIfExists(it) } }
    val replyTo = remember(replyToId) { replyToId?.let { LocalCache.checkGetOrCreateNote(it) } }

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            LoadLiveActivityChannel(channelId) {
                LaunchedEffect(it.info) {
                    it.info?.streaming()?.let {
                        OnlineStatusCache.resetIfOfflineToRetry(it)
                    }
                }
                LiveActivityTopBar(it, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) {
        Column(Modifier.padding(it)) {
            LiveActivityChannelView(channelId, draft, replyTo, accountViewModel, nav)
        }
    }
}

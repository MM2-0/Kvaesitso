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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.rememberCallStarter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.ChatroomView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.RenderRoomTopBar
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType

@Composable
fun ChatroomScreen(
    roomId: ChatroomKey,
    draftMessage: String? = null,
    attachmentUri: String? = null,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Off with the Calls setting, and with NIP-17, whose gift-wrap inbox carries the call signalling.
    val toggles = accountViewModel.account.chatFeedToggles
    val callingActive by toggles.callingActive.collectAsState(toggles.isCallingActive())
    val peers = roomId.users
    // Null where the platform cannot place calls; the buttons are hidden then.
    val startVoiceCall = rememberCallStarter(peers, CallType.VOICE)
    val startVideoCall = rememberCallStarter(peers, CallType.VIDEO)
    val isCallSupported = roomId.users.size <= 5 && callingActive

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            RenderRoomTopBar(
                room = roomId,
                accountViewModel = accountViewModel,
                nav = nav,
                onCallClick = if (isCallSupported && startVoiceCall != null) ({ _ -> startVoiceCall() }) else null,
                onVideoCallClick = if (isCallSupported && startVideoCall != null) ({ _ -> startVideoCall() }) else null,
            )
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) {
        Column(Modifier.padding(it)) {
            ChatroomView(
                room = roomId,
                draftMessage = draftMessage,
                attachmentUri = attachmentUri,
                replyToNote = replyToNote,
                editFromDraft = editFromDraft,
                expiresDays = expiresDays,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

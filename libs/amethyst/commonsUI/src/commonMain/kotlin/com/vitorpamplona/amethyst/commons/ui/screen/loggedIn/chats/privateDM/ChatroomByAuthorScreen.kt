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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.RenderRoomTopBar
import com.vitorpamplona.amethyst.commons.ui.theme.BottomTopHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.collections.immutable.persistentSetOf

@Composable
fun ChatroomByAuthorScreen(
    authorPubKeyHex: String?,
    draftMessage: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (authorPubKeyHex == null) return

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            RoomByAuthorTopBar(authorPubKeyHex, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) {
        Column(Modifier.padding(it)) {
            ChatroomByAuthor(authorPubKeyHex, draftMessage, accountViewModel, nav)
        }
    }
}

@Composable
fun LoadRoomByAuthor(
    authorPubKeyHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (ChatroomKey?) -> Unit,
) {
    val room by remember(authorPubKeyHex) {
        mutableStateOf<ChatroomKey?>(ChatroomKey(persistentSetOf(authorPubKeyHex)))
    }

    content(room)
}

@Composable
private fun RoomByAuthorTopBar(
    authorPubKeyHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadRoomByAuthor(authorPubKeyHex = authorPubKeyHex, accountViewModel) { room ->
        if (room != null) {
            RenderRoomTopBar(room, accountViewModel, nav)
        } else {
            Spacer(BottomTopHeight)
        }
    }
}

@Composable
fun ChatroomByAuthor(
    authorPubKeyHex: String?,
    draftMessage: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (authorPubKeyHex == null) return

    LoadRoomByAuthor(authorPubKeyHex, accountViewModel) {
        it?.let {
            ChatroomView(
                room = it,
                draftMessage = draftMessage,
                replyToNote = null,
                editFromDraft = null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

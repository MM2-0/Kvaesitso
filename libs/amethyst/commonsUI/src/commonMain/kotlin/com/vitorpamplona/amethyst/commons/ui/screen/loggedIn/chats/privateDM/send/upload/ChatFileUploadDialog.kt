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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.RoomNameOnlyDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun RoomChatFileUploadDialog(
    channelScreenModel: ChatNewMessageViewModel,
    state: ChatFileUploadState,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val roomState = channelScreenModel.room.collectAsStateWithLifecycle()
    val room = roomState.value ?: return

    ChatFileUploadDialog(
        state,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NonClickableUserPictures(
                    room = room,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                RoomNameOnlyDisplay(room, Modifier.padding(start = 10.dp), FontWeight.Normal, accountViewModel)
            }
        },
        upload = {
            channelScreenModel.uploadAndSend(
                onError = accountViewModel.toastManager::toast,
                uploader = accountViewModel.mediaUploader,
                onceUploaded = onUpload,
            )

            accountViewModel.account.settings.changeDefaultFileServer(state.selectedServer)
            accountViewModel.account.settings.changeStripLocationOnUpload(state.stripMetadata)
        },
        onCancel,
        accountViewModel,
        nav,
        isNip17 = true,
    )
}

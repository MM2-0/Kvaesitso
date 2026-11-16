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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

@Composable
fun ChatroomHeader(
    room: ChatroomKey,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    if (room.users.size == 1) {
        LoadUser(baseUserHex = room.users.first()) { baseUser ->
            if (baseUser != null) {
                UserChatroomHeader(
                    baseUser = baseUser,
                    modifier = modifier,
                    accountViewModel = accountViewModel,
                    onClick = onClick,
                )
            }
        }
    } else {
        GroupChatroomHeader(
            room = room,
            modifier = modifier,
            accountViewModel = accountViewModel,
            onClick = onClick,
        )
    }
}

@Composable
fun UserChatroomHeader(
    baseUser: User,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
            ),
    ) {
        Column(modifier, Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableUserPicture(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                Column(
                    modifier =
                        Modifier
                            .padding(start = 10.dp)
                            .weight(1f),
                ) {
                    UsernameDisplay(baseUser, accountViewModel = accountViewModel)
                }
            }
        }
    }
}

@Composable
fun GroupChatroomHeader(
    room: ChatroomKey,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NonClickableUserPictures(
                    room = room,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                RoomNameOnlyDisplay(room, Modifier.padding(start = 10.dp), FontWeight.Bold, accountViewModel)
            }
        }
    }
}

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

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlin.math.min

@Composable
fun RoomNameOnlyDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    accountViewModel: AccountViewModel,
) {
    // Subscribe in the LocalCache for changes that arrive in the device
    val roomSubject by accountViewModel.account.chatroomList
        .getOrCreatePrivateChatroom(room)
        .subject
        .collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = roomSubject, modifier = modifier) {
        if (!it.isNullOrBlank()) {
            DisplayRoomSubject(it, fontWeight)
        } else {
            DisplayUserSetAsSubject(room, accountViewModel, FontWeight.Normal)
        }
    }
}

@Composable
fun DisplayUserSetAsSubject(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val userList = remember(room) { room.users.toList() }
    DisplayUserSetAsSubject(userList, accountViewModel, fontWeight)
}

@Composable
fun DisplayUserSetAsSubject(
    userList: List<HexKey>,
    accountViewModel: AccountViewModel,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    if (userList.size == 1) {
        // Regular Design
        Row {
            LoadUser(baseUserHex = userList[0]) {
                it?.let { UsernameDisplay(it, Modifier.weight(1f), fontWeight = fontWeight, accountViewModel = accountViewModel) }
            }
        }
    } else {
        Row {
            userList.take(4).forEachIndexed { index, value ->
                LoadUser(baseUserHex = value) {
                    it?.let { ShortUsernameDisplay(baseUser = it, fontWeight = fontWeight, accountViewModel = accountViewModel) }
                }

                if (min(userList.size, 4) - 1 != index) {
                    Text(
                        text = ", ",
                        fontWeight = fontWeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun RoomNameDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    // The 1:1 counterpart, resolved once per row by the caller. Null for group rooms, which
    // resolve every member on their own via DisplayUserSetAsSubject. Deliberately has no default:
    // this function no longer self-resolves, so a caller must decide explicitly rather than
    // silently rendering a blank 1:1 name.
    preloadedUser: User?,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val roomSubject by accountViewModel.account.chatroomList
        .getOrCreatePrivateChatroom(room)
        .subject
        .collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = roomSubject, modifier = modifier) {
        if (!it.isNullOrBlank()) {
            if (room.users.size > 1) {
                DisplayRoomSubject(it, fontWeight)
            } else {
                DisplayUserAndSubject(it, accountViewModel, preloadedUser, fontWeight)
            }
        } else if (room.users.size == 1) {
            Row {
                preloadedUser?.let { UsernameDisplay(it, Modifier.weight(1f), fontWeight = fontWeight, accountViewModel = accountViewModel) }
            }
        } else {
            DisplayUserSetAsSubject(room, accountViewModel, fontWeight)
        }
    }
}

@Composable
fun DisplayRoomSubject(
    roomSubject: String,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Row {
        Text(
            text = roomSubject,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

@Composable
private fun DisplayUserAndSubject(
    subject: String,
    accountViewModel: AccountViewModel,
    preloadedUser: User?,
    fontWeight: FontWeight,
) {
    Row {
        Text(
            text = subject,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = " - ",
            fontWeight = fontWeight,
            maxLines = 1,
        )
        preloadedUser?.let { UsernameDisplay(it, Modifier.weight(1f), fontWeight = fontWeight, accountViewModel = accountViewModel) }
    }
}

@Composable
fun ShortUsernameDisplay(
    baseUser: User,
    weight: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    accountViewModel: AccountViewModel,
) {
    val userInfo by observeUserInfo(baseUser, accountViewModel)

    val firstName =
        remember(userInfo) {
            userInfo?.info?.firstName() ?: baseUser.pubkeyDisplayHex()
        }

    CrossfadeIfEnabled(targetState = firstName, modifier = weight) {
        CreateTextWithEmoji(
            text = it,
            tags = userInfo?.tags,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

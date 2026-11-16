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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_video
import com.vitorpamplona.amethyst.commons.resources.call_voice
import com.vitorpamplona.amethyst.commons.resources.chat_room_member_count
import com.vitorpamplona.amethyst.commons.resources.edits_the_channel_metadata
import com.vitorpamplona.amethyst.commons.resources.messages_group_descriptor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.ChatRoomFaces
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.RoomNameOnlyDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.collections.immutable.toPersistentList

@Composable
fun RenderRoomTopBar(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onCallClick: ((String) -> Unit)? = null,
    onVideoCallClick: ((String) -> Unit)? = null,
) {
    if (room.users.size == 1) {
        TopBarExtensibleWithBackButton(
            title = {
                LoadUser(baseUserHex = room.users.first()) { baseUser ->
                    if (baseUser != null) {
                        ClickableUserPicture(
                            baseUser = baseUser,
                            accountViewModel = accountViewModel,
                            size = Size34dp,
                        )

                        Spacer(modifier = DoubleHorzSpacer)

                        UsernameDisplay(baseUser, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)

                        if (onVideoCallClick != null) {
                            IconButton(
                                onClick = { onVideoCallClick(baseUser.pubkeyHex) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.Videocam,
                                    contentDescription = stringRes(Res.string.call_video),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (onCallClick != null) {
                            IconButton(
                                onClick = { onCallClick(baseUser.pubkeyHex) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.Call,
                                    contentDescription = stringRes(Res.string.call_voice),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            extendableRow = {
                LoadUser(baseUserHex = room.users.first()) {
                    if (it != null) {
                        UserCompose(
                            baseUser = it,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            },
            nav = nav,
        )
    } else {
        TopBarExtensibleWithBackButton(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Two overlapping faces without badges, as on the Messages list: a 2x2
                    // mosaic with a trust score on each face is unreadable at header size.
                    ChatRoomFaces(
                        userHexList = remember(room) { room.users.toList() },
                        size = Size34dp,
                        accountViewModel = accountViewModel,
                    )

                    // The name, then who is in it: a group header with only a name gave no
                    // hint of how many people would read what you type.
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        RoomNameOnlyDisplay(room, Modifier, FontWeight.SemiBold, accountViewModel)
                        Text(
                            // room.users is everyone but me.
                            text = pluralStringRes(Res.plurals.chat_room_member_count, room.users.size + 1, room.users.size + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                        )
                    }

                    if (onVideoCallClick != null) {
                        IconButton(
                            onClick = { onVideoCallClick(room.users.joinToString(",")) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                symbol = MaterialSymbols.Videocam,
                                contentDescription = stringRes(Res.string.call_video),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (onCallClick != null) {
                        IconButton(
                            onClick = { onCallClick(room.users.joinToString(",")) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                symbol = MaterialSymbols.Call,
                                contentDescription = stringRes(Res.string.call_voice),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
            extendableRow = {
                GroupMembersHeader(room = room, accountViewModel = accountViewModel, nav = nav)
            },
            nav = nav,
        )
    }
}

@Composable
fun GroupMembersHeader(
    room: ChatroomKey,
    lineModifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list = remember(room) { room.users.toPersistentList() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = Res.string.messages_group_descriptor),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        EditRoomSubjectButton(room, accountViewModel)
    }

    LazyColumn(
        modifier = Modifier,
        state = rememberLazyListState(),
    ) {
        itemsIndexed(list, key = { _, item -> item }) { _, item ->
            LoadUser(baseUserHex = item) {
                if (it != null) {
                    UserCompose(
                        baseUser = it,
                        modifier = lineModifier,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRoomSubjectButton(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
) {
    var wantsToPost by remember { mutableStateOf(false) }

    if (wantsToPost) {
        NewChatroomSubjectDialog({ wantsToPost = false }, accountViewModel, room)
    }

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { wantsToPost = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            symbol = MaterialSymbols.EditNote,
            contentDescription = stringRes(Res.string.edits_the_channel_metadata),
        )
    }
}

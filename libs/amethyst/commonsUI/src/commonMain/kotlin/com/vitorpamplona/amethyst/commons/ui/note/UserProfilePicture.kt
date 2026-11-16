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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsScore
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.profile_image_of_user
import com.vitorpamplona.amethyst.commons.resources.unknown_author
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.FollowingIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.ScoreTag
import com.vitorpamplona.amethyst.commons.ui.note.hovercard.UserHoverCardAnchor
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAuthorWithBlank(baseNote, accountViewModel = accountViewModel) {
        if (it == null) {
            DisplayBlankAuthor(size, pictureModifier, accountViewModel)
        } else {
            UserPicture(it, size, pictureModifier, accountViewModel, nav)
        }
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
) {
    WatchAuthorWithBlank(baseNote, accountViewModel = accountViewModel) {
        if (it == null) {
            DisplayBlankAuthor(size, modifier, accountViewModel)
        } else {
            ClickableUserPicture(it, size, accountViewModel, modifier, onClick)
        }
    }
}

@Composable
fun DisplayBlankAuthor(
    size: Dp,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    val nullModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape)
        }

    RobohashAsyncImage(
        robot = "authornotfound",
        contentDescription = stringRes(Res.string.unknown_author),
        modifier = nullModifier,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
    )
}

@Composable
fun UserPicture(
    userHex: String,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = userHex) {
        if (it != null) {
            UserPicture(
                user = it,
                size = size,
                pictureModifier = pictureModifier,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            DisplayBlankAuthor(
                size,
                pictureModifier,
                accountViewModel,
            )
        }
    }
}

@Composable
fun UserPicture(
    user: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserHoverCardAnchor(user, accountViewModel, nav) {
        ClickableUserPicture(
            baseUser = user,
            size = size,
            accountViewModel = accountViewModel,
            modifier = pictureModifier,
            onClick = { nav.nav(routeFor(it)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null,
) {
    // BaseUser is the same reference as accountState.user
    val myModifier =
        remember(baseUser) {
            if (onClick != null && onLongClick != null) {
                Modifier
                    .size(size)
                    .combinedClickable(
                        onClick = { onClick(baseUser) },
                        onLongClick = { onLongClick(baseUser) },
                    )
            } else if (onClick != null) {
                Modifier
                    .size(size)
                    .clickable(
                        onClick = { onClick(baseUser) },
                    )
            } else {
                Modifier.size(size)
            }
        }

    BaseUserPicture(baseUser, size, accountViewModel, modifier, myModifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableUserPicture(
    baseUserHex: HexKey,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((HexKey) -> Unit)? = null,
    onLongClick: ((HexKey) -> Unit)? = null,
) {
    // BaseUser is the same reference as accountState.user
    val myModifier =
        remember(baseUserHex) {
            if (onClick != null && onLongClick != null) {
                Modifier
                    .size(size)
                    .combinedClickable(
                        onClick = { onClick(baseUserHex) },
                        onLongClick = { onLongClick(baseUserHex) },
                    )
            } else if (onClick != null) {
                Modifier
                    .size(size)
                    .clickable(
                        onClick = { onClick(baseUserHex) },
                    )
            } else {
                Modifier.size(size)
            }
        }

    BaseUserPicture(baseUserHex, size, accountViewModel, modifier, myModifier)
}

/**
 * The faces of a chat room (Messages-list rows, chat headers). A 1:1 room shows its counterpart;
 * a group shows at most two faces, overlapped, instead of a 2x2 mosaic. Every face keeps the
 * usual follow mark and NIP-85 trust score, as [BaseUserPicture] draws them everywhere else.
 */
@Composable
fun ChatRoomFaces(
    userHexList: List<HexKey>,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    when (userHexList.size) {
        0 -> {}

        1 -> {
            LoadUser(baseUserHex = userHexList.first()) {
                it?.let { BaseUserPicture(it, size, accountViewModel, outerModifier = Modifier.size(size)) }
            }
        }

        else -> {
            val face = size * 0.68f
            val ring = 2.dp
            Box(Modifier.size(size)) {
                Box(Modifier.align(Alignment.TopStart)) {
                    BaseUserPicture(userHexList[0], face, accountViewModel)
                }
                // The front face sits in a disc of the background color, which cuts a clean ring
                // out of the face behind it instead of letting the two pictures smear together.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(face + ring * 2)
                            .background(MaterialTheme.colorScheme.background, CircleShape),
                ) {
                    BaseUserPicture(userHexList[1], face, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun NonClickableUserPictures(
    room: ChatroomKey,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    NonClickableUserPictures(room.users.toList(), size, accountViewModel)
}

@Composable
fun NonClickableUserPictures(
    userHexList: List<HexKey>,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
        when (userHexList.size) {
            0 -> {}

            1 -> {
                LoadUser(baseUserHex = userHexList.first()) {
                    it?.let { BaseUserPicture(it, size, accountViewModel, outerModifier = Modifier) }
                }
            }

            2 -> {
                val userList = userHexList

                LoadUser(baseUserHex = userList[0]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.5f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.5f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }

            3 -> {
                val userList = userHexList

                LoadUser(baseUserHex = userList[0]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[2]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(1.8f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }

            else -> {
                val userList = userHexList

                LoadUser(baseUserHex = userList[0]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[1]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[2]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
                LoadUser(baseUserHex = userList[3]) {
                    it?.let {
                        BaseUserPicture(
                            it,
                            size.div(2f),
                            accountViewModel,
                            outerModifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BaseUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    innerModifier: Modifier = Modifier,
    outerModifier: Modifier = Modifier.size(size),
) {
    Box(outerModifier, contentAlignment = Alignment.TopEnd) {
        ObserveAndDrawInnerUserPicture(baseUser, size, accountViewModel, innerModifier)

        WatchUserFollows(baseUser.pubkeyHex, accountViewModel) { newFollowingState ->
            if (newFollowingState) {
                FollowingIcon(Modifier.size(size.div(3.5f)))
            }
        }

        ObserveAndRenderUserCards(baseUser, size, Modifier.align(Alignment.BottomCenter), accountViewModel)
    }
}

@Composable
fun BaseUserPicture(
    baseUserHex: HexKey,
    size: Dp,
    accountViewModel: AccountViewModel,
    innerModifier: Modifier = Modifier,
    outerModifier: Modifier = Modifier.size(size),
) {
    Box(outerModifier, contentAlignment = Alignment.TopEnd) {
        LoadUser(baseUserHex) {
            if (it != null) {
                ObserveAndDrawInnerUserPicture(it, size, accountViewModel, innerModifier)

                ObserveAndRenderUserCards(it, size, Modifier.align(Alignment.BottomCenter), accountViewModel)
            } else {
                InnerUserPicture(
                    userHex = baseUserHex,
                    userPicture = null,
                    userName = null,
                    size = size,
                    modifier = innerModifier,
                )
            }
        }

        WatchUserFollows(baseUserHex, accountViewModel) { newFollowingState ->
            if (newFollowingState) {
                FollowingIcon(Modifier.size(size.div(3.5f)))
            }
        }
    }
}

@Composable
fun ObserveAndDrawInnerUserPicture(
    user: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    innerModifier: Modifier = Modifier,
) {
    val userProfile by observeUserInfo(user, accountViewModel)

    InnerUserPicture(
        userHex = user.pubkeyHex,
        userPicture = userProfile?.info?.profilePicture(),
        userName = userProfile?.info?.bestName(),
        size = size,
        modifier = innerModifier,
        pictureFallbacks = userProfile?.pictureFallbacks() ?: emptyList(),
    )
}

private const val PREVIEW_USER_HEX = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"
private const val PREVIEW_PICTURE_URL = "http://null"
private const val PREVIEW_USER_NAME = "vitor"

@Preview
@Composable
fun ScoreTag55Preview() {
    ThemeComparisonColumn {
        Row {
            var size = 75.dp
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                InnerUserPicture(
                    userHex = PREVIEW_USER_HEX,
                    userPicture = PREVIEW_PICTURE_URL,
                    userName = PREVIEW_USER_NAME,
                    size = size,
                    modifier = Modifier,
                )
                FollowingIcon(Modifier.size(size.div(3.5f)))
                ScoreTag(100, size, Modifier.align(Alignment.BottomCenter))
            }
            size = 55.dp
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                InnerUserPicture(
                    userHex = PREVIEW_USER_HEX,
                    userPicture = PREVIEW_PICTURE_URL,
                    userName = PREVIEW_USER_NAME,
                    size = size,
                    modifier = Modifier,
                )
                FollowingIcon(Modifier.size(size.div(3.5f)))
                ScoreTag(100, size, Modifier.align(Alignment.BottomCenter))
            }
            size = 35.dp
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                InnerUserPicture(
                    userHex = PREVIEW_USER_HEX,
                    userPicture = PREVIEW_PICTURE_URL,
                    userName = PREVIEW_USER_NAME,
                    size = size,
                    modifier = Modifier,
                )
                FollowingIcon(Modifier.size(size.div(3.5f)))
                ScoreTag(100, size, Modifier.align(Alignment.BottomCenter))
            }
            size = 25.dp
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                InnerUserPicture(
                    userHex = PREVIEW_USER_HEX,
                    userPicture = PREVIEW_PICTURE_URL,
                    userName = PREVIEW_USER_NAME,
                    size = size,
                    modifier = Modifier,
                )
                FollowingIcon(Modifier.size(size.div(3.5f)))
                ScoreTag(100, size, Modifier.align(Alignment.BottomCenter))
            }
            size = 18.dp
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                InnerUserPicture(
                    userHex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                    userPicture = PREVIEW_PICTURE_URL,
                    userName = PREVIEW_USER_NAME,
                    size = size,
                    modifier = Modifier,
                )
                FollowingIcon(Modifier.size(size.div(3.5f)))
                ScoreTag(100, size, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
fun InnerUserPicture(
    userHex: String,
    userPicture: String?,
    userName: String?,
    size: Dp,
    modifier: Modifier,
    pictureFallbacks: List<String> = emptyList(),
) {
    UserAvatar(
        userHex = userHex,
        pictureUrl = userPicture,
        pictureFallbacks = pictureFallbacks,
        size = size,
        modifier = modifier,
        contentDescription =
            if (userName != null) {
                stringRes(id = Res.string.profile_image_of_user, userName)
            } else {
                stringRes(id = Res.string.profile_image)
            },
    )
}

@Composable
fun WatchUserFollows(
    userHex: String,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    if (accountViewModel.isLoggedUser(userHex)) {
        onFollowChanges(true)
    } else {
        val state by accountViewModel.account.allFollows.flow
            .collectAsStateWithLifecycle()

        onFollowChanges(state.authors.contains(userHex))
    }
}

@Composable
fun ObserveAndRenderUserCards(
    user: User,
    size: Dp,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    val score by observeUserAssertionsScore(user, accountViewModel)

    score?.let {
        ScoreTag(it, size, modifier)
    }
}

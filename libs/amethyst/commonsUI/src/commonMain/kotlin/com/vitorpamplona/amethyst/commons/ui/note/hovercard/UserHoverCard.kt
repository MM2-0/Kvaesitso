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
package com.vitorpamplona.amethyst.commons.ui.note.hovercard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsFollowerCount
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.number_followers
import com.vitorpamplona.amethyst.commons.resources.profile_card_follows_you
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.commons.ui.note.types.ProfileCardBodyTopPadding
import com.vitorpamplona.amethyst.commons.ui.note.types.ProfileCardChip
import com.vitorpamplona.amethyst.commons.ui.note.types.ProfileCardHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.ProfileCardNames
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.ProfileActions
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

private val CardWidth = 340.dp
private val CardShape = RoundedCornerShape(16.dp)

/**
 * A profile at a glance, opened by resting the mouse on someone's face or name: the same
 * banner, avatar, names and bio as the profile screen and the profile's own actions (message,
 * follow, lists), so following or blocking someone doesn't take a trip to their profile.
 *
 * Clicking the header opens the profile. [onMoreOptions] asks the host for the profile's
 * action menu (block, report, nickname): the host draws it outside the card, so its dialogs
 * survive the card closing.
 */
@Composable
fun UserHoverCard(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpenProfile: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userInfo by observeUserInfo(user, accountViewModel)
    val metadata = userInfo?.info
    val tags = userInfo?.tags
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        shape = CardShape,
        color = cardBackground,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.width(CardWidth),
    ) {
        Column {
            Column(Modifier.clickable(onClick = onOpenProfile)) {
                ProfileCardHeader(user, metadata?.banner, cardBackground, accountViewModel) {
                    FollowsYouChip(user, accountViewModel)
                }

                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = ProfileCardBodyTopPadding)) {
                    ProfileCardNames(user, metadata, tags)

                    ObserveDisplayNip05Status(user, accountViewModel, nav)

                    metadata?.about?.ifBlank { null }?.let { about ->
                        CreateTextWithEmoji(
                            text = about,
                            tags = tags,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    FollowerCount(user, accountViewModel)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp).height(Size35dp),
            ) {
                ProfileActions(user, accountViewModel, nav)

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onMoreOptions) {
                    Icon(
                        symbol = MaterialSymbols.MoreVert,
                        contentDescription = stringRes(Res.string.more_options),
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowsYouChip(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val followsMe by observeUserIsFollowing(user, accountViewModel.account.userProfile(), accountViewModel)

    if (followsMe) {
        ProfileCardChip(
            symbol = MaterialSymbols.Person,
            label = stringRes(Res.string.profile_card_follows_you),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FollowerCount(
    user: User,
    accountViewModel: AccountViewModel,
) {
    // The count a trusted NIP-85 provider publishes; "--" while there is none for this user.
    val followerCount by observeUserAssertionsFollowerCount(user, accountViewModel)

    if (followerCount != "--") {
        Text(
            text = stringRes(Res.string.number_followers, followerCount),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

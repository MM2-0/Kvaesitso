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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.buzz.ui.BuzzAgentLabel
import com.vitorpamplona.amethyst.commons.buzz.ui.rememberBuzzContextualName
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserDisplayNickname
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.hovercard.UserHoverCardAnchor
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.identityNames.IdentityNamePolicy
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The author's name at the top of the first bubble of their run. Name only: the face sits
 * outside the bubble, beside the run's last bubble ([ChatAuthorAvatar]), so the bubble keeps
 * to what was said instead of opening with a picture and its badges.
 */
@Composable
fun DrawAuthorInfo(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A geohash chat resolves the message's `n` nickname here (throwaway keys have no profile);
    // null everywhere else, so authors render from their profile as usual.
    val nameOverride = LocalChatDisplayNameResolver.current?.invoke(baseNote)
    // In a Buzz channel two members may share a name; Buzz tells them apart there.
    val buzzChannel =
        remember(baseNote) {
            baseNote.inGatherers
                ?.firstNotNullOfOrNull { it as? RelayGroupChannel }
                ?.takeIf { BuzzRelayDialect.isBuzz(it.groupId.relayUrl) }
        }
    baseNote.author?.let {
        WatchAndDisplayUser(it, nameOverride, buzzChannel, accountViewModel, nav)
    }
}

/**
 * A stable, pubkey-derived name color so authors are scannable in fast-moving
 * group rooms. The hue comes from the pubkey; saturation/lightness are tuned per
 * theme so every hue stays readable on the "them" bubble fill. Saturation is kept
 * low: the hue only has to tell two names apart, and at full strength every author
 * line was another bright color competing with the message under it.
 */
fun authorNameColorFor(
    pubkeyHex: String,
    isLightTheme: Boolean,
): Color {
    val hue = (pubkeyHex.take(6).toIntOrNull(16) ?: pubkeyHex.hashCode()).mod(360).toFloat()
    return if (isLightTheme) {
        Color.hsl(hue, saturation = 0.45f, lightness = 0.38f)
    } else {
        Color.hsl(hue, saturation = 0.38f, lightness = 0.74f)
    }
}

/** Size of the author face beside a chat bubble. */
val ChatAuthorAvatarSize = 28.dp

/** Weight and size of the author name inside a bubble: a label over the message, not a heading. */
val ChatAuthorNameWeight = FontWeight.SemiBold
val ChatAuthorNameSize = 13.sp

/**
 * The author's face in a group chat, at [size] (the layout hands it [ChatAuthorAvatarSize] for the
 * bubble gutter, a smaller one for the open layout's header), with the follow mark and NIP-85
 * trust score [BaseUserPicture] draws on every profile picture. Tapping it opens the profile.
 */
@Composable
fun ChatAuthorAvatar(
    userHex: HexKey,
    accountViewModel: AccountViewModel,
    size: Dp = ChatAuthorAvatarSize,
    onClick: () -> Unit,
) {
    BaseUserPicture(
        baseUserHex = userHex,
        size = size,
        accountViewModel = accountViewModel,
        outerModifier = Modifier.size(size).clickable(onClick = onClick),
    )
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    nameOverride: String?,
    buzzChannel: RelayGroupChannel?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(author, accountViewModel)
    val nickname by observeUserDisplayNickname(author, accountViewModel)
    val petName = nickname?.petName
    val profileName = userState?.info?.bestName()
    // Buzz's contextual name ("Alice’s Honey", "Honey · 7xk2") when the channel has a namesake.
    val contextual = buzzChannel?.let { rememberBuzzContextualName(it, author, accountViewModel) }?.name
    val qualified = contextual != null && profileName != null && contextual != IdentityNamePolicy.trim(profileName)
    // A geohash message's `n` nickname wins over the (usually empty) profile of a throwaway key, and a
    // nickname the account set wins over everything else.
    val displayName = nameOverride ?: petName ?: (if (qualified) contextual else profileName)

    val isLightTheme = MaterialTheme.colorScheme.isLight
    val nameColor =
        remember(author.pubkeyHex, isLightTheme) {
            authorNameColorFor(author.pubkeyHex, isLightTheme)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserHoverCardAnchor(author, accountViewModel, nav, Modifier.weight(1f, fill = false)) {
            CreateTextWithEmoji(
                text = displayName ?: author.pubkeyDisplayHex(),
                tags = (if (nameOverride == null && petName != null) nickname?.tags else userState?.tags) ?: EmptyTagList,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = ChatAuthorNameWeight,
                fontSize = ChatAuthorNameSize,
            )
        }
        // An agent says so, and whose it is (its owner comes from the NIP-OA tag on its kind 0),
        // unless its contextual name already does.
        BuzzAgentLabel(author, userState?.nipOaOwner, accountViewModel, showOwner = !qualified)
    }
}

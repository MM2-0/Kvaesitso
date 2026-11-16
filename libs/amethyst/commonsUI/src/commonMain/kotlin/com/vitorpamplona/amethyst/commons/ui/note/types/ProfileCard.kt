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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsFollowerCount
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.number_followers
import com.vitorpamplona.amethyst.commons.resources.profile_card_bot
import com.vitorpamplona.amethyst.commons.resources.profile_card_follows_you
import com.vitorpamplona.amethyst.commons.ui.components.LocalReadOnlyPreview
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.InnerUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.commons.ui.note.ShowFollowingOrUnfollowingButton
import com.vitorpamplona.amethyst.commons.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata

// A kind-0 in the feed is a person, not a JSON blob. The card mirrors the profile
// screen it opens: banner, overlapping avatar, name, nip05 and a bio, so the reader
// recognizes who this is without leaving the feed.
private val BannerHeight = 104.dp
private val AvatarSize = 72.dp
private val AvatarRingWidth = 3.dp

/** How far the avatar hangs below the banner, into the body of the card. */
private val AvatarOverhang = 30.dp

private val ChipShape = RoundedCornerShape(50)

private val BannerModifier = Modifier.fillMaxWidth().height(BannerHeight)

// A disc painted behind the avatar, not a clip: the picture clips itself, and clipping here also
// cut off the follow mark that BaseUserPicture draws past the circle's edge.
private val AvatarRingModifier = Modifier.size(AvatarSize + AvatarRingWidth * 2)

private val AvatarRowModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).offset(y = AvatarOverhang)

/** The room the body leaves above itself for the avatar hanging out of [ProfileCardHeader]. */
internal val ProfileCardBodyTopPadding = AvatarOverhang + 8.dp

private val CardBodyPadding =
    Modifier.padding(
        start = 14.dp,
        end = 14.dp,
        top = ProfileCardBodyTopPadding,
        bottom = 14.dp,
    )

@Composable
fun RenderProfileCard(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // In a signer prompt the event is a profile about to REPLACE the current one, so it must show
    // what it says, not the author's cached profile: otherwise an update that drops a field or
    // changes the bio looks like no change at all.
    val proposed =
        if (LocalReadOnlyPreview.current) {
            remember(baseNote.event) { (baseNote.event as? MetadataEvent)?.contactMetaData() ?: UserMetadata() }
        } else {
            null
        }
    WatchAuthor(baseNote, accountViewModel) { author ->
        ProfileCard(author, backgroundColor, accountViewModel, nav, proposed)
    }
}

@Composable
fun ProfileCard(
    author: User,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    proposed: UserMetadata? = null,
) {
    val userInfo by observeUserInfo(author, accountViewModel)
    val metadata = proposed ?: userInfo?.info
    val tags = userInfo?.tags

    Column(
        MaterialTheme.colorScheme.innerPostModifier.clickable {
            nav.nav(routeFor(author))
        },
    ) {
        ProfileCardHeader(author, metadata?.banner, backgroundColor.value, accountViewModel, proposedPicture = proposed?.let { it.picture ?: "" }) {
            ProfileCardActions(author, accountViewModel)
        }

        Column(CardBodyPadding) {
            ProfileCardNames(author, metadata, tags)

            // The verified badge checks the CURRENT address; a proposed change shows as plain text.
            if (proposed == null) ObserveDisplayNip05Status(author, accountViewModel, nav)

            metadata?.about?.ifBlank { null }?.let { about ->
                Spacer(Modifier.height(Size5dp))
                CreateTextWithEmoji(
                    text = about,
                    tags = tags,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ProfileCardChips(author, metadata, accountViewModel)
        }
    }
}

/**
 * Banner + avatar + [trailing] (the follow action on the feed card). The avatar and the
 * trailing row are laid out inside the banner's [Box] and pushed down by [AvatarOverhang]: the
 * offset doesn't change the Box's measured height, so the body below reserves the same amount
 * as top padding ([ProfileCardBodyTopPadding]) and no gap is left at the bottom of the card.
 */
@Composable
internal fun ProfileCardHeader(
    author: User,
    banner: String?,
    cardBackground: Color,
    accountViewModel: AccountViewModel,
    // Non-null in a signer prompt: the picture the new profile sets ("" for none).
    proposedPicture: String? = null,
    trailing: @Composable () -> Unit,
) {
    // Fades the banner into the card so the avatar and the name below always have
    // enough contrast, whatever the user uploaded.
    val scrim =
        remember(cardBackground) {
            Brush.verticalGradient(0.35f to Color.Transparent, 1f to cardBackground)
        }

    Box(BannerModifier) {
        BannerImage(banner, BannerModifier, accountViewModel)

        Box(BannerModifier.background(scrim))

        Row(
            modifier = AvatarRowModifier.align(Alignment.BottomStart),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = AvatarRingModifier.background(cardBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (proposedPicture != null) {
                    InnerUserPicture(
                        userHex = author.pubkeyHex,
                        userPicture = proposedPicture.ifBlank { null },
                        userName = null,
                        size = AvatarSize,
                        modifier = Modifier,
                    )
                } else {
                    BaseUserPicture(author, AvatarSize, accountViewModel)
                }
            }

            Spacer(Modifier.weight(1f))

            trailing()
        }
    }
}

@Composable
private fun ProfileCardActions(
    author: User,
    accountViewModel: AccountViewModel,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, author)

    if (isHidden) {
        ShowUserButton { accountViewModel.show(author) }
    } else if (!accountViewModel.isLoggedUser(author)) {
        // Nothing to follow on your own card.
        ShowFollowingOrUnfollowingButton(author, accountViewModel)
    }
}

@Composable
internal fun ProfileCardNames(
    author: User,
    metadata: UserMetadata?,
    tags: ImmutableListOfLists<String>?,
) {
    // pubkeyDisplayHex() hex-decodes and bech32-encodes the key; keep it off the
    // recomposition path.
    val shortNpub = remember(author) { author.pubkeyDisplayHex() }
    val bestName = metadata?.bestName() ?: shortNpub

    Row(verticalAlignment = Alignment.CenterVertically) {
        CreateTextWithEmoji(
            text = bestName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        metadata?.pronouns?.ifBlank { null }?.let {
            Spacer(Modifier.size(Size5dp))
            Text(
                text = remember(it) { "($it)" },
                color = MaterialTheme.colorScheme.placeholderText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val handle = metadata?.name
    if (!handle.isNullOrBlank() && handle != bestName) {
        CreateTextWithEmoji(
            text = remember(handle) { "@$handle" },
            tags = tags,
            color = MaterialTheme.colorScheme.placeholderText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCardChips(
    author: User,
    metadata: UserMetadata?,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    val website = metadata?.website?.ifBlank { null }
    val lnAddress = metadata?.lnAddress()?.ifBlank { null }
    val isBot = metadata?.bot == true

    // A self-follow in your own kind:3 is common; "Follows you" on your own card is not a fact.
    val followsMe by observeUserIsFollowing(author, accountViewModel.account.userProfile(), accountViewModel)
    val followsYou = followsMe && !accountViewModel.isLoggedUser(author)
    val followerCount by observeUserAssertionsFollowerCount(author, accountViewModel)
    // "--" is the placeholder the contact-card observer emits while no trusted
    // assertion for this user has arrived. Nothing to brag about yet.
    val followers = followerCount.takeIf { it != "--" }

    if (website == null && lnAddress == null && !isBot && !followsYou && followers == null) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        if (followers != null) {
            ProfileCardChip(
                symbol = MaterialSymbols.Groups,
                label = stringRes(Res.string.number_followers, followers),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (followsYou) {
            ProfileCardChip(
                symbol = MaterialSymbols.Person,
                label = stringRes(Res.string.profile_card_follows_you),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (website != null) {
            ProfileCardChip(
                symbol = MaterialSymbols.Link,
                label = remember(website) { website.removePrefix("https://").removePrefix("http://").removeSuffix("/") },
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    runCatching {
                        if (website.contains("://")) {
                            uri.openUri(website)
                        } else {
                            uri.openUri("http://$website")
                        }
                    }
                },
            )
        }

        if (lnAddress != null) {
            ProfileCardChip(
                symbol = MaterialSymbols.Bolt,
                label = lnAddress,
                color = MaterialTheme.colorScheme.bitcoinColor,
            )
        }

        if (isBot) {
            ProfileCardChip(
                symbol = MaterialSymbols.Assistant,
                label = stringRes(Res.string.profile_card_bot),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

/**
 * Pill in the same visual language as the profile's payment rails: tinted outline,
 * icon, single ellipsized line. Without [onClick] the pill stays inert so the tap
 * falls through to the card and opens the profile.
 */
@Composable
internal fun ProfileCardChip(
    symbol: MaterialSymbol,
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    // Clip before clickable: Surface applies the passed-in modifier above its own
    // shape clip, so an unclipped ripple would paint a square over the pill.
    Surface(
        shape = ChipShape,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = if (onClick != null) Modifier.clip(ChipShape).clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = color,
                modifier = Size16Modifier,
            )
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.ui.BuzzAgentLabel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copy_nprofile_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_npub_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.github
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.mastodon
import com.vitorpamplona.amethyst.commons.resources.show_nprofile_as_a_qr_code
import com.vitorpamplona.amethyst.commons.resources.telegram
import com.vitorpamplona.amethyst.commons.resources.twitter
import com.vitorpamplona.amethyst.commons.resources.website
import com.vitorpamplona.amethyst.commons.resources.x
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.util.LongPressCopyText
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ObserveAndRenderNIP05VerifiedSymbol
import com.vitorpamplona.amethyst.commons.ui.note.lastSeenSentence
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.platform.SpeakNameButton
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.apps.DisplayAppRecommendations
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.badges.DisplayBadges
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy3dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.UserExternalIdentitiesViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TelegramIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.UnsupportedIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

private const val IDENTITY_ICON_CACHE_KEY = 0

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawAdditionalInfo(
    baseUser: User,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    externalIdentities: UserExternalIdentitiesViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(baseUser, accountViewModel)
    val user = userState ?: return
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val identities by externalIdentities.identities.collectAsStateWithLifecycle()

    val displayName = user.info.bestName()

    val ui = accountViewModel.settings.uiSettingsFlow
    val showBadges by ui.showProfileBadges.collectAsStateWithLifecycle()
    val showAppRecommendations by ui.showProfileAppRecommendations.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = SpacedBy3dp) {
        // the nickname the account gave this user, on top of (not replacing)
        // the profile's own display name below
        UserNicknameCard(baseUser, accountViewModel)

        if (displayName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 7.dp),
            ) {
                CreateTextWithEmoji(
                    text = displayName,
                    tags = user.tags,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                Spacer(StdHorzSpacer)
                user.info.pronouns?.let {
                    Text(
                        text = "($it)",
                        color = MaterialTheme.colorScheme.placeholderText,
                        fontSize = 14.sp,
                    )
                    Spacer(StdHorzSpacer)
                }

                SpeakNameButton(displayName)
            }

            // A Buzz agent says so, and whose it is: the owner from the NIP-OA tag on its kind 0,
            // which opens the owner's profile.
            BuzzAgentLabel(
                author = baseUser,
                owner = user.nipOaOwner,
                accountViewModel = accountViewModel,
                onOwnerClick = { nav.nav(Route.Profile(it)) },
            )
        }

        if (displayName != user.info.name && !user.info.name.isNullOrBlank()) {
            Text(
                color = MaterialTheme.colorScheme.placeholderText,
                text = "@" + user.info.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // In or out of the Web of Trust network; nothing when no network is active.
        ProfileTrustBadge(baseUser, accountViewModel, nav)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = baseUser.pubkeyDisplayHex(),
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )

            IconButton(
                modifier = Modifier.size(23.dp).padding(start = 5.dp),
                onClick = {
                    scope.launch {
                        clipboard.setText(baseUser.pubkeyNpub())
                    }
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = stringRes(id = Res.string.copy_npub_to_clipboard),
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = baseUser.toNProfile().toShortDisplay(6),
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )

            IconButton(
                modifier = Modifier.size(23.dp).padding(start = 5.dp),
                onClick = {
                    scope.launch {
                        clipboard.setText(baseUser.toNProfile())
                    }
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = stringRes(id = Res.string.copy_nprofile_to_clipboard),
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }

            IconButton(
                modifier = Modifier.size(23.dp),
                onClick = { nav.nav(Route.QRDisplay(baseUser.pubkeyHex)) },
            ) {
                Icon(
                    painter = painterRes(Res.drawable.ic_qrcode, 1),
                    contentDescription = stringRes(id = Res.string.show_nprofile_as_a_qr_code),
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        DisplayLastSeen(baseUser, accountViewModel)

        DisplayNip05ProfileStatus(baseUser, accountViewModel)

        val website = user.info.website
        if (!website.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    symbol = MaterialSymbols.Link,
                    contentDescription = stringRes(Res.string.website),
                    modifier = Modifier.size(18.dp),
                )

                LongPressCopyText(
                    displayText = website.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                    copyValue = website,
                    onClick = {
                        runCatching {
                            if (website.contains("://")) {
                                uri.openUri(website)
                            } else {
                                uri.openUri("http://$website")
                            }
                        }
                    },
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 1.dp, horizontal = 5.dp),
                )
            }
        }

        val displayIdentities = identities.ifEmpty { user.identities }
        if (displayIdentities.isNotEmpty()) {
            displayIdentities.forEach { identity: IdentityClaimTag -> IdentityClaimRow(identity) }
        }

        val lud16 =
            remember(userState) {
                userState?.info?.lud16?.trim()
                    ?: userState?.info?.lud06?.trim()
            }
        DisplayPaymentRailChips(baseUser, lud16, user, accountViewModel, nav)

        if (showBadges) {
            DisplayBadges(baseUser, accountViewModel, nav)
        }

        user.info.about?.let {
            Row(
                modifier = Modifier.padding(top = 10.dp, bottom = 5.dp),
            ) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = false,
                    quotesLeft = 1,
                    tags = user.tags,
                    backgroundColor = background,
                    id = it,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        if (showAppRecommendations) {
            DisplayAppRecommendations(appRecommendations, accountViewModel, nav)
        }
    }
}

@Composable
fun DisplayLastSeen(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val lastSeenFlow =
        remember {
            accountViewModel.account.cache
                .observeLatestEvent<Event>(Filter(authors = listOf(user.pubkeyHex)))
                .map {
                    it?.createdAt
                }.flowOn(Dispatchers.IO)
        }

    val lastSeen by lastSeenFlow.collectAsStateWithLifecycle(null)

    lastSeen?.let { timestamp ->
        Text(
            text = lastSeenSentence(timestamp),
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DisplayNip05ProfileStatus(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    when (val nip05State = nip05StateMetadata) {
        is Nip05State.Exists -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = SpacedBy5dp) {
                ObserveAndRenderNIP05VerifiedSymbol(nip05State, 2, Size15Modifier, accountViewModel)

                val uri = LocalUriHandler.current
                val displayValue = nip05State.nip05.toDisplayValue()

                LongPressCopyText(
                    displayText = displayValue,
                    copyValue = displayValue,
                    onClick = {
                        runCatching { uri.openUri("https://${nip05State.nip05.domain}") }
                    },
                    softWrap = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
                )
            }
        }

        else -> { }
    }
}

/**
 * One NIP-39 claimed account: the platform's logo and the handle, which opens the proof; a
 * long-press copies the handle. A platform this app doesn't know shows a link glyph and its name
 * instead of a logo that would claim the wrong service. Shared by the profile header and the
 * external-identities card in the feed.
 */
@Composable
fun IdentityClaimRow(
    identity: IdentityClaimTag,
    iconSize: Dp = 18.dp,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (identity is UnsupportedIdentity) {
            Icon(
                symbol = MaterialSymbols.Link,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = identity.platformIdentity(),
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 5.dp),
            )
        } else {
            Icon(
                tint = Color.Unspecified,
                painter = painterRes(getIdentityClaimIcon(identity), IDENTITY_ICON_CACHE_KEY),
                contentDescription = stringRes(getIdentityClaimDescription(identity)),
                modifier = Modifier.size(iconSize),
            )
            LongPressCopyText(
                displayText = identity.identity,
                copyValue = identity.identity,
                onClick = { runCatching { uri.openUri(identity.toProofUrl()) } },
                style = textStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 5.dp),
            )
        }
    }
}

fun getIdentityClaimIcon(identity: IdentityClaimTag): DrawableResource =
    when (identity) {
        is TwitterIdentity -> Res.drawable.x
        is TelegramIdentity -> Res.drawable.telegram
        is MastodonIdentity -> Res.drawable.mastodon
        is GitHubIdentity -> Res.drawable.github
        else -> Res.drawable.github
    }

fun getIdentityClaimDescription(identity: IdentityClaimTag): StringResource =
    when (identity) {
        is TwitterIdentity -> Res.string.twitter
        is TelegramIdentity -> Res.string.telegram
        is MastodonIdentity -> Res.string.mastodon
        is GitHubIdentity -> Res.string.github
        else -> Res.string.github
    }

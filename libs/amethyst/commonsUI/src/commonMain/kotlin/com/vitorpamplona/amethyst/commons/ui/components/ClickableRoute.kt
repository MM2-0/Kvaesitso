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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserDisplayNickname
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_invite_naddr_label
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.ClickableInLineIconRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.richtext.CustomEmojiChecker
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.njumpLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNIP19
import com.vitorpamplona.quartz.nip29RelayGroups.GroupNAddrInvite
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

@Composable
fun ClickableRoute(
    word: String,
    nip19: Nip19Parser.ParseReturn,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val entity = nip19.entity) {
        is NPub -> DisplayUser(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NProfile -> DisplayUser(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NNote -> DisplayEvent(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEvent -> DisplayEvent(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEmbed -> LoadAndDisplayEvent(entity.event, nip19.additionalChars, accountViewModel, nav)
        is NAddress -> DisplayAddress(entity, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NRelay -> Text(word)
        is NSec -> Text(word)
        else -> Text(word)
    }
}

@Composable
fun LoadOrCreateNote(
    event: Event,
    accountViewModel: AccountViewModel,
    content: @Composable (Note?) -> Unit,
) {
    var note by
        remember(event.id) { mutableStateOf(LocalCache.getNoteIfExists(event.id)) }

    if (note == null) {
        LaunchedEffect(key1 = event.id) {
            note = accountViewModel.noteFromEvent(event)
        }
    }

    content(note)
}

@Composable
private fun LoadAndDisplayEvent(
    event: Event,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadOrCreateNote(event, accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, event.id, additionalChars, accountViewModel, nav)
        } else {
            val externalLink = event.toNIP19()
            val uri = LocalUriHandler.current

            CreateClickableText(
                clickablePart = "@$externalLink",
                suffix = additionalChars,
                maxLines = 1,
                onClick = {
                    runCatching { uri.openUri(njumpLink(externalLink)) }
                },
            )
        }
    }
}

@Composable
fun DisplayEvent(
    hex: HexKey,
    nip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(hex) {
        if (it != null) {
            DisplayNoteLink(it, hex, additionalChars, accountViewModel, nav)
        } else {
            val externalLink = njumpLink(nip19)
            val uri = LocalUriHandler.current

            CreateClickableText(
                clickablePart = remember(nip19) { "@$nip19" },
                suffix = additionalChars,
                maxLines = 1,
                onClick = {
                    runCatching { uri.openUri(externalLink) }
                },
            )
        }
    }
}

@Composable
private fun DisplayNoteLink(
    it: Note,
    hex: HexKey,
    addedCharts: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(it, accountViewModel)
    val noteIdDisplayNote = remember(noteState) { "@${noteState.note.idDisplayNote()}" }

    val route = routeFor(it, accountViewModel.account) ?: Route.EventRedirect(hex)

    CreateClickableText(
        clickablePart = noteIdDisplayNote,
        suffix = addedCharts,
        route = route,
        nav = nav,
    )
}

@Composable
private fun DisplayAddress(
    nip19: NAddress,
    originalNip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A NIP-29 group is addressed by its relay-signed kind-39000 metadata. Route
    // straight into the group chat rather than the generic addressable-note view.
    // The relay is only carried in the naddr's relay hint (the group id is unique
    // only per relay), so we need that hint to open it.
    val groupRelay = if (nip19.kind == GroupMetadataEvent.KIND) nip19.relay.firstOrNull() else null
    if (groupRelay != null) {
        // An invite code may be appended to the group naddr as `?invite=<code>`; it lands
        // in additionalChars. Pass it through so the group auto-joins with a kind-9021 code.
        val inviteCode = GroupNAddrInvite.parse(additionalChars)
        CreateClickableText(
            clickablePart = "#${nip19.dTag}",
            suffix = additionalChars,
            route = Route.RelayGroup(nip19.dTag, groupRelay.url, inviteCode = inviteCode),
            nav = nav,
        )
        return
    }

    // A Concord invite bundle (kind 33301) is addressed by a bare naddr, but redeeming it
    // needs the 16-byte unlock token that only lives in the full invite link's #fragment —
    // a naddr alone can't be joined. Show an informative label instead of the generic
    // (and here always-empty) addressable-note card.
    if (nip19.kind == ConcordInviteBundleEvent.KIND) {
        Text(
            text = stringRes(Res.string.concord_invite_naddr_label) + (additionalChars ?: ""),
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    var noteBase by remember(nip19) { mutableStateOf(LocalCache.getNoteIfExists(nip19.aTag())) }

    if (noteBase == null) {
        LaunchedEffect(key1 = nip19) {
            noteBase = LocalCache.getOrCreateAddressableNote(nip19.address())
        }
    }

    noteBase?.let {
        val noteState by observeNote(it, accountViewModel)

        val route = remember(noteState) { Route.Note(nip19.aTag()) }
        val displayName = remember(noteState) { "@${noteState.note.idDisplayNote()}" }

        CreateClickableText(
            clickablePart = displayName,
            suffix = additionalChars,
            route = route,
            nav = nav,
        )
    }

    if (noteBase == null) {
        val uri = LocalUriHandler.current

        CreateClickableText(
            clickablePart = "@$originalNip19",
            suffix = additionalChars,
            maxLines = 1,
            onClick = {
                runCatching { uri.openUri(njumpLink(originalNip19)) }
            },
        )
    }
}

@Composable
fun DisplayUser(
    userHex: HexKey,
    originalNip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var userBase by
        remember(userHex) {
            mutableStateOf(
                LocalCache.getUserIfExists(userHex),
            )
        }

    if (userBase == null) {
        LaunchedEffect(key1 = userHex) {
            userBase = LocalCache.checkGetOrCreateUser(userHex)
        }
    }

    userBase?.let { RenderUserAsClickableText(it, additionalChars, accountViewModel, nav) }

    if (userBase == null) {
        val uri = LocalUriHandler.current

        CreateClickableText(
            clickablePart = "@$originalNip19",
            suffix = additionalChars,
            maxLines = 1,
            onClick = {
                runCatching { uri.openUri(njumpLink(originalNip19)) }
            },
        )
    }
}

@Composable
fun RenderUserAsClickableText(
    baseUser: User,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(baseUser, accountViewModel)
    val nickname by observeUserDisplayNickname(baseUser, accountViewModel)
    val petName = nickname?.petName

    CreateClickableTextWithEmoji(
        clickablePart = "@" + (petName ?: userState?.info?.bestName() ?: baseUser.pubkeyDisplayHex()),
        suffix = additionalChars?.ifBlank { null },
        maxLines = 1,
        route = remember(baseUser) { routeFor(baseUser) },
        nav = nav,
        tags = (if (petName != null) nickname?.tags else userState?.tags) ?: EmptyTagList,
    )
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    route: Route,
    nav: INav,
) {
    CreateClickableText(
        clickablePart,
        suffix,
        maxLines,
        overrideColor,
        fontWeight,
        fontSize,
    ) { nav.nav(route) }
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    val text =
        remember(clickablePart, suffix) {
            val clickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: primaryColor,
                    fontWeight = fontWeight,
                )

            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "clickable",
                        styles = TextLinkStyles(clickablePartStyle),
                    ) {
                        onClick()
                    },
                ) {
                    append(clickablePart)
                }
                if (!suffix.isNullOrBlank()) {
                    val nonClickablePartStyle =
                        SpanStyle(
                            fontSize = fontSize,
                            color = overrideColor ?: onBackgroundColor,
                            fontWeight = fontWeight,
                        )

                    withStyle(nonClickablePartStyle) { append(suffix) }
                }
            }
        }

    Text(
        text = text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    suffix: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = TextUnit.Unspecified,
    route: Route,
    nav: INav,
    tags: ImmutableListOfLists<String>?,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            CreateClickableText(it, suffix, maxLines, overrideColor, fontWeight, fontSize, route, nav)
        },
        onEmojiText = {
            val nonClickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: MaterialTheme.colorScheme.onBackground,
                    fontWeight = fontWeight,
                )

            val clickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: MaterialTheme.colorScheme.primary,
                    fontWeight = fontWeight,
                )

            ClickableInLineIconRenderer(
                it,
                maxLines,
                clickablePartStyle,
                suffix,
                nonClickablePartStyle,
            ) {
                nav.nav(route)
            }
        },
    )
}

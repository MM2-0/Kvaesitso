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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Tunestr
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05VerifState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserStatuses
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip05_checking
import com.vitorpamplona.amethyst.commons.resources.nip05_failed
import com.vitorpamplona.amethyst.commons.resources.nip05_verified
import com.vitorpamplona.amethyst.commons.resources.nip_05
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.ui.theme.nip05
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun ObserveDisplayNip05Status(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAuthor(baseNote = baseNote, accountViewModel) {
        ObserveDisplayNip05Status(it, accountViewModel, nav)
    }
}

@Composable
fun ObserveDisplayNip05Status(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val statuses by observeUserStatuses(baseUser, accountViewModel)
    val nip05State by baseUser.nip05State().flow.collectAsStateWithLifecycle()

    VerifyAndDisplayNIP05OrStatusLine(
        nip05State,
        statuses,
        baseUser,
        accountViewModel,
        nav,
    )
}

@Composable
private fun VerifyAndDisplayNIP05OrStatusLine(
    nip05State: Nip05State,
    statuses: ImmutableList<AddressableNote>,
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nip05State is Nip05State.Exists) {
            val nip05VerifState by nip05State.verificationState.collectAsStateWithLifecycle()

            if (nip05VerifState.isExpired()) {
                LaunchedEffect(key1 = nip05VerifState) {
                    accountViewModel.runOnIO {
                        nip05State.checkAndUpdate(accountViewModel.nip05ClientBuilder)
                    }
                }
            }

            if (nip05VerifState is Nip05VerifState.Verified && !statuses.isEmpty()) {
                ObserveRotateStatuses(statuses, accountViewModel, nav)
            } else {
                DisplayNIP05(nip05State, nip05VerifState, accountViewModel)
            }
        } else {
            if (!statuses.isEmpty()) {
                RotateStatuses(statuses, accountViewModel, nav)
            } else {
                DisplayUsersNpub(baseUser.pubkeyDisplayHex())
            }
        }
    }
}

@Composable
fun ObserveRotateStatuses(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveAllStatusesToAvoidSwitchigAllTheTime(statuses, accountViewModel)

    RotateStatuses(
        statuses,
        accountViewModel,
        nav,
    )
}

@Composable
fun ObserveAllStatusesToAvoidSwitchigAllTheTime(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
) {
    statuses.forEach {
        EventFinderFilterAssemblerSubscription(it, accountViewModel)
    }
}

@Composable
fun RotateStatuses(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var indexToDisplay by remember(statuses) { mutableIntStateOf(0) }

    DisplayStatus(statuses[indexToDisplay], accountViewModel, nav)

    if (statuses.size > 1) {
        // Keyed like the index above: a new list gets a new index, and a loop still holding the
        // old one would rotate a state nobody reads while this one stayed on its first status.
        LaunchedEffect(statuses) {
            while (true) {
                delay(10.seconds)
                indexToDisplay = (indexToDisplay + 1) % statuses.size
            }
        }
    }
}

@Composable
fun DisplayUsersNpub(npub: String) {
    Text(
        text = npub,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DisplayStatus(
    addressableNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(addressableNote, accountViewModel)
    val noteEvent = noteState.note.event as? UserStatusEvent ?: return

    DisplayStatus(noteEvent, accountViewModel, nav)
}

@Composable
fun DisplayStatus(
    event: UserStatusEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val emojis =
        remember(event) {
            val emojiList = event.taggedEmojis()
            if (emojiList.isEmpty()) {
                persistentMapOf()
            } else {
                emojiList.associate { it.code to it.url }.toImmutableMap()
            }
        }

    // Buzz sets a plain emoji with a 2-element `["emoji", "🎉"]` tag instead of inlining it in the
    // text; lead with it the way Buzz's own client does. NIP-30 custom emoji are handled above.
    val text =
        remember(event) {
            val emoji = event.statusEmoji()
            if (emoji == null) {
                event.content
            } else if (event.content.isBlank()) {
                emoji
            } else {
                "$emoji ${event.content}"
            }
        }

    DisplayStatusInner(
        text,
        event.dTag(),
        event.firstTaggedUrl()?.ifBlank { null },
        event.referencedAddressId()?.let { Address.parse(it) },
        event.referencedEventId(),
        event.referencedProfile(),
        emojis,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayStatusInner(
    content: String,
    type: String,
    url: String?,
    nostrATag: Address?,
    nostrEventId: HexKey?,
    nostrPTag: String?,
    emojis: ImmutableMap<String, String>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (type) {
        UserStatusEvent.MUSIC -> {
            Icon(
                imageVector = CustomHashTagIcons.Tunestr,
                null,
                modifier = Size15Modifier.padding(end = Size5dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        else -> {}
    }

    if (emojis.isNotEmpty()) {
        CreateTextWithEmoji(
            text = content,
            emojis = emojis,
            color = MaterialTheme.colorScheme.placeholderText,
            fontSize = Font14SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = content,
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    when {
        url != null -> {
            val uri = LocalUriHandler.current
            Spacer(modifier = StdHorzSpacer)
            IconButton(
                modifier = Size15Modifier,
                onClick = { runCatching { uri.openUri(url.trim()) } },
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                    null,
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.lessImportantLink,
                )
            }
        }

        nostrATag != null -> {
            LoadAddressableNote(nostrATag) { note ->
                if (note != null) {
                    Spacer(modifier = StdHorzSpacer)
                    IconButton(
                        modifier = Size15Modifier,
                        onClick = {
                            routeFor(
                                note,
                                accountViewModel.account,
                            )?.let { nav.nav(it) }
                        },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                            null,
                            modifier = Size15Modifier,
                            tint = MaterialTheme.colorScheme.lessImportantLink,
                        )
                    }
                }
            }
        }

        nostrEventId != null -> {
            LoadNote(baseNoteHex = nostrEventId) {
                if (it != null) {
                    Spacer(modifier = StdHorzSpacer)
                    IconButton(
                        modifier = Size15Modifier,
                        onClick = {
                            routeFor(
                                it,
                                accountViewModel.account,
                            )?.let { nav.nav(it) }
                        },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                            null,
                            modifier = Size15Modifier,
                            tint = MaterialTheme.colorScheme.lessImportantLink,
                        )
                    }
                }
            }
        }

        nostrPTag != null -> {
            LoadUser(baseUserHex = nostrPTag) { user ->
                if (user != null) {
                    Spacer(modifier = StdHorzSpacer)
                    IconButton(
                        modifier = Size15Modifier,
                        onClick = { nav.nav(routeForUser(nostrPTag)) },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                            null,
                            modifier = Size15Modifier,
                            tint = MaterialTheme.colorScheme.lessImportantLink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ObserveAndDisplayNIP05(
    nip05State: Nip05State.Exists,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    if (nip05State.nip05.hasLocalPart()) {
        Text(
            text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    ObserveAndRenderNIP05VerifiedSymbol(nip05State, 1, NIP05IconSize, accountViewModel)

    ClickableTextPrimary(
        text = nip05State.nip05.domain,
        onClick = { runCatching { uri.openUri("https://${nip05State.nip05.domain}") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}

@Composable
fun DisplayNIP05(
    nip05State: Nip05State.Exists,
    nip05VerifState: Nip05VerifState,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    if (nip05State.nip05.hasLocalPart()) {
        Text(
            text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    RenderNIP05VerifiedSymbol(nip05VerifState, 1, NIP05IconSize, accountViewModel)

    ClickableTextPrimary(
        text = nip05State.nip05.domain,
        onClick = { runCatching { uri.openUri("https://${nip05State.nip05.domain}") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}

@Composable
fun ObserveAndRenderNIP05VerifiedSymbol(
    nip05State: Nip05State.Exists,
    compositionSizeReference: Int,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val state by nip05State.verificationState.collectAsStateWithLifecycle()

    if (state.isExpired()) {
        LaunchedEffect(key1 = state) {
            accountViewModel.runOnIO {
                nip05State.checkAndUpdate(accountViewModel.nip05ClientBuilder)
            }
        }
    }

    RenderNIP05VerifiedSymbol(state, compositionSizeReference, modifier, accountViewModel)
}

@Composable
fun RenderNIP05VerifiedSymbol(
    state: Nip05VerifState,
    compositionSizeReference: Int,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    CrossfadeIfEnabled(targetState = state) {
        when (it) {
            is Nip05VerifState.Verifying, is Nip05VerifState.NotStarted -> {
                // Treat "not started" and "in flight" as a single loading visual so the
                // badge doesn't blank-flash before the first check fires.
                Icon(
                    symbol = MaterialSymbols.Downloading,
                    contentDescription = stringRes(id = Res.string.nip05_checking),
                    modifier = modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }

            is Nip05VerifState.Verified -> {
                Icon(
                    painter = painterRes(Res.drawable.nip_05, compositionSizeReference),
                    contentDescription = stringRes(id = Res.string.nip05_verified),
                    modifier = modifier,
                    tint = MaterialTheme.colorScheme.nip05,
                )
            }

            is Nip05VerifState.Failed, is Nip05VerifState.Error -> {
                // Failed (pubkey mismatch) and Error (HTTP/network) carry distinct retry
                // timing but look the same to the user — both mean "badge isn't showing".
                Icon(
                    symbol = MaterialSymbols.Report,
                    contentDescription = stringRes(id = Res.string.nip05_failed),
                    modifier = modifier,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

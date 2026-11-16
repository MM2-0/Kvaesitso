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
package com.vitorpamplona.amethyst.commons.ui.note.types.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.util.isWebLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark

/**
 * A member that is a person. Loaded lazily and skipped until the profile arrives, because a bare
 * hex key in a list of people reads as noise rather than as a member.
 */
@Composable
fun UserMemberRow(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(pubKey) { user ->
        user?.let {
            Column(modifier = Modifier.fillMaxWidth()) {
                UserCompose(it, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

/**
 * A member that is a note or an addressable event — a bookmark, a curated article, a repository.
 *
 * [quotesLeft] guards the recursion: a bookmarked note can itself quote things, and NoteCompose
 * decrements it on the way down.
 */
@Composable
fun BookmarkMemberRow(
    item: BookmarkIdTag,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return

    when (item) {
        is AddressBookmark -> AddressMemberRow(item.address, quotesLeft, backgroundColor, accountViewModel, nav)

        is EventBookmark ->
            LoadNote(item.eventId) { note ->
                note?.let {
                    NoteCompose(
                        baseNote = it,
                        isQuotedNote = true,
                        quotesLeft = quotesLeft - 1,
                        parentBackgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
    }
}

/** A member named by coordinate: an article, a repository, an app, a DVM feed. */
@Composable
fun AddressMemberRow(
    address: Address,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return

    LoadAddressableNote(address) { note ->
        note?.let {
            NoteCompose(
                baseNote = it,
                isQuotedNote = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/**
 * A member that is just a word: a hashtag, a geohash, a muted word.
 *
 * [onClick] is optional because only some of these lead anywhere — a hashtag opens its feed, a
 * muted word opens nothing.
 */
@Composable
fun LabelMemberRow(
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        HorizontalDivider(thickness = DividerThickness)
    }
}

/** A hashtag member, which opens its feed. */
@Composable
fun HashtagMemberRow(
    hashtag: String,
    nav: INav,
) = LabelMemberRow("#$hashtag") { nav.nav(Route.Hashtag(hashtag)) }

/**
 * A member that is a web link (`r` tag). A preview card when the viewer has link previews on, a
 * plain clickable URL otherwise, the same choice the rich-text renderer makes for a link in a note.
 * Only `http(s)` links get either: anything else the author wrote (`javascript:`, `file:`,
 * `intent:`) is shown as plain text that opens nothing.
 */
@Composable
fun LinkMemberRow(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (!isWebLink(url)) {
        LabelMemberRow(url)
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (LocalMediaCapture.current != null || accountViewModel.settings.showUrlPreview()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LoadUrlPreview(url, url, null, accountViewModel, nav)
            }
        } else {
            ClickableUrl(
                url = url,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}

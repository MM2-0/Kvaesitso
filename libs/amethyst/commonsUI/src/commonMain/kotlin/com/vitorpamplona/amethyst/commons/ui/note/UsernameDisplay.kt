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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserDisplayNickname
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserNameText
import com.vitorpamplona.amethyst.commons.ui.note.hovercard.UserHoverCardAnchor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The note author's name. With [nav], resting the mouse on the name opens their
 * [UserHoverCardAnchor] card; [weight] then sizes the slot, and only the name itself is hoverable.
 */
@Composable
fun NoteUsernameDisplay(
    baseNote: Note,
    weight: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    WatchAuthor(baseNote, accountViewModel) {
        if (nav == null) {
            UsernameDisplay(it, weight, textColor = textColor, accountViewModel = accountViewModel)
        } else {
            Box(weight) {
                UserHoverCardAnchor(it, accountViewModel, nav) {
                    UsernameDisplay(it, textColor = textColor, accountViewModel = accountViewModel)
                }
            }
        }
    }
}

@Composable
fun WatchAuthor(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (User) -> Unit,
) {
    val noteAuthor = baseNote.author
    if (noteAuthor != null) {
        inner(noteAuthor)
    } else {
        val authorState by observeNote(baseNote, accountViewModel)
        authorState.note.author?.let {
            inner(it)
        }
    }
}

@Composable
fun WatchAuthorWithBlank(
    baseNote: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    inner: @Composable (User?) -> Unit,
) {
    val noteAuthor = baseNote.author
    if (noteAuthor != null) {
        inner(noteAuthor)
    } else {
        val authorState by observeNote(baseNote, accountViewModel)
        CrossfadeIfEnabled(targetState = authorState.note.author, modifier = modifier, label = "WatchAuthorWithBlank") { newAuthor ->
            inner(newAuthor)
        }
    }
}

@Composable
fun UsernameDisplay(
    baseUser: User,
    weight: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    accountViewModel: AccountViewModel,
) {
    val userMetadata by observeUserInfo(baseUser, accountViewModel)
    val nickname by observeUserDisplayNickname(baseUser, accountViewModel)

    CrossfadeIfEnabled(targetState = userMetadata, modifier = weight, label = "UsernameDisplay") {
        // the account's own nickname for this user wins over the user's metadata;
        // its custom emojis resolve against the contact card's tags, not the profile's
        val petName = nickname?.petName
        UserNameText(
            user = baseUser,
            displayName = petName ?: it?.info?.bestName(),
            tags = if (petName != null) nickname?.tags else it?.tags,
            modifier = weight,
            fontWeight = fontWeight,
            textColor = textColor,
            textAlign = textAlign,
        )
    }
}

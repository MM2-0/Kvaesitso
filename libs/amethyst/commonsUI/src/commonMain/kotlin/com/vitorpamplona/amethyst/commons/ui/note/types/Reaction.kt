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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ActivityBadge
import com.vitorpamplona.amethyst.commons.ui.note.ActivityCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.ActivityHeaderRow
import com.vitorpamplona.amethyst.commons.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.commons.ui.note.LikeTint
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.ui.note.RenderReaction as RenderReactionEmoji

/**
 * Renders a kind 7 reaction as an activity card, like the zap kinds, tinted
 * with the Liked heart color: badge + reactor → author on the first line and
 * the reacted-to post quoted inside the card.
 */
@Composable
fun RenderReaction(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val reactionType =
        remember(note.event) {
            note.event?.let { it.content.firstFullCharOrEmoji(ImmutableListOfLists(it.tags)) } ?: ""
        }

    ActivityCardFrame(LikeTint) { cardBackground ->
        ActivityHeaderRow(
            tint = LikeTint,
            pillLabel = "REACTION",
            badge = {
                if (reactionType == "+" || reactionType.isBlank()) {
                    ActivityBadge(LikeTint) {
                        LikedIcon(Modifier.size(16.dp), Color.White)
                    }
                } else {
                    RenderReactionEmoji(reactionType)
                }
            },
            senderAvatar = {
                val sender = note.author
                if (sender != null) {
                    UserPicture(sender, Size25dp, Modifier, accountViewModel, nav)
                } else {
                    DisplayBlankAuthor(Size25dp, accountViewModel = accountViewModel)
                }
            },
            recipientAvatar =
                note.replyTo?.lastOrNull()?.author?.let {
                    { UserPicture(it, Size25dp, Modifier, accountViewModel, nav) }
                },
        )

        RenderTargetNote(note, quotesLeft, cardBackground, accountViewModel, nav)
    }
}

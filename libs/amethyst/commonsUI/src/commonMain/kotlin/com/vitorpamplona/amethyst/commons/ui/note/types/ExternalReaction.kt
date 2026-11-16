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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ActivityBadge
import com.vitorpamplona.amethyst.commons.ui.note.ActivityCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.ActivityHeaderRow
import com.vitorpamplona.amethyst.commons.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.commons.ui.note.LikeTint
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip25Reactions.ExternalReactionEvent
import com.vitorpamplona.amethyst.commons.ui.note.RenderReaction as RenderReactionEmoji

/**
 * Renders a kind-17 reaction — NIP-25's form for reacting to something that is **not** a nostr
 * event: a web page, a podcast episode, a book, a paper.
 *
 * It borrows the activity-card frame from [RenderReaction] so a like reads the same whatever it
 * is aimed at, but the target cannot be a quoted note: there is no event to quote, only a NIP-73
 * external id. So the card names the target instead, as a link when it is one.
 */
@Composable
fun RenderExternalReaction(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ExternalReactionEvent ?: return

    val reactionType = remember(noteEvent) { noteEvent.content.firstFullCharOrEmoji(ImmutableListOfLists(noteEvent.tags)) }
    val target = remember(noteEvent) { noteEvent.externalIds().firstOrNull() }
    val url = remember(noteEvent) { noteEvent.openableUrl() }
    val externalKind = remember(noteEvent) { noteEvent.externalKinds().firstOrNull() }

    ActivityCardFrame(LikeTint) {
        ActivityHeaderRow(
            tint = LikeTint,
            pillLabel = "REACTION",
            badge = {
                if (noteEvent.isLike()) {
                    ActivityBadge(LikeTint) { LikedIcon(Modifier.size(16.dp), Color.White) }
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
            // There is no recipient: the thing reacted to is off nostr and has no pubkey.
            recipientAvatar = null,
        )

        if (target != null) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (url != null) {
                    ClickableUrlOrBlossom(urlText = url, url = url)
                } else {
                    // An ISBN or a podcast GUID is an identifier, not a link; show it as text
                    // rather than as something that looks tappable and does nothing.
                    Text(
                        text = target,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                externalKind?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

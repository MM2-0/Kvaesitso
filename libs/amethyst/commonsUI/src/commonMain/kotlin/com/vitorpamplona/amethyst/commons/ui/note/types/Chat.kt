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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip92IMeta.appendMissingImetaUrls
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReplyNoteComposition
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent

@Composable
fun RenderChat(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: ReplyRenderType,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    isBoostedNote: Boolean = false,
) {
    val noteEvent = note.event ?: return
    // The newest in-place edit (Buzz, Concord, Marmot) wins — the chat room shows it, so a
    // notification card or quote of the same message must too.
    val content = observeChatMessageEdit(note)?.event?.content ?: noteEvent.content
    val eventContent = content
    // Content actually handed to the media renderer. The shared renderer only shows media whose URL
    // appears in the text, but some NIP-C7/Concord clients (e.g. Ditto/Soapbox Armada) attach an
    // image purely as a NIP-92 `imeta` tag and leave the content empty. Append any imeta URL missing
    // from the content so those attachments render — symmetric to ChannelChat.imageMessage, which
    // appends the URL on send. Encrypted-blob key/nonce are already registered by URL, so the shared
    // pipeline fetches and decrypts them transparently.
    val displayContent = remember(noteEvent, content) { appendMissingImetaUrls(content, noteEvent) }

    // A boosted note inside a zap/nutzap/onchain activity card is always shown as a
    // compact 2-line preview, even when the logged-in user is only a zap-split
    // beneficiary (and thus not the author) of the post being zapped.
    if (makeItShort && (isBoostedNote || accountViewModel.isLoggedUser(note.author))) {
        Text(
            text = eventContent,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        // A kind-9 chat message carries its reply target as a NIP-18 `q` (or NIP-10 `e`)
        // tag, NOT as a NIP-10 thread — so it's not a BaseThreadedEvent and RenderTextEvent's
        // reply-to preview never fires for it. Render the quoted parent here so a Concord/MLS
        // chat reply shows what it's replying to wherever NoteCompose draws it (Notifications
        // tab, feed, threads) — the chat feed has its own reply-row and passes NONE.
        if (unPackReply == ReplyRenderType.FULL && !makeItShort) {
            val replyingDirectlyTo =
                remember(note) {
                    // Skip the preview when the parent is already cited inline (`nostr:...`) in the
                    // message — quotesLeft renders it at that spot, so a top preview would duplicate
                    // it. Happens with MLS/WhiteNoise quotes; Concord `q` replies aren't cited inline.
                    note.replyTo?.lastOrNull()?.takeUnless { parent ->
                        (noteEvent as? BaseNoteEvent)?.findCitations()?.contains(parent.idHex) == true
                    }
                }
            if (replyingDirectlyTo != null) {
                ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
                Spacer(modifier = StdVertSpacer)
            }
        }

        val callbackUri = remember(note) { note.toNostrUri() }

        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel,
        ) {
            val tags =
                remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

            TranslatableRichTextViewer(
                content = displayContent,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (noteEvent.hasHashtags()) {
            val callbackUri = remember(note) { note.toNostrUri() }
            DisplayUncitedHashtags(noteEvent, eventContent, callbackUri, accountViewModel, nav)
        }
    }
}

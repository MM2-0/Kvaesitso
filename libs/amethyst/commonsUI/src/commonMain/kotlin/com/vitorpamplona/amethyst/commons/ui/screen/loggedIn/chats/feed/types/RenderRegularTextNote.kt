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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.chats.ui.chatJumboEmojiAllowed
import com.vitorpamplona.amethyst.commons.chats.ui.jumboEmojiCount
import com.vitorpamplona.amethyst.commons.chats.ui.jumboEmojiFontSize
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip92IMeta.appendMissingImetaUrls
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.could_not_decrypt_the_message
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAudioFromIMeta
import com.vitorpamplona.amethyst.commons.ui.note.types.getAudioMetaWithWaveform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun RenderRegularTextNote(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadDecryptedContentOrNull(note = note, accountViewModel = accountViewModel) { eventContent ->
        if (eventContent != null) {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val jumboAllowed = chatJumboEmojiAllowed()
                val jumboCount = remember(eventContent, jumboAllowed) { if (jumboAllowed) jumboEmojiCount(eventContent) else 0 }

                // A voice message is an audio URL whose imeta carries the waveform.
                // The main feed's note renderer short-circuits those to the waveform
                // player; without the same check here the generic media pipeline
                // takes them and a recorded message arrives as a player surface
                // instead of the waveform it was previewed as. Matched against the
                // DECRYPTED content rather than `isAudioOnlyContent()`, because in a
                // chat the note's own `content` may still be the sealed payload.
                val audioMeta = remember(note.event) { note.event?.getAudioMetaWithWaveform() }

                if (audioMeta != null && eventContent.trim() == audioMeta.url) {
                    RenderAudioFromIMeta(note, accountViewModel, nav)
                } else if (jumboCount > 0) {
                    // Emoji-only messages render as jumbo emoji (the bubble behind
                    // them is transparent — see NormalChatNote).
                    Text(
                        text = eventContent.trim(),
                        fontSize = jumboEmojiFontSize(jumboCount),
                    )
                } else {
                    // Marmot custom emoji are encrypted attachments of the message, not URLs to fetch.
                    val marmotTags = rememberMarmotRenderTags(note, accountViewModel)
                    val ownTags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }
                    val tags = marmotTags ?: ownTags

                    // Some NIP-C7/Concord clients (e.g. Ditto/Soapbox Armada) attach an image purely as a
                    // NIP-92 `imeta` tag and leave the content empty. The shared renderer only shows media
                    // whose URL appears in the text, so append any imeta URL missing from the (decrypted)
                    // content — encrypted-blob key/nonce are already registered by URL, so the shared
                    // pipeline fetches and decrypts them transparently.
                    val displayContent = remember(note.event, eventContent) { appendMissingImetaUrls(eventContent, note.event) }

                    TranslatableRichTextViewer(
                        content = displayContent,
                        canPreview = canPreview,
                        quotesLeft = if (innerQuote) 0 else 1,
                        modifier = Modifier,
                        tags = tags,
                        backgroundColor = bgColor,
                        id = note.idHex,
                        callbackUri = note.toNostrUri(),
                        authorPubKey = note.author?.pubkeyHex,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else {
            TranslatableRichTextViewer(
                content = stringRes(id = Res.string.could_not_decrypt_the_message),
                canPreview = true,
                quotesLeft = 0,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = bgColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

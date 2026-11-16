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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.latestBuzzEdit
import com.vitorpamplona.amethyst.commons.model.latestConcordEdit
import com.vitorpamplona.amethyst.commons.model.latestMarmotEdit
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.replyingDirectlyTo
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCodeLocationBanner
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipTextView
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.commons.ui.note.ReplyNoteComposition
import com.vitorpamplona.amethyst.commons.ui.note.ReplyToLabel
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.DisplayCommentScope
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.datasources.PreloadThreadForReply
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

enum class ReplyRenderType {
    FULL,
    LINE,
    NONE,
}

/**
 * The text [RenderTextEvent] puts on screen for [note] given its decrypted [body]: a NIP-14
 * subject the body doesn't already repeat is prepended to it.
 *
 * This is the exact string handed to `TranslatableRichTextViewer`, so it is also the key the
 * translation cache stores the result under. The copy-text menus look their translation up by
 * the same function — keying on the raw body instead would miss the entry for every
 * subject-carrying note and silently copy the untranslated text.
 *
 * A kind-30041 publication section is the other text whose screen string differs from its
 * content: it renders (and translates) the markdown its AsciiDoc body converts to, so that is
 * what this returns for one. A kind-30817 NIP is the third: its header already shows the
 * title and status the content opens with, so this returns the body without them.
 */
fun displayedNoteText(
    note: Note,
    body: String,
): String {
    (note.event as? PublicationContentEvent)?.let { return publicationSectionMarkdown(it) }
    // A NIP's header shows its title and status, so the body drops the headings that repeat them.
    (note.event as? NipTextEvent)?.let { if (it.content == body) return NipTextView.of(it).body }

    val subject = (note.event as? TextNoteEvent)?.subject()?.ifBlank { null }
    return if (subject != null && !body.contains(subject, ignoreCase = true)) {
        "$subject\n\n$body"
    } else {
        body
    }
}

@Composable
fun RenderTextEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: ReplyRenderType,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    isBoostedNote: Boolean = false,
) {
    val noteEvent = note.event ?: return

    if (unPackReply != ReplyRenderType.NONE) {
        // Eagerly pull the rest of this reply's thread while it's on screen, so opening
        // the conversation finds it already loaded. No-op when the note is a root itself.
        PreloadThreadForReply(note, accountViewModel)

        val canShowReply by
            remember(note) {
                derivedStateOf {
                    noteEvent is BaseThreadedEvent && !makeItShort && (note.replyTo != null || noteEvent.mentionKeys().isNotEmpty())
                }
            }

        val parentNote = remember(note) { replyingDirectlyTo(note, LocalCache) }

        if (parentNote != null && canShowReply) {
            when (unPackReply) {
                ReplyRenderType.FULL -> {
                    ReplyNoteComposition(parentNote, backgroundColor, accountViewModel, nav)
                    Spacer(modifier = StdVertSpacer)
                }

                ReplyRenderType.LINE -> {
                    // Zap receipts are signed by the recipient's lightning provider;
                    // label the reply with the zap sender instead of the service key.
                    val zapSender =
                        if (parentNote.event is ZapReceiptEvent) {
                            observeZapSender(parentNote, accountViewModel).value
                        } else {
                            null
                        }
                    val parentAuthor = zapSender ?: parentNote.author
                    if (parentAuthor != null) {
                        ReplyToLabel(
                            parentAuthorDisplay = parentAuthor.toBestDisplayName(),
                            onClick = { nav.nav(routeFor(parentAuthor)) },
                        )
                        Spacer(modifier = HalfVertSpacer)
                    }
                }
            }
        } else if (!makeItShort && noteEvent is CommentEvent) {
            // No in-cache parent note: this comment answers a scope rather than a note.
            // What a comment answers is context, like a quote: its card keeps the margin.
            ProvideMediaBleed(0.dp) { DisplayCommentScope(noteEvent, accountViewModel, nav) }
        }
    }

    // An inline code-review comment (NIP-22 on a patch or PR, with `f`/`line`): say which lines.
    if (noteEvent is CommentEvent) {
        val location = remember(noteEvent) { noteEvent.gitCodeLocation() }
        if (location != null) {
            GitCodeLocationBanner(location)
            Spacer(modifier = HalfVertSpacer)
        }
    }

    // Check if this is an audio-only event (content is just an audio URL with waveform IMeta)
    val isAudioOnly = remember(noteEvent) { noteEvent.isAudioOnlyContent() }
    if (isAudioOnly) {
        RenderAudioFromIMeta(note, accountViewModel, nav)
        return
    }

    // A chat message edited in place (Buzz kind-40003, Concord, Marmot) shows its newest edit here
    // too — notification cards and quotes render it through this path, not the chat bubble.
    val chatEdit = if (noteEvent is ChatEvent || noteEvent is StreamMessageV2Event) observeChatMessageEdit(note) else null

    LoadDecryptedContent(
        note,
        accountViewModel,
    ) { body ->
        val newBody =
            if (editState.value is GenericLoadable.Loaded) {
                (editState.value as? GenericLoadable.Loaded)
                    ?.loaded
                    ?.modificationToShow
                    ?.value
                    ?.event
                    ?.content ?: body
            } else {
                chatEdit?.event?.content ?: body
            }

        val eventContent = remember(newBody) { displayedNoteText(note, newBody) }

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
            val callbackUri = remember(note) { note.toNostrUri() }

            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags =
                    remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id =
                        if (editState.value is GenericLoadable.Loaded) {
                            (editState.value as GenericLoadable.Loaded<EditState>)
                                .loaded.modificationToShow.value
                                ?.idHex ?: note.idHex
                        } else {
                            note.idHex
                        },
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (noteEvent.hasHashtags()) {
                DisplayUncitedHashtags(noteEvent, eventContent, callbackUri, accountViewModel, nav)
            }
        }
    }
}

/** The newest in-place edit of a chat message (Concord, Buzz or Marmot), recomposing as edits land. */
@Composable
fun observeChatMessageEdit(note: Note): Note? {
    // State keyed by the note, not produceState: produceState's state outlives a key change,
    // so a row reused for another note showed that note's previous edit until the first emission.
    val latest = remember(note) { mutableStateOf(note.latestChatEdit()) }
    LaunchedEffect(note) {
        note
            .flow()
            .edits.stateFlow
            .collect { latest.value = note.latestChatEdit() }
    }
    return latest.value
}

private fun Note.latestChatEdit(): Note? = latestConcordEdit() ?: latestBuzzEdit() ?: latestMarmotEdit()

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitActivityTarget
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCoverNoteHeader
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent

/**
 * A NIP-34 cover note (kind 1624) on its own — in an item's history, a quote, a repost or a
 * notification: the "Cover note" label, the markdown body, and the issue / patch / PR it covers
 * (linked through its root `e`) quoted below. Whether it is the one *displayed* on that item is
 * decided by GitCoverNotes, which only trusts the item author and the repository maintainers; this
 * card shows any note, signed by whoever wrote it.
 */
@Composable
fun RenderGitCoverNoteEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? GitCoverNoteEvent ?: return

    GitCoverNoteHeader(event, accountViewModel, showAuthor = false)

    Spacer(modifier = StdVertSpacer)

    val tags = remember(event) { event.tags.toImmutableListOfLists() }
    TranslatableRichTextViewer(
        content = event.content,
        canPreview = canPreview && !makeItShort,
        quotesLeft = quotesLeft,
        modifier = Modifier.fillMaxWidth(),
        tags = tags,
        backgroundColor = backgroundColor,
        id = note.idHex,
        callbackUri = note.toNostrUri(),
        authorPubKey = event.pubKey,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (!makeItShort && note.replyTo?.lastOrNull() != null) {
        Spacer(modifier = StdVertSpacer)
        GitActivityTarget(note, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

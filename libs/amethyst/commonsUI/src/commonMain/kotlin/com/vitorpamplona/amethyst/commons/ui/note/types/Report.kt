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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip56Reports.ui.reportTypeLabel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.report_card_title
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip56Reports.ReportEvent

/**
 * A NIP-56 report, drawn as a report: a flag with the reason, then WHO is reported and what. It used
 * to render as a text note holding only the reason ("Spam"), so a report about a person showed no
 * person at all, which was most confusing exactly where it matters most, in a signer consent prompt.
 */
@Composable
fun RenderReport(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ReportEvent ?: return

    val reportTypes =
        remember(noteEvent) {
            (noteEvent.reportedPost() + noteEvent.reportedAuthor())
                .mapTo(LinkedHashSet()) { it.type }
        }
    val reason = reportTypes.map { reportTypeLabel(it) }.joinToString(", ")
    val people = remember(noteEvent) { noteEvent.reportedAuthor().map { it.pubKey }.distinct() }
    val reportedPostIds = remember(noteEvent) { noteEvent.reportedPost().map { it.eventId }.toSet() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Report,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringRes(Res.string.report_card_title, reason),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // The person a report accuses is the point of it. Skipped when the report is about one of
        // their posts, which already shows them as its author below.
        if (reportedPostIds.isEmpty()) {
            people.forEach { UserMemberRow(it, accountViewModel, nav) }
        }

        noteEvent.content.ifBlank { null }?.let { comment ->
            TranslatableRichTextViewer(
                content = comment,
                canPreview = true,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                quotesLeft = 1,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        note.replyTo?.lastOrNull()?.let {
            NoteCompose(
                baseNote = it,
                modifier = MaterialTheme.colorScheme.replyModifier,
                isQuotedNote = true,
                unPackReply = ReplyRenderType.NONE,
                makeItShort = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

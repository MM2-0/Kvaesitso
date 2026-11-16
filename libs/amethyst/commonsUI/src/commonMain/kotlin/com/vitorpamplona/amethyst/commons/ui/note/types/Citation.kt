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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.citation_label_default
import com.vitorpamplona.amethyst.commons.resources.citation_label_print
import com.vitorpamplona.amethyst.commons.resources.citation_label_prompt
import com.vitorpamplona.amethyst.commons.resources.citation_label_web
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.citations.CitationEvent
import com.vitorpamplona.quartz.experimental.citations.ExternalCitationEvent
import com.vitorpamplona.quartz.experimental.citations.HardcopyCitationEvent
import com.vitorpamplona.quartz.experimental.citations.PromptCitationEvent

/**
 * Renders a citation (kinds 31, 32 and 33) — a reference to a source, published as its own event.
 *
 * One card for all three, because they are one idea aimed at three sorts of source and a reader
 * should not have to learn three layouts: an icon that says *what kind of source*, the source's
 * name, a line of provenance built from whichever fields the citer supplied, and the citer's own
 * note underneath.
 *
 * The provenance line is assembled rather than templated. A citation may carry an author, a
 * containing work, a volume, a page range, a date, a DOI, a model name — and almost never all of
 * them, so a fixed layout would be mostly blank labels. Joining only what is present keeps the
 * card honest about how much the citer actually recorded.
 */
@Composable
fun RenderCitation(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? CitationEvent ?: return

    val title = remember(noteEvent) { noteEvent.displayTitle() }
    val provenance = remember(noteEvent) { provenanceOf(noteEvent) }
    val url = remember(noteEvent) { urlOf(noteEvent) }

    Column(Modifier.fillMaxWidth()) {
        Column(MaterialTheme.colorScheme.replyModifier) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Size5dp)) {
                Icon(
                    symbol = iconFor(noteEvent),
                    contentDescription = null,
                    modifier = Modifier.width(16.dp),
                    tint = MaterialTheme.colorScheme.grayText,
                )

                Spacer(Modifier.width(Size5dp))

                Text(
                    text = labelFor(noteEvent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            provenance?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            url?.let {
                Spacer(Modifier.padding(top = Size5dp))
                ClickableUrlOrBlossom(urlText = it, url = it)
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
            val modifier = Modifier.fillMaxWidth().padding(top = 6.dp)

            // A prompt citation's body is the prompt itself, which the reference implementation
            // renders as Markdown; the other two carry a plain note.
            if (noteEvent is PromptCitationEvent) {
                LocalRichTextPlatform.current.Markdown(
                    content = noteEvent.content,
                    tags = tags,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    backgroundColor = backgroundColor,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                TranslatableRichTextViewer(
                    content = noteEvent.content,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = modifier,
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

private fun iconFor(event: CitationEvent): MaterialSymbol =
    when (event) {
        is ExternalCitationEvent -> MaterialSymbols.Link
        is HardcopyCitationEvent -> MaterialSymbols.MenuBook
        is PromptCitationEvent -> MaterialSymbols.AutoAwesome
        else -> MaterialSymbols.AutoMirrored.Article
    }

@Composable
private fun labelFor(event: CitationEvent): String =
    when (event) {
        is ExternalCitationEvent -> stringRes(Res.string.citation_label_web)
        is HardcopyCitationEvent -> stringRes(Res.string.citation_label_print)
        is PromptCitationEvent -> stringRes(Res.string.citation_label_prompt)
        else -> stringRes(Res.string.citation_label_default)
    }

private fun urlOf(event: CitationEvent): String? =
    when (event) {
        is ExternalCitationEvent -> event.url()
        is PromptCitationEvent -> event.url()
        else -> null
    }

/**
 * The provenance line: whichever of author / containing work / volume / pages / date / identifier
 * the citer recorded, in the order a reader expects to scan them.
 */
private fun provenanceOf(event: CitationEvent): String? {
    val parts =
        buildList {
            event.author()?.let { add(it) }

            when (event) {
                is HardcopyCitationEvent -> {
                    event.publishedIn()?.let { work ->
                        add(event.volume()?.let { "$work $it" } ?: work)
                    }
                    event.pageRange()?.let { add("pp. $it") }
                    event.doi()?.let { add("doi:$it") }
                    event.editor()?.let { add("ed. $it") }
                }

                is PromptCitationEvent -> event.llm()?.let { add(it) }

                else -> Unit
            }

            event.publishedBy()?.let { add(it) }
            event.publishedOn()?.let { add(it) }
            event.location()?.let { add(it) }
        }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Preview
@Composable
fun CitationProvenancePreview() {
    ThemeComparisonColumn {
        Column {
            // The point of assembling rather than templating: these carry very different fields.
            Text(provenanceOf(previewHardcopy()) ?: "")
            Text(provenanceOf(previewPrompt()) ?: "")
        }
    }
}

private fun previewHardcopy() =
    HardcopyCitationEvent(
        "id",
        "pk",
        0L,
        arrayOf(
            arrayOf("title", "The Farmer and The Snake"),
            arrayOf("author", "Aesop"),
            arrayOf("published_in", "Aesop's Fables", "3rd ed."),
            arrayOf("page_range", "14-15"),
        ),
        "",
        "sig",
    )

private fun previewPrompt() =
    PromptCitationEvent(
        "id",
        "pk",
        0L,
        arrayOf(arrayOf("llm", "Claude"), arrayOf("published_on", "2026-09-08")),
        "Summarize the fable.",
        "sig",
    )

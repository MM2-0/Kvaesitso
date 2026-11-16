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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipDefinedKind
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipTextView
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_nip
import com.vitorpamplona.amethyst.commons.resources.kinds
import com.vitorpamplona.amethyst.commons.resources.nip_contents
import com.vitorpamplona.amethyst.commons.resources.nip_contents_sections
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.richtext.MarkdownHeading
import com.vitorpamplona.amethyst.commons.ui.markdown.MarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.kinds.KindNames

/**
 * Feed card for a NIP published on Nostr ("NIPs on Nostr"): its NIP/status badges, title, summary
 * (or its opening paragraph as plain text) and the kinds it defines, each opening its kind page. The
 * full text is read in the thread view, under [RenderNipHeaderForThread].
 */
@Composable
fun NipCard(
    noteEvent: NipTextEvent,
    nav: INav? = null,
) {
    val view = remember(noteEvent) { NipTextView.of(noteEvent) }
    val text = view.summary ?: view.preview.ifBlank { null }

    Column(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ).padding(12.dp),
    ) {
        NipBadges(view)

        view.title?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        text?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (view.kinds.isNotEmpty()) {
            Spacer(modifier = DoubleVertSpacer)
            NipKinds(view.kinds, onKindClick = nav?.let { { kind: Int -> it.nav(Route.EventKind(kind)) } })
        }
    }
}

/**
 * Header of a NIP opened in the thread view: badges, title, summary, the kinds it defines and,
 * for a long one, its table of contents. The body below it is the NIP's Markdown, without the
 * headings and status line shown here.
 */
@Composable
fun RenderNipHeaderForThread(
    noteEvent: NipTextEvent,
    nav: INav,
    anchors: MarkdownAnchors?,
) {
    val view = remember(noteEvent) { NipTextView.of(noteEvent) }
    NipHeader(view, nav, anchors, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
}

/** The header of a NIP being read, on Nostr or official. */
@Composable
fun NipHeader(
    view: NipTextView,
    nav: INav,
    anchors: MarkdownAnchors?,
    modifier: Modifier = Modifier,
) {
    // The body may render translated; its sections are found by their place among the originals.
    if (anchors != null) remember(anchors, view) { anchors.useSections(view.sections.map { it.anchor }) }

    Column(modifier = modifier) {
        NipBadges(view)

        view.title?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        view.summary?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (view.kinds.isNotEmpty()) {
            Spacer(modifier = DoubleVertSpacer)
            NipKinds(view.kinds, onKindClick = { nav.nav(Route.EventKind(it)) })
        }

        if (anchors != null && view.hasContents) {
            Spacer(modifier = DoubleVertSpacer)
            NipContents(view.sections) { anchors.jumpTo(it) }
        }
    }
}

/** `NIP-XX` (or just `NIP`), then the status tag and the nips repo's `draft` `optional` flags. */
@Composable
fun NipBadges(view: NipTextView) {
    FlowRow(
        horizontalArrangement = SpacedBy5dp,
        verticalArrangement = SpacedBy5dp,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        NipPill(
            label = view.number?.let { stringRes(Res.string.nip_number, it) } ?: stringRes(Res.string.kind_nip),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            bold = true,
        )
        view.status?.let { StatusPill(it) }
        view.flags.forEach { StatusPill(it) }
    }
}

@Composable
fun StatusPill(status: String) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (status.lowercase()) {
            "final", "mandatory", "merged", "accepted" -> colors.primaryContainer to colors.onPrimaryContainer
            "review", "proposed", "proposal" -> colors.tertiaryContainer to colors.onTertiaryContainer
            "deprecated", "unrecommended", "rejected", "withdrawn" -> colors.errorContainer to colors.onErrorContainer
            else -> colors.surfaceVariant to colors.onSurfaceVariant
        }
    NipPill(status, container, content)
}

/** "Kinds" followed by one chip per kind: its number and the NIP's name for it, or a known name. */
@Composable
fun NipKinds(
    kinds: List<NipDefinedKind>,
    onKindClick: ((Int) -> Unit)?,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = SpacedBy5dp,
        verticalArrangement = SpacedBy5dp,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(Res.string.kinds),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
        )
        kinds.forEach { kind ->
            val name = kind.label ?: knownKindName(kind.kind)
            NipPill(
                label = if (name != null) "${kind.kind} · $name" else kind.kind.toString(),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onKindClick?.let { { it(kind.kind) } },
            )
        }
    }
}

/**
 * The NIP's sections, folded to one line ("Contents · 7 sections") until opened. A section
 * scrolls the reader to its heading.
 */
@Composable
fun NipContents(
    sections: List<MarkdownHeading>,
    onSection: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = remember(sections) { sections.filter { it.level <= 3 } }
    val top = remember(shown) { shown.minOfOrNull { it.level } ?: 2 }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringRes(Res.string.nip_contents),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " · " + pluralStringRes(Res.plurals.nip_contents_sections, shown.size, shown.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
        if (open) {
            Column(modifier = Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                shown.forEach { section ->
                    Text(
                        text = section.text,
                        style = if (section.level == top) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = if (section.level == top) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSection(section.anchor) }
                                .padding(start = 12.dp + (14 * (section.level - top)).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun knownKindName(kind: Int): String? = kindDisplayName(kind)?.let { stringRes(it) } ?: KindNames.nameFor(kind)

@Composable
fun NipPill(
    label: String,
    container: Color,
    content: Color,
    bold: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        modifier = if (onClick != null) Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else null,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val previewEvent =
    NipTextEvent(
        id = "eb2b05394ff0014bb6a79c2eacfd1c80696821592f4dbf86c950c4bf16614aa0",
        pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
        createdAt = 1794580631,
        content =
            "A user stores the API key of their AI provider so every client they sign into can run AI\n" +
                "tasks for them, billed to the user.\n\n## Event\n\nKind `12000`, replaceable, no required tags. " +
                "`content` is a [NIP-44](44.md) ciphertext.",
        sig = "7fa9f1d49c41c7bfbdad6d089d1c6685777c86de8fbda7662a630888658839f0444cb1398385f759461c09191b287fa222eec0ffe22b3fa8cf740f71aab9dc21",
        tags =
            arrayOf(
                arrayOf("d", "ai-agent-keys"),
                arrayOf("title", "AI Agent Keys"),
                arrayOf("k", "12000", "AI Agent Keys"),
                arrayOf("status", "review"),
                arrayOf("client", "NostrHub"),
            ),
    )

@Preview
@Composable
fun NipCardPreview() {
    ThemeComparisonColumn(
        toPreview = { NipCard(previewEvent) },
    )
}

@Preview
@Composable
fun NipHeaderForThreadPreview() {
    ThemeComparisonColumn(
        toPreview = { NipHeader(NipTextView.of(previewEvent), EmptyNav(), null) },
    )
}

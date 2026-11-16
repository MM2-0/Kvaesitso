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

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_merge_accepted
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_merge_request
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_merged_version
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_redirect
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_request
import com.vitorpamplona.amethyst.commons.resources.wiki_collab_version_to_merge
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeAcceptanceEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeRequestEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent

/**
 * Renders a kind-818 wiki merge request — NIP-54, Appendix 1: "please fold my fork of this
 * article back into yours".
 *
 * Both ends are shown as rows that resolve to the articles they name, because a merge request
 * that only says "a merge was requested" tells a reader nothing actionable.
 */
@Composable
fun RenderWikiMergeRequest(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? WikiMergeRequestEvent ?: return

    val target = remember(noteEvent) { noteEvent.targetArticle() }
    val source = remember(noteEvent) { noteEvent.mergeSource() }

    Column(Modifier.fillMaxWidth()) {
        Column(MaterialTheme.colorScheme.replyModifier) {
            WikiCollabLabel(MaterialSymbols.Forward, stringRes(Res.string.wiki_collab_merge_request))

            target?.let { WikiArticleRow(it, accountViewModel, nav) }

            source?.let {
                Spacer(Modifier.padding(top = Size5dp))
                WikiVersionRow(it, stringRes(Res.string.wiki_collab_version_to_merge), accountViewModel, nav)
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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

/**
 * Renders a kind-819 merge acceptance: the request that was accepted and the version it produced.
 *
 * Not a NIP-54 kind — see [WikiMergeAcceptanceEvent] for why it exists at all.
 */
@Composable
fun RenderWikiMergeAcceptance(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? WikiMergeAcceptanceEvent ?: return

    val result = remember(noteEvent) { noteEvent.result() }
    val request = remember(noteEvent) { noteEvent.request() }

    Column(MaterialTheme.colorScheme.replyModifier) {
        WikiCollabLabel(MaterialSymbols.Check, stringRes(Res.string.wiki_collab_merge_accepted))

        result?.let { WikiVersionRow(it, stringRes(Res.string.wiki_collab_merged_version), accountViewModel, nav) }

        request?.let {
            Spacer(Modifier.padding(top = Size5dp))
            WikiVersionRow(it, stringRes(Res.string.wiki_collab_request), accountViewModel, nav)
        }
    }
}

/**
 * Renders a kind-30819 wiki redirect: "this name should take you to that article".
 *
 * NIP-54 never gives this kind an example event, so the shape rendered here is the one the
 * publishing clients emit — see [WikiRedirectEvent].
 */
@Composable
fun RenderWikiRedirect(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: these kinds are DEFINED by replacement — a newer rating,
    // review or redirect lands on the same Note instance. `Note` is @Stable and `event`
    // is a plain @Volatile var, so a bare read registers no snapshot dependency and
    // Compose would keep showing the superseded version.
    val observedEvent by observeNoteEvent<WikiRedirectEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val from = remember(noteEvent) { noteEvent.fromSlug() }
    val target = remember(noteEvent) { noteEvent.target() }

    Column(MaterialTheme.colorScheme.replyModifier) {
        WikiCollabLabel(MaterialSymbols.Forward, stringRes(Res.string.wiki_collab_redirect))

        if (from.isNotEmpty()) {
            Text(
                text = from,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        target?.let { WikiArticleRow(it, accountViewModel, nav) }
    }
}

@Composable
private fun WikiCollabLabel(
    symbol: MaterialSymbol,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Size5dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.width(16.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )

        Spacer(Modifier.width(Size5dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}

/** An article named by coordinate — the merge target, or a redirect's destination. */
@Composable
private fun WikiArticleRow(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { articleNote ->
        if (articleNote != null) {
            val article by observeNoteEvent<WikiArticleEvent>(articleNote, accountViewModel)

            WikiRow(
                // The coordinate's own identifier is a readable slug, so it stands in until the
                // article arrives rather than leaving the row blank.
                title = article?.title() ?: address.dTag,
                caption = null,
                onClick = { nav.nav(Route.Note(articleNote.idHex)) },
            )
        } else {
            WikiRow(title = address.dTag, caption = null, onClick = null)
        }
    }
}

/** A specific article revision, named by event id. */
@Composable
private fun WikiVersionRow(
    eventId: String,
    caption: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(eventId) { versionNote ->
        if (versionNote != null) {
            val article by observeNoteEvent<WikiArticleEvent>(versionNote, accountViewModel)

            WikiRow(
                title = article?.title() ?: eventId.take(8),
                caption = caption,
                onClick = { nav.nav(Route.Note(versionNote.idHex)) },
            )
        } else {
            WikiRow(title = eventId.take(8), caption = caption, onClick = null)
        }
    }
}

@Composable
private fun WikiRow(
    title: String,
    caption: String?,
    onClick: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
    ) {
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

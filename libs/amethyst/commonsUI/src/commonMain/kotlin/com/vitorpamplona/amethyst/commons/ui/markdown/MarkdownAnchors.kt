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
package com.vitorpamplona.amethyst.commons.ui.markdown

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lets a reader jump to a section of a Markdown document: the Markdown renderer splits the
 * document at its headings while one of these is provided ([LocalMarkdownAnchors]) and registers
 * where each section starts; a table of contents or a `[link](#section)` then scrolls [scrollable]
 * to it. [viewport] is the scrolling container, and [topInsetPx] the part of it a top bar covers.
 *
 * Sections are also registered by their position among the document's headings, and the owner can
 * name the document's original anchors ([useSections]). A translated body renders its headings in
 * another language, so their own anchors differ from a table of contents or a `#Encryption` link
 * written against the original; the position still finds them.
 *
 * Positions are read when a jump is asked for, so they are right however far the reader scrolled.
 * A section whose layout left the screen (a lazy list recycled it) can't be reached until it is
 * composed again; for a document's own page that never happens.
 */
@Stable
class MarkdownAnchors(
    private val scrollable: ScrollableState,
    private val scope: CoroutineScope,
    private val topInsetPx: () -> Float,
) {
    private val targets = HashMap<String, LayoutCoordinates>()
    private val byPosition = HashMap<Int, LayoutCoordinates>()
    private var sections: List<String> = emptyList()
    private var viewport: LayoutCoordinates? = null

    /** The document's anchors in heading order, as written (untranslated). */
    fun useSections(anchors: List<String>) {
        sections = anchors
    }

    fun registerViewport(coordinates: LayoutCoordinates) {
        viewport = coordinates
    }

    /** Registers the section that opens with the [position]th heading of the document. */
    fun register(
        anchor: String,
        position: Int,
        coordinates: LayoutCoordinates,
    ) {
        targets[anchor] = coordinates
        byPosition[position] = coordinates
    }

    /**
     * The section a link points at, when it is one of this document's: `#Encryption`, or the
     * hashtag form the Markdown library turns a `#…` link into (`nostr:hashtag?id=Encryption`).
     * Null for anything else, so a real hashtag still opens its feed.
     */
    fun anchorOfLink(uri: String): String? {
        val raw =
            when {
                uri.startsWith("#") -> uri.removePrefix("#")
                uri.startsWith(HASHTAG_URI) -> uri.removePrefix(HASHTAG_URI)
                uri.startsWith(NOSTR_HASHTAG_URI) -> uri.removePrefix(NOSTR_HASHTAG_URI)
                else -> return null
            }
        val key = MarkdownChunks.anchorOf(raw)
        return key.takeIf { it in targets || it in sections }
    }

    /** Scrolls to [anchor] (`encryption`, `#Encryption`); false when the section isn't laid out. */
    fun jumpTo(anchor: String): Boolean {
        val key = MarkdownChunks.anchorOf(anchor.removePrefix("#"))
        // The original section's position first: it survives translation; the rendered anchor otherwise.
        val position = sections.indexOf(key)
        val target = (if (position >= 0) byPosition[position] else null) ?: targets[key] ?: return false
        val view = viewport ?: return false
        if (!target.isAttached || !view.isAttached) return false
        val delta = target.positionInRoot().y - view.positionInRoot().y - topInsetPx()
        scope.launch { scrollable.animateScrollBy(delta) }
        return true
    }
}

private const val HASHTAG_URI = "hashtag?id="
private const val NOSTR_HASHTAG_URI = "nostr:hashtag?id="

/** The anchors of the document being read; null where headings needn't be reachable (feeds). */
val LocalMarkdownAnchors = staticCompositionLocalOf<MarkdownAnchors?> { null }

@Composable
fun rememberMarkdownAnchors(
    scrollable: ScrollableState,
    topInsetPx: () -> Float = { 0f },
): MarkdownAnchors {
    val scope = rememberCoroutineScope()
    return remember(scrollable, scope) { MarkdownAnchors(scrollable, scope, topInsetPx) }
}

/**
 * Marks the scrolling container the anchors measure against. onPlaced, not onGloballyPositioned:
 * the coordinates are only read when a jump is asked for, so nothing needs to run while scrolling.
 */
fun Modifier.markdownAnchorViewport(anchors: MarkdownAnchors?): Modifier = if (anchors == null) this else onPlaced { anchors.registerViewport(it) }

/** Marks where the section [anchor], the [position]th heading of the document, starts. */
fun Modifier.markdownAnchor(
    anchors: MarkdownAnchors?,
    anchor: String?,
    position: Int,
): Modifier = if (anchors == null || anchor == null) this else onPlaced { anchors.register(anchor, position, it) }

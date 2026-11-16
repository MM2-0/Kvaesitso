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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.collection.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.resources.show_more
import com.vitorpamplona.amethyst.commons.richtext.ExpandableTextCutOffCalculator
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

object ShowFullTextCache {
    val cache = LruCache<String, Boolean>(10)
}

@Composable
fun ExpandableRichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    id: String,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ExpandableTextViewer(content, modifier, backgroundColor, id) { text, textModifier ->
        RichTextViewer(
            text,
            canPreview,
            quotesLeft,
            textModifier,
            tags,
            backgroundColor,
            callbackUri,
            authorPubKey,
            accountViewModel,
            nav,
        )
    }
}

/**
 * Crops [content] to a preview with "Show More" under it, and "Show Less" once expanded, drawing
 * the text through [displayText]. [ExpandableRichTextViewer] hands it the rich-text viewer; a
 * kind whose body is always one format (a markdown chapter) hands it that renderer directly.
 *
 * [displayText] gets the visible text and the modifier to lay it out with, which carries the
 * room the inline "Show Less" button sits in.
 */
@Composable
fun ExpandableTextViewer(
    content: String,
    modifier: Modifier,
    backgroundColor: MutableState<Color>,
    id: String,
    displayText: @Composable (String, Modifier) -> Unit,
) {
    var showFullText by
        rememberSaveable {
            val cached = ShowFullTextCache.cache[id]
            if (cached == null) {
                ShowFullTextCache.cache.put(id, false)
                mutableStateOf(false)
            } else {
                mutableStateOf(cached)
            }
        }

    val whereToCut = remember(content) { ExpandableTextCutOffCalculator.indexToCutOff(content) }

    val text by
        remember(content) {
            derivedStateOf {
                if (showFullText) {
                    content
                } else {
                    content.take(whereToCut)
                }
            }
        }

    val canCollapse = showFullText && content.length > whereToCut
    val gutter = LocalExpandedTextGutter.current

    if (canCollapse && gutter != null) {
        // The note measures itself in its author column. When it is taller than the feed, "Show
        // Less" is pinned there while the text scrolls by; otherwise it stays under the text.
        DisposableEffect(gutter, id) {
            val collapse: () -> Unit = {
                showFullText = false
                ShowFullTextCache.cache.put(id, false)
            }
            gutter.register(collapse)
            onDispose { gutter.unregister(collapse) }
        }
    }

    // "Show Less" goes where "Show More" was, under the text, when the note fits on screen or there
    // is no author column to pin it in (chats, profiles, quotes).
    val showLessInline = canCollapse && (gutter == null || !gutter.pinned)
    val revealTop = if (showLessInline && gutter == null) rememberRevealTop() else null

    // One call site for the text in every state: expanding, collapsing or pinning never throws away
    // what it has laid out (loaded images, players). The inline button sits in the text's padding.
    Box(modifier = revealTop?.modifier ?: Modifier) {
        displayText(
            text,
            if (showLessInline) modifier.align(Alignment.TopStart).padding(bottom = ShowLessRowHeight) else modifier.align(Alignment.TopStart),
        )

        if (content.length > whereToCut && !showFullText) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        // Fades the edges of media that bleeds past the text, too.
                        .bleedHorizontally(LocalMediaBleed.current)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton {
                    showFullText = !showFullText
                    ShowFullTextCache.cache.put(id, showFullText)
                }
            }
        }

        if (showLessInline) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                ShowLessButton {
                    // In a note, bring the whole note back, author row included.
                    if (gutter != null) gutter.revealNoteTop() else revealTop?.reveal()
                    showFullText = false
                    ShowFullTextCache.cache.put(id, false)
                }
            }
        }
    }
}

private val ShowLessRowHeight = 40.dp

@Composable
fun ShowMoreButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = StdTopPadding,
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ShowMoreButtonPadding,
    ) {
        Text(text = stringRes(Res.string.show_more))
        Spacer(Modifier.width(4.dp))
        Icon(symbol = MaterialSymbols.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

private val ShowMoreButtonPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)

/**
 * The quiet half of the [ShowMoreButton] pair: same place, same shape, mirrored icon, but no fill and
 * a muted color, since collapsing is something people rarely reach for.
 */
@Composable
fun ShowLessButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ShowLessButtonPadding,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.placeholderText),
    ) {
        Text(text = stringRes(Res.string.show_less), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Icon(symbol = MaterialSymbols.UnfoldLess, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

private val ShowLessButtonPadding = PaddingValues(horizontal = 12.dp)

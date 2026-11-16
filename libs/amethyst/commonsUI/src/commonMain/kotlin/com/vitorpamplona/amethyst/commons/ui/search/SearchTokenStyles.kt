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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.vitorpamplona.amethyst.commons.search.SearchSegment

/**
 * How each kind of token is drawn inside the field.
 *
 * A chip is a tinted background plus a colour, not a border: the field is one run of text with a
 * caret moving through it, and a boxed chip would break the line's rhythm every time a token
 * settled. The tint is what says "this word is a filter, not a search term" — the distinction a
 * reader most needs, because `#bitcoin` typed as a term and `#bitcoin` lifted into a `#t` filter
 * return very different result sets.
 */
@Immutable
data class SearchTokenStyles(
    val person: SpanStyle,
    val pointer: SpanStyle,
    val hashtag: SpanStyle,
    val date: SpanStyle,
    val label: SpanStyle,
    val scope: SpanStyle,
    val group: SpanStyle,
    val kind: SpanStyle,
    val extension: SpanStyle,
    val exclusion: SpanStyle,
    val phrase: SpanStyle,
) {
    fun styleFor(segment: SearchSegment): SpanStyle? =
        when (segment) {
            is SearchSegment.Text -> null
            is SearchSegment.Key -> person
            is SearchSegment.Pointer -> pointer
            is SearchSegment.Hashtag -> hashtag
            is SearchSegment.DateBound -> date
            is SearchSegment.Label -> label
            is SearchSegment.Scope -> scope
            is SearchSegment.Group -> group
            is SearchSegment.Kind -> kind
            // `lang:` and `domain:` are both NIP-50 extensions the relay answers, so they read
            // as one family rather than two colours a reader would have to learn apart.
            is SearchSegment.Language -> extension
            is SearchSegment.Domain -> extension
            is SearchSegment.Exclusion -> exclusion
            is SearchSegment.Phrase -> phrase
        }
}

/** The token palette for the current theme. Tints are drawn from the scheme, never hard-coded. */
@Composable
fun rememberSearchTokenStyles(): SearchTokenStyles {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        fun chip(
            on: Color,
            container: Color,
            weight: FontWeight = FontWeight.Medium,
        ) = SpanStyle(color = on, background = container.copy(alpha = 0.35f), fontWeight = weight)

        SearchTokenStyles(
            person = chip(scheme.primary, scheme.primaryContainer, FontWeight.SemiBold),
            pointer = chip(scheme.tertiary, scheme.tertiaryContainer),
            hashtag = chip(scheme.primary, scheme.primaryContainer),
            date = chip(scheme.secondary, scheme.secondaryContainer),
            label = chip(scheme.tertiary, scheme.tertiaryContainer),
            scope = chip(scheme.tertiary, scheme.tertiaryContainer),
            group = chip(scheme.secondary, scheme.secondaryContainer),
            kind = chip(scheme.secondary, scheme.secondaryContainer, FontWeight.SemiBold),
            extension = chip(scheme.tertiary, scheme.tertiaryContainer),
            // An exclusion removes results rather than narrowing to them, which is the one
            // token whose effect a reader can misread as its opposite — so it is the one drawn
            // in the error colour, struck through, instead of a tint like the rest.
            exclusion =
                chip(scheme.error, scheme.errorContainer)
                    .copy(textDecoration = TextDecoration.LineThrough),
            phrase = chip(scheme.onSurface, scheme.surfaceVariant),
        )
    }
}

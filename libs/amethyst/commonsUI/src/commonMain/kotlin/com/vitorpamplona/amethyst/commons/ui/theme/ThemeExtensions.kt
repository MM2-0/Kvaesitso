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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Highlighter-pen wash painted behind NIP-84 highlighted text.
 *
 * Light themes get the classic near-opaque yellow marker: dark glyphs read straight
 * through it, exactly like a pen on paper. Dark themes cannot do that — a bright
 * yellow slab behind light text is unreadable, and inverting to dark glyphs makes the
 * quote fight the card it sits on — so they get a translucent amber that glows behind
 * the text instead of covering it, paired with [highlightMarkerText].
 *
 * Deliberately NOT derived from the user's accent color: a highlighter reads as yellow
 * regardless of theme, and re-tinting it to (say) a teal accent loses the metaphor.
 */
val ColorScheme.highlightMarker: Color
    get() = if (isLight) Color(0xFFFFE066).copy(alpha = 0.88f) else Color(0xFFFFD24A).copy(alpha = 0.33f)

/** Glyph color for text sitting on the [highlightMarker] wash. */
val ColorScheme.highlightMarkerText: Color
    get() = if (isLight) Color(0xFF1A1400) else Color(0xFFFFEFC2)

/**
 * The un-highlighted text surrounding a NIP-84 highlight. Dimmed so the marked span
 * carries the eye without needing bold, which is what the quote used to lean on.
 */
val ColorScheme.highlightContextText: Color
    get() = onSurface.copy(alpha = if (isLight) 0.55f else 0.50f)

/** Left rule marking the block as quoted from somewhere else. */
val ColorScheme.highlightQuoteBar: Color
    get() = onSurface.copy(alpha = if (isLight) 0.16f else 0.22f)

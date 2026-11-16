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
package com.vitorpamplona.amethyst.commons.ui.spellcheck

import androidx.compose.runtime.Immutable

/** A span of the stored text that an output transformation draws as [visual] instead. */
@Immutable
data class TextReplacement(
    val rawStart: Int,
    val rawEnd: Int,
    val visual: String,
)

/**
 * Implemented by an `OutputTransformation` that swaps parts of the text for something else (a
 * mention's `npub` for the person's name), so the spell checker, which reads the stored text, can
 * find where a word is drawn.
 */
interface VisualOffsetMapping {
    /** Every replaced span of [rawText], in order. */
    fun replacements(rawText: String): List<TextReplacement>
}

/** Where [rawOffset] lands in the drawn text, or null when it falls inside a replaced span. */
internal fun List<TextReplacement>.toVisual(rawOffset: Int): Int? {
    var shift = 0
    for (replacement in this) {
        when {
            replacement.rawEnd <= rawOffset -> shift += replacement.visual.length - (replacement.rawEnd - replacement.rawStart)
            replacement.rawStart < rawOffset -> return null
            else -> break
        }
    }
    return rawOffset + shift
}

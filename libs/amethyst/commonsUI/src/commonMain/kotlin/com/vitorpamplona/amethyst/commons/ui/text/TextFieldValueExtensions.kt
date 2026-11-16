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
package com.vitorpamplona.amethyst.commons.ui.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

fun TextFieldValue.insertUrlAtCursor(url: String): TextFieldValue {
    var toInsert = url.trim()
    if (selection.start > 0 && text[selection.start - 1] != ' ' && text[selection.start - 1] != '\n') {
        toInsert = " $toInsert"
    }

    // takes the position before adding an empty char after the url
    val endOfUrlIndex = selection.start + toInsert.length

    if (selection.end < text.length && text[selection.end] != ' ' && text[selection.end] != '\n') {
        toInsert = "$toInsert "
    }

    return TextFieldValue(
        text.replaceRange(selection.start, selection.end, toInsert),
        TextRange(endOfUrlIndex, endOfUrlIndex),
    )
}

fun TextFieldValue.replaceCurrentWord(wordToInsert: String): TextFieldValue {
    val lastWordStart = currentWordStartIdx()
    val lastWordEnd = currentWordEndIdx()
    val cursor = lastWordStart + wordToInsert.length
    return TextFieldValue(
        text.replaceRange(lastWordStart, lastWordEnd, wordToInsert),
        TextRange(cursor, cursor),
    )
}

fun TextFieldValue.currentWordStartIdx(): Int {
    val previousNewLine = text.lastIndexOf('\n', selection.start - 1)
    val previousSpace = text.lastIndexOf(' ', selection.start - 1)

    return max(
        previousNewLine,
        previousSpace,
    ) + 1
}

fun TextFieldValue.currentWordEndIdx(): Int {
    val nextNewLine = text.indexOf('\n', selection.end)
    val nextSpace = text.indexOf(' ', selection.end)

    if (nextSpace < 0 && nextNewLine < 0) return selection.end
    if (nextSpace > 0 && nextNewLine > 0) {
        return min(
            nextNewLine,
            nextSpace,
        )
    }
    if (nextSpace > 0) {
        return nextSpace
    }
    return nextNewLine
}

fun TextFieldValue.currentWord(): String {
    if (selection.end != selection.start) return ""

    val start = currentWordStartIdx()
    val end = currentWordEndIdx()

    return if (start < end) {
        val word = text.substring(start, end)
        word
    } else {
        ""
    }
}

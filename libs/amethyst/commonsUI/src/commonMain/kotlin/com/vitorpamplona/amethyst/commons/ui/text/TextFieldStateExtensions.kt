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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.math.max
import kotlin.math.min

fun TextFieldState.setTextAndPlaceCursorAtBeginning(text: String) {
    edit {
        replace(0, length, text)
        selection = TextRange(0, 0)
    }
}

/**
 * Appends [signature] to the end of the field, separated by a blank line, keeping the
 * cursor where it was so the user starts typing above it. No-op when the signature is
 * already present, so re-running a screen's load logic never duplicates it.
 */
fun TextFieldState.appendSignature(signature: String) {
    if (text.contains(signature)) return
    edit {
        val cursor = selection
        append("\n\n")
        append(signature)
        selection = cursor
    }
}

fun TextFieldState.insertUrlAtCursor(url: String) {
    edit {
        var toInsert = url.trim()
        val selStart = selection.start
        val selEnd = selection.end
        val text = asCharSequence()

        if (selStart > 0 && text[selStart - 1] != ' ' && text[selStart - 1] != '\n') {
            toInsert = " $toInsert"
        }

        // takes the position before adding an empty char after the url
        val endOfUrlIndex = selStart + toInsert.length

        if (selEnd < length && text[selEnd] != ' ' && text[selEnd] != '\n') {
            toInsert = "$toInsert "
        }

        replace(selStart, selEnd, toInsert)
        selection = TextRange(endOfUrlIndex, endOfUrlIndex)
    }
}

/**
 * Inserts [word] at the cursor, replacing any selection, as a word of its own: a space before it
 * when it would touch the previous word, a space after it, and the cursor past that space.
 */
fun TextFieldState.insertWordAtCursor(word: String) {
    edit {
        val text = asCharSequence()
        val start = selection.start
        val end = selection.end
        val before = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val hasSpaceAfter = end < length && text[end] == ' '
        val toInsert = before + word + if (hasSpaceAfter) "" else " "
        replace(start, end, toInsert)
        val cursor = start + toInsert.length + if (hasSpaceAfter) 1 else 0
        selection = TextRange(cursor, cursor)
    }
}

/** Replaces the selection with [text], as typing it would, leaving the cursor after it. */
fun TextFieldState.insertAtCursor(text: String) {
    edit {
        val start = selection.start
        replace(start, selection.end, text)
        selection = TextRange(start + text.length)
    }
}

fun TextFieldState.replaceCurrentWord(wordToInsert: String) {
    edit {
        val text = asCharSequence()
        val selStart = selection.start
        val selEnd = selection.end
        val lastWordStart = currentWordStartIdx(text, selStart)
        val lastWordEnd = currentWordEndIdx(text, selEnd, length)
        val cursor = lastWordStart + wordToInsert.length
        replace(lastWordStart, lastWordEnd, wordToInsert)
        selection = TextRange(cursor, cursor)
    }
}

fun TextFieldState.currentWord(): String {
    val text = this.text
    val selection = this.selection
    if (selection.end != selection.start) return ""

    val start = currentWordStartIdx(text, selection.start)
    val end = currentWordEndIdx(text, selection.end, text.length)

    return if (start < end) {
        text.subSequence(start, end).toString()
    } else {
        ""
    }
}

private fun currentWordStartIdx(
    text: CharSequence,
    selectionStart: Int,
): Int {
    val previousNewLine = text.lastIndexOf('\n', selectionStart - 1)
    val previousSpace = text.lastIndexOf(' ', selectionStart - 1)

    return max(
        previousNewLine,
        previousSpace,
    ) + 1
}

private fun currentWordEndIdx(
    text: CharSequence,
    selectionEnd: Int,
    textLength: Int,
): Int {
    val nextNewLine = text.indexOf('\n', selectionEnd)
    val nextSpace = text.indexOf(' ', selectionEnd)

    if (nextSpace < 0 && nextNewLine < 0) return selectionEnd
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

private fun CharSequence.lastIndexOf(
    char: Char,
    startIndex: Int,
): Int {
    for (i in startIndex downTo 0) {
        if (this[i] == char) return i
    }
    return -1
}

private fun CharSequence.indexOf(
    char: Char,
    startIndex: Int,
): Int {
    for (i in startIndex until length) {
        if (this[i] == char) return i
    }
    return -1
}

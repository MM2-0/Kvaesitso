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
package com.vitorpamplona.amethyst.commons.nip23LongContent.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * State holder for a markdown editor with selection-aware formatting.
 *
 * Solves the focus/selection bug: toolbar buttons steal focus from TextField,
 * collapsing the selection. We cache the last known selection on every value
 * change, and toolbar operations use the cached selection.
 */
class MarkdownEditorState(
    initial: String = "",
) {
    var value by mutableStateOf(TextFieldValue(initial))
        private set

    /** Cached selection — updated on every onValueChange, survives focus loss. */
    var lastSelection: TextRange = TextRange.Zero
        private set

    fun onValueChange(newValue: TextFieldValue) {
        value = newValue
        // Only cache non-zero selections (focus loss sends collapsed range)
        if (newValue.selection.length > 0 || lastSelection == TextRange.Zero) {
            lastSelection = newValue.selection
        }
        // Also cache cursor position when no selection
        if (newValue.selection.collapsed) {
            lastSelection = newValue.selection
        }
    }

    fun loadContent(content: String) {
        value = TextFieldValue(content)
        lastSelection = TextRange.Zero
    }

    val text: String get() = value.text

    // --- Active state detection (uses current value.selection for display) ---

    val isBold: Boolean
        get() = isWrapped("**", "**")

    val isItalic: Boolean
        get() = isWrappedItalic()

    val isStrikethrough: Boolean
        get() = isWrapped("~~", "~~")

    val isInlineCode: Boolean
        get() = isWrapped("`", "`")

    val isBlockquote: Boolean
        get() = isLinePrefix("> ")

    val isUnorderedList: Boolean
        get() = isLinePrefix("- ")

    val isOrderedList: Boolean
        get() {
            val lineStart = text.lastIndexOf('\n', value.selection.min - 1) + 1
            val line = text.substring(lineStart)
            return line.matches(Regex("^\\d+\\.\\s.*"))
        }

    val isTaskList: Boolean
        get() = isLinePrefix("- [ ] ") || isLinePrefix("- [x] ")

    val headingLevel: Int?
        get() {
            val lineStart = text.lastIndexOf('\n', value.selection.min - 1) + 1
            val line = text.substring(lineStart)
            return when {
                line.startsWith("### ") -> 3
                line.startsWith("## ") -> 2
                line.startsWith("# ") -> 1
                else -> null
            }
        }

    // --- Formatting operations (use lastSelection to survive focus loss) ---

    fun toggleBold() {
        applyToggleWrap("**", "**")
    }

    fun toggleItalic() {
        applyToggleWrapItalic()
    }

    fun toggleStrikethrough() {
        applyToggleWrap("~~", "~~")
    }

    fun toggleInlineCode() {
        applyToggleWrap("`", "`")
    }

    fun setHeading(level: Int?) {
        val sel = lastSelection
        val lineStart = text.lastIndexOf('\n', sel.min - 1) + 1
        val line = text.substring(lineStart)

        // Remove existing heading prefix
        val stripped =
            when {
                line.startsWith("### ") -> line.removePrefix("### ")
                line.startsWith("## ") -> line.removePrefix("## ")
                line.startsWith("# ") -> line.removePrefix("# ")
                else -> line
            }
        val oldPrefixLen =
            when {
                line.startsWith("### ") -> 4
                line.startsWith("## ") -> 3
                line.startsWith("# ") -> 2
                else -> 0
            }

        val newPrefix =
            when (level) {
                1 -> "# "
                2 -> "## "
                3 -> "### "
                else -> ""
            }

        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        val newText = text.substring(0, lineStart) + newPrefix + stripped + text.substring(lineEnd)
        val shift = newPrefix.length - oldPrefixLen

        value =
            TextFieldValue(
                text = newText,
                selection = TextRange((sel.min + shift).coerceAtLeast(lineStart), (sel.max + shift).coerceAtLeast(lineStart)),
            )
        lastSelection = value.selection
    }

    fun toggleBlockquote() {
        applyToggleLinePrefix("> ")
    }

    fun toggleUnorderedList() {
        applyToggleLinePrefix("- ")
    }

    fun toggleOrderedList() {
        val sel = lastSelection
        val lineStart = text.lastIndexOf('\n', sel.min - 1) + 1
        val line = text.substring(lineStart)

        if (line.matches(Regex("^\\d+\\.\\s.*"))) {
            // Remove ordered list prefix
            val prefixEnd = line.indexOf(". ") + 2
            val newText = text.substring(0, lineStart) + line.substring(prefixEnd) + text.substring(lineStart + line.indexOf('\n').let { if (it == -1) line.length else it })
            value =
                TextFieldValue(
                    text = text.substring(0, lineStart) + line.substring(prefixEnd),
                    selection = TextRange((sel.min - prefixEnd).coerceAtLeast(lineStart)),
                )
        } else {
            applyToggleLinePrefix("1. ")
        }
        lastSelection = value.selection
    }

    fun toggleTaskList() {
        val sel = lastSelection
        val lineStart = text.lastIndexOf('\n', sel.min - 1) + 1

        when {
            text.startsWith("- [ ] ", lineStart) -> {
                // Remove task list prefix
                val newText = text.substring(0, lineStart) + text.substring(lineStart + 6)
                val shift = 6
                value = TextFieldValue(text = newText, selection = TextRange((sel.min - shift).coerceAtLeast(lineStart)))
                lastSelection = value.selection
            }

            text.startsWith("- [x] ", lineStart) -> {
                val newText = text.substring(0, lineStart) + text.substring(lineStart + 6)
                val shift = 6
                value = TextFieldValue(text = newText, selection = TextRange((sel.min - shift).coerceAtLeast(lineStart)))
                lastSelection = value.selection
            }

            else -> {
                applyToggleLinePrefix("- [ ] ")
            }
        }
    }

    fun toggleCodeBlock() {
        applyToggleWrap("```\n", "\n```")
    }

    fun insertHorizontalRule() {
        val sel = lastSelection
        val insert = "\n---\n"
        val newText = text.substring(0, sel.min) + insert + text.substring(sel.max)
        value = TextFieldValue(text = newText, selection = TextRange(sel.min + insert.length))
        lastSelection = value.selection
    }

    fun insertLink() {
        val sel = lastSelection
        val selected = text.substring(sel.min, sel.max)

        if (selected.isNotEmpty()) {
            val newText = text.substring(0, sel.min) + "[$selected](url)" + text.substring(sel.max)
            val urlStart = sel.min + selected.length + 3
            value = TextFieldValue(text = newText, selection = TextRange(urlStart, urlStart + 3))
        } else {
            val newText = text.substring(0, sel.min) + "[](url)" + text.substring(sel.min)
            value = TextFieldValue(text = newText, selection = TextRange(sel.min + 1))
        }
        lastSelection = value.selection
    }

    fun insertImage() {
        val sel = lastSelection
        val selected = text.substring(sel.min, sel.max)

        if (selected.isNotEmpty()) {
            val newText = text.substring(0, sel.min) + "![$selected](url)" + text.substring(sel.max)
            val urlStart = sel.min + selected.length + 4
            value = TextFieldValue(text = newText, selection = TextRange(urlStart, urlStart + 3))
        } else {
            val newText = text.substring(0, sel.min) + "![alt](url)" + text.substring(sel.min)
            val urlStart = sel.min + 7
            value = TextFieldValue(text = newText, selection = TextRange(urlStart, urlStart + 3))
        }
        lastSelection = value.selection
    }

    // --- Private helpers ---

    private fun isWrapped(
        prefix: String,
        suffix: String,
    ): Boolean {
        val sel = value.selection
        val start = sel.min
        val end = sel.max
        return start >= prefix.length &&
            end + suffix.length <= text.length &&
            text.substring(start - prefix.length, start) == prefix &&
            text.substring(end, end + suffix.length) == suffix
    }

    private fun isWrappedItalic(): Boolean {
        val sel = value.selection
        val start = sel.min
        val end = sel.max
        if (start < 1 || end + 1 > text.length) return false
        if (text[start - 1] != '*' || text[end] != '*') return false
        val hasBoldBefore = start >= 2 && text[start - 2] == '*'
        val hasBoldAfter = end + 1 < text.length && text[end + 1] == '*'
        return !hasBoldBefore && !hasBoldAfter
    }

    private fun isLinePrefix(prefix: String): Boolean {
        val lineStart = text.lastIndexOf('\n', value.selection.min - 1) + 1
        return text.startsWith(prefix, lineStart)
    }

    private fun applyToggleWrap(
        prefix: String,
        suffix: String,
    ) {
        val sel = lastSelection
        val start = sel.min
        val end = sel.max

        val wrapped =
            start >= prefix.length &&
                end + suffix.length <= text.length &&
                text.substring(start - prefix.length, start) == prefix &&
                text.substring(end, end + suffix.length) == suffix

        value =
            if (wrapped) {
                val newText =
                    text.substring(0, start - prefix.length) +
                        text.substring(start, end) +
                        text.substring(end + suffix.length)
                TextFieldValue(newText, TextRange(start - prefix.length, end - prefix.length))
            } else if (start == end) {
                val newText = text.substring(0, start) + prefix + suffix + text.substring(start)
                TextFieldValue(newText, TextRange(start + prefix.length))
            } else {
                val newText = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end)
                TextFieldValue(newText, TextRange(start + prefix.length, end + prefix.length))
            }
        lastSelection = value.selection
    }

    private fun applyToggleWrapItalic() {
        val sel = lastSelection
        val start = sel.min
        val end = sel.max

        val isItalic =
            start >= 1 &&
                end + 1 <= text.length &&
                text[start - 1] == '*' &&
                text[end] == '*' &&
                !(start >= 2 && text[start - 2] == '*') &&
                !(end + 1 < text.length && text[end + 1] == '*')

        if (isItalic) {
            val newText = text.substring(0, start - 1) + text.substring(start, end) + text.substring(end + 1)
            value = TextFieldValue(newText, TextRange(start - 1, end - 1))
        } else {
            applyToggleWrap("*", "*")
            return
        }
        lastSelection = value.selection
    }

    private fun applyToggleLinePrefix(prefix: String) {
        val sel = lastSelection
        val lineStart = text.lastIndexOf('\n', sel.min - 1) + 1

        value =
            if (text.startsWith(prefix, lineStart)) {
                val newText = text.substring(0, lineStart) + text.substring(lineStart + prefix.length)
                val shift = prefix.length
                TextFieldValue(newText, TextRange((sel.min - shift).coerceAtLeast(lineStart), (sel.max - shift).coerceAtLeast(lineStart)))
            } else {
                val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
                TextFieldValue(newText, TextRange(sel.min + prefix.length, sel.max + prefix.length))
            }
        lastSelection = value.selection
    }
}

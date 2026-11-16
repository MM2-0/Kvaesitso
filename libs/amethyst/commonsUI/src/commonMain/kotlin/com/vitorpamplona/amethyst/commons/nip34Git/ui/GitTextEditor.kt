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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_edit_block_done
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/**
 * A file being edited as lines, with one block of them in a text field at a time: the cost of a
 * keystroke is the block's, not the file's (a whole file in one field cost ~150 ms a keystroke at
 * 256 KB on desktop). Lines are read as they are and written back the same way: a CRLF file stays
 * CRLF, and a missing final newline stays missing.
 */
@Stable
class GitTextEditorState(
    private val original: String,
) {
    private val crlf = original.contains("\r\n")
    private val lineBreak = if (crlf) "\r\n" else "\n"

    /** The file's lines, without their line breaks; a final newline is a last empty line. */
    val lines = mutableStateListOf<String>().apply { addAll(original.split('\n').map { if (crlf) it.removeSuffix("\r") else it }) }

    /** The lines in the field now, or null when none is being edited. */
    var block by mutableStateOf<IntRange?>(null)
        private set

    var blockField by mutableStateOf<TextFieldState?>(null)
        private set

    private var fieldOriginal = ""

    /** True once any block was changed (an edit reverted by hand still counts; [text] tells). */
    var edited by mutableStateOf(false)
        private set

    val isChanged: Boolean get() = edited || (blockField?.text?.toString()?.let { it != fieldOriginal } ?: false)

    /** True from [open] until the field asked for focus: once per block, not each time it scrolls into view. */
    var wantsFocus by mutableStateOf(false)
        internal set

    /**
     * Opens the lines around [line] (an index as the lines are drawn now, the open block's own
     * lines counted as they were) for editing, keeping what the open block had.
     */
    fun open(line: Int) {
        val previous = block
        val delta = commit()
        // A line drawn below the block moved by however many lines the block gained or lost.
        val target = if (previous != null && line > previous.last) line + delta else line
        val first = (target - BLOCK_BEFORE).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val last = (target + BLOCK_AFTER).coerceIn(first, lines.lastIndex.coerceAtLeast(0))
        val text = lines.subList(first, last + 1).joinToString("\n")
        fieldOriginal = text
        block = first..last
        blockField = TextFieldState(text)
        wantsFocus = true
    }

    /** Puts the open block back into the file's lines; returns how many lines it gained (negative: lost). */
    fun commit(): Int {
        val range = block ?: return 0
        val text = blockField?.text?.toString() ?: fieldOriginal
        var delta = 0
        if (text != fieldOriginal) {
            val replacement = text.split('\n').map { if (crlf) it.removeSuffix("\r") else it }
            for (i in range.last downTo range.first) lines.removeAt(i)
            lines.addAll(range.first, replacement)
            delta = replacement.size - (range.last - range.first + 1)
            edited = true
        }
        block = null
        blockField = null
        return delta
    }

    /** The whole file as it is now, the open block included. */
    fun text(): String {
        commit()
        return lines.joinToString(lineBreak)
    }

    companion object {
        const val BLOCK_BEFORE = 5
        const val BLOCK_AFTER = 15
    }
}

/** [state]'s lines, one tap away from a field: the tapped line and those around it. */
@Composable
fun GitTextEditor(
    state: GitTextEditorState,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val gutter =
        (
            state.lines.size
                .toString()
                .length * 8 + 8
        ).dp
    val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    val block = state.block
    LazyColumn(modifier, state = listState) {
        val before = block?.first ?: state.lines.size
        items(before, key = { "line-$it" }) { index -> EditorLine(index, state.lines[index], gutter, codeStyle, readOnly) { state.open(index) } }
        if (block != null) {
            item(key = "block-${block.first}") {
                val field = state.blockField ?: return@item
                val focus = remember(block.first) { FocusRequester() }
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))) {
                    Row {
                        Text(
                            "${block.first + 1}",
                            style = codeStyle.copy(color = MaterialTheme.colorScheme.grayText),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(gutter).padding(end = 6.dp),
                        )
                        BasicTextField(
                            state = field,
                            readOnly = readOnly,
                            textStyle = codeStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { state.commit() }, enabled = !readOnly) { Text(stringRes(Res.string.git_edit_block_done)) }
                    }
                }
                LaunchedEffect(block.first) {
                    if (state.wantsFocus) {
                        state.wantsFocus = false
                        runCatching { focus.requestFocus() }
                    }
                }
            }
            val after = block.last + 1
            items(state.lines.size - after, key = { "line-${after + it}" }) { offset ->
                val index = after + offset
                EditorLine(index, state.lines[index], gutter, codeStyle, readOnly) { state.open(index) }
            }
        }
    }
}

@Composable
private fun EditorLine(
    index: Int,
    text: String,
    gutter: Dp,
    style: TextStyle,
    readOnly: Boolean,
    onOpen: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !readOnly, onClick = onOpen)) {
        Text(
            "${index + 1}",
            style = style.copy(color = MaterialTheme.colorScheme.grayText),
            textAlign = TextAlign.End,
            modifier = Modifier.width(gutter).padding(end = 6.dp),
        )
        Text(text, style = style)
    }
}

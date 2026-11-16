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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.edited
import com.vitorpamplona.amethyst.commons.resources.original
import com.vitorpamplona.amethyst.commons.ui.note.QuietMark
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Pencil marking an edited note; tapping cycles through the versions. The
 * bare pencil is the latest edit — a suffix appears only while browsing
 * older versions.
 */
@Composable
fun DisplayEditStatus(editState: EditState) {
    val label =
        if (editState.showingVersion.value == editState.lastVersionId()) {
            null
        } else if (editState.showingVersion.value == editState.originalVersionId()) {
            stringRes(id = Res.string.original)
        } else {
            "#${editState.versionId()}"
        }

    QuietMark(
        symbol = MaterialSymbols.Edit,
        text = label,
        contentDescription = stringRes(id = Res.string.edited),
        onClick = { editState.nextModification() },
    )
}

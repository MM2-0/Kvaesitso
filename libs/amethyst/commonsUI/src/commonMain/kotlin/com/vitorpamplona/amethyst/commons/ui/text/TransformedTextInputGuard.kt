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

import androidx.compose.runtime.Composable

/**
 * Wraps a text field whose [androidx.compose.foundation.text.input.OutputTransformation] changes
 * the text's length (the `@npub1…` → `@Name` mention chips), so the soft keyboard cannot crash it.
 *
 * Compose maps every IME command in a batch through the offset mapping of the text as it was
 * *before* the batch. Its `setSelection` then sets the mapped offset without clamping it, so a
 * batch that first shortens the text (deleting or replacing a mention chip) and then moves the
 * cursor puts the cursor past the end of the text and throws
 * `IllegalArgumentException: Expected TextRange(68, 68) to be in TextRange(0, 18)`. Samsung
 * Keyboard sends exactly that batch. The bug is in foundation's `ImeEditCommand.setSelection`
 * (still present in 1.13.0-beta02).
 *
 * **This is a workaround for that Compose bug, not the intended design.** Using an
 * OutputTransformation that changes the text's length is supported API; the missing clamp is
 * Compose's (its sibling commands `commitText`, `setComposingRegion` and `deleteSurroundingText`
 * all clamp). Remove this guard and its call sites once Compose fixes it:
 * `TransformedTextInputGuardTest.unguardedField_crashesOnTheBatch` (amethyst androidTest) asserts
 * the unguarded field still crashes, so it starts failing on the Compose release that fixes it.
 *
 * On Android this commits the commands queued so far before a `setSelection` (or select-all) that
 * arrives inside a batch, so the selection is mapped through the text it lands in. The cost: the
 * keyboard sees the batch's earlier edits applied before the batch ends, where stock Compose
 * applies the whole batch at once. On desktop and iOS it renders [content] as is; their IME paths
 * were not checked for the same bug.
 *
 * @param active false renders [content] as is: a field without an OutputTransformation has no
 *   offset mapping to go stale. Keep it constant for a field; flipping it recreates [content].
 */
@Composable
expect fun TransformedTextInputGuard(
    active: Boolean = true,
    content: @Composable () -> Unit,
)

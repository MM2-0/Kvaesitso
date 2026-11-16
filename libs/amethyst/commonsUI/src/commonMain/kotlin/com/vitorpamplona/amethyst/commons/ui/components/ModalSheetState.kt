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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * The [SheetState] for a `ModalBottomSheet`: starts [SheetValue.Hidden] and, with
 * [skipPartiallyExpanded], opens straight to [SheetValue.Expanded].
 *
 * Every sheet goes through here so the deprecated `rememberModalBottomSheetState` is called in
 * exactly one place. Its suggested replacement, `rememberBottomSheetState`, is not equivalent:
 * the deprecated function pins `isBottomSheetPartiallyExpandedDeterministicEnabled` to false,
 * while `rememberBottomSheetState` takes the global flag (default true), which changes where a
 * sheet that keeps [SheetValue.PartiallyExpanded] places and settles that anchor. The flag is
 * only reachable through Material3 internals (`rememberSheetState`, the flag-carrying `Saver`),
 * so the deprecated call stays until Material3 exposes it or the sheets move to the new behavior
 * on purpose.
 */
@ExperimentalMaterial3Api
@Composable
fun rememberModalSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState {
    @Suppress("DEPRECATION")
    return rememberModalBottomSheetState(skipPartiallyExpanded, confirmValueChange)
}

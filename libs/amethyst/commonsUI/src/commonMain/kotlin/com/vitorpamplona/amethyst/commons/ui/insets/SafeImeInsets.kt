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
package com.vitorpamplona.amethyst.commons.ui.insets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The IME inset to lay out against, in place of `WindowInsets.ime`.
 *
 * On Android this corrects a Compose insets listener that can freeze the animated IME inset for the
 * rest of the activity's life (see the Android actual's `SafeImeInsets`). Desktop and iOS have no
 * such defect and return `WindowInsets.ime` as is.
 */
@Composable
expect fun rememberSafeImeInsets(): WindowInsets

/**
 * Drop-in replacement for `Modifier.imePadding()` that survives a stranded IME inset.
 *
 * Prefer this everywhere; `imePadding()` reads the raw animated inset and, on Android, will hold a
 * keyboard-sized gap open for the rest of the activity's life once Compose's insets listener wedges.
 * Consumption semantics are identical — this is `windowInsetsPadding` over the same inset.
 */
@Composable
fun Modifier.imePaddingSafe(): Modifier = windowInsetsPadding(rememberSafeImeInsets())

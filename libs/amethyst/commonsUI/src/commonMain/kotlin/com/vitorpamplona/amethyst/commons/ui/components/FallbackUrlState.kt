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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Walks a primary URL and its NIP-92 `fallback` URLs in order: [url] is the one to load now, and
 * [onError] moves to the next candidate after it fails. Once the last candidate fails, [onError]
 * is a no-op and the caller's own error state stays on screen.
 */
@Stable
class FallbackUrlState(
    private val primary: String?,
    private val fallbacks: List<String>,
) {
    private var index by mutableIntStateOf(0)

    val url: String? get() = if (index == 0) primary else fallbacks.getOrNull(index - 1)

    fun hasNext() = primary != null && index < fallbacks.size

    fun onError() {
        if (hasNext()) index++
    }
}

@Composable
fun rememberFallbackUrlState(
    primary: String?,
    fallbacks: List<String>,
): FallbackUrlState = remember(primary, fallbacks) { FallbackUrlState(primary, fallbacks) }

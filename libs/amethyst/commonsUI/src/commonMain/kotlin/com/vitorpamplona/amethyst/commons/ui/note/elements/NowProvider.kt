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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

private const val TICK_INTERVAL_MS = 30_000L

// Shared coarse-grained "now" ticker. One coroutine refreshes the value at TICK_INTERVAL_MS,
// every TimeAgo on screen reads from it. Because TimeAgo wraps the formatted string in
// `derivedStateOf`, the Text only recomposes when the displayed string actually changes
// (e.g. crossing 1m → 2m) — not on every tick.
val LocalNowSeconds = compositionLocalOf<State<Long>> { mutableLongStateOf(TimeUtils.now()) }

@Composable
fun NowProvider(content: @Composable () -> Unit) {
    val now =
        produceState(TimeUtils.now()) {
            while (true) {
                delay(TICK_INTERVAL_MS)
                value = TimeUtils.now()
            }
        }
    CompositionLocalProvider(LocalNowSeconds provides now, content = content)
}

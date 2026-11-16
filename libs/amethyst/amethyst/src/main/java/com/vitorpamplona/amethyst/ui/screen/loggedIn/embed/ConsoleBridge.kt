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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine

/**
 * A surface controller that exposes JavaScript console output captured from the embedded WebView.
 * The [consoleLogs] list is Compose snapshot state so the console sheet recomposes as messages arrive.
 * Implemented by [com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.EmbeddedWebAppController].
 */
interface ConsoleBridge {
    val consoleLogs: SnapshotStateList<ConsoleLine>

    /**
     * How many of [consoleLogs] are errors — the pill's badge. Kept as its own state so the tab layer
     * reads a single int instead of counting the list, which subscribed it to EVERY log line: a page
     * logging each frame recomposed the whole layer each frame.
     */
    val consoleErrorCount: IntState

    fun clearConsoleLogs()
}

/**
 * A capped JavaScript console buffer that keeps its error count as separate state (see
 * [ConsoleBridge.consoleErrorCount]). Main-thread only.
 */
class ConsoleBuffer(
    private val max: Int,
) {
    val lines = mutableStateListOf<ConsoleLine>()
    private val errors = mutableIntStateOf(0)
    val errorCount: IntState get() = errors

    fun add(line: ConsoleLine) {
        if (lines.size >= max && lines.removeAt(0).level == ConsoleLine.Level.ERROR) errors.intValue--
        lines.add(line)
        if (line.level == ConsoleLine.Level.ERROR) errors.intValue++
    }

    fun clear() {
        lines.clear()
        errors.intValue = 0
    }
}

/** Maps a provider's console level (WebView's `ConsoleMessage.MessageLevel` name) onto the chrome's. */
fun consoleLevelOf(level: String): ConsoleLine.Level =
    when (level) {
        "ERROR" -> ConsoleLine.Level.ERROR
        "WARNING" -> ConsoleLine.Level.WARNING
        "DEBUG" -> ConsoleLine.Level.DEBUG
        "TIP" -> ConsoleLine.Level.INFO
        else -> ConsoleLine.Level.LOG
    }

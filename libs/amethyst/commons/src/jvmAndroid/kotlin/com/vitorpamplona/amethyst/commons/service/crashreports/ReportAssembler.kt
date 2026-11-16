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
package com.vitorpamplona.amethyst.commons.service.crashreports

/**
 * Longest headline (`ClassName: message`) a report will carry for the throwable or its cause.
 *
 * An exception message is usually one line, but it doesn't have to be: a failed navigation quotes
 * the entire route it could not match, and that route may itself hold a whole previous report.
 * Without a cap each crash report would embed the last one, tripling in size every round (the route
 * re-encodes `%` as `%25`) until the report is too big to send — which is a crash of its own. The
 * stack trace below the headline is what makes a report useful, and it stays whole.
 */
private const val MAX_HEADLINE_LENGTH = 1_000

private fun Throwable.headline(): String {
    val full = toString()
    if (full.length <= MAX_HEADLINE_LENGTH) return full
    // Never cut between the halves of a surrogate pair.
    val cut = if (Character.isHighSurrogate(full[MAX_HEADLINE_LENGTH - 1])) MAX_HEADLINE_LENGTH - 1 else MAX_HEADLINE_LENGTH
    return full.take(cut) + "… (${full.length} chars)"
}

/**
 * A crash report for a developer to read: the exception, the build, and the device it ran on.
 * [versionLine] names the build ("1.17.0-PLAY"); [deviceRows] describe the device (Android's
 * manufacturer and model, the desktop's OS and Java), each a row of the report's table.
 */
class ReportAssembler(
    private val versionLine: String = "",
    private val deviceRows: List<Pair<String, String>> = emptyList(),
) {
    fun buildReport(
        e: Throwable,
        threadName: String = Thread.currentThread().name,
    ): String =
        buildString {
            // Fully qualified, NOT simpleName. Release builds are obfuscated, so this
            // headline would otherwise read "a: 1.16.0-PLAY" — and a bare simple name
            // carries no package for `retrace` to resolve it against, so it would stay
            // unreadable even after the rest of the report retraced cleanly. The FQN
            // retraces back to the real exception class. See scripts/retrace.sh.
            append(e.javaClass.name)
            append(": ")
            appendLine(versionLine)
            appendLine()

            appendLine("| Prop | Value |")
            appendLine("|------|-------|")
            (deviceRows + ("Thread" to threadName)).forEach { (name, value) ->
                append("| ")
                append(name)
                append(" | ")
                append(value)
                appendLine(" |")
            }
            appendLine()

            appendLine("```")
            append("Thread: ")
            appendLine(threadName)
            appendLine(e.headline())
            // "    at <frame>", not just "    <frame>": `at` is what every stack-trace
            // parser keys on, R8's `retrace` included. Without it a release report is
            // passed through untouched and stays obfuscated. See scripts/retrace.sh.
            e.stackTrace.forEach {
                append("    at ")
                appendLine(it.toString())
            }
            val cause = e.cause
            if (cause != null) {
                appendLine("\n\nCause:")
                append("    ")
                appendLine(cause.headline())
                cause.stackTrace.forEach {
                    append("        at ")
                    appendLine(it.toString())
                }
            }
            appendLine("```")
        }
}

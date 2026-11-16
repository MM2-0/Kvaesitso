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
 * Keeps a report of a crash for the next start, then lets the crash go on as it would have.
 * Written synchronously: the process is about to die, and a coroutine launched now may never run.
 */
class UnexpectedCrashSaver(
    val cache: CrashReportCache,
    val assembler: ReportAssembler,
) : Thread.UncaughtExceptionHandler {
    private val defaultUEH: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(
        t: Thread,
        e: Throwable,
    ) {
        // OOM reports are junk
        if (e !is OutOfMemoryError) {
            runCatching { cache.writeReport(assembler.buildReport(e, t.name)) }
        }
        val previous = defaultUEH
        if (previous != null) {
            previous.uncaughtException(t, e)
        } else {
            // Without a default handler (a desktop JVM) the JVM would have printed this.
            System.err.print("Exception in thread \"${t.name}\" ")
            e.printStackTrace()
        }
    }
}

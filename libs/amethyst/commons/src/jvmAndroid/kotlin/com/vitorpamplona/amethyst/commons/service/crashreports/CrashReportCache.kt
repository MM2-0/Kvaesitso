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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val STACK_TRACE_FILENAME = "stack.trace"

/**
 * The last crash's report, kept in [dir] until the next start hands it to the user. Android passes
 * its files dir, where this file has always lived (`openFileOutput` wrote it there).
 */
class CrashReportCache(
    private val dir: File,
) {
    private val file get() = File(dir, STACK_TRACE_FILENAME)

    /**
     * Replaces the kept report. It goes to a temp file first and is renamed into place, so two
     * threads failing at once, or a write cut short by the dying process, never leave a garbled
     * report. Only the owner can read it: a stack trace can quote note or relay content.
     */
    @Synchronized
    fun writeReport(report: String) {
        val temp = File(dir, "$STACK_TRACE_FILENAME.tmp")
        temp.writeText(report)
        temp.setReadable(false, false)
        temp.setReadable(true, true)
        temp.setWritable(false, false)
        temp.setWritable(true, true)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    suspend fun loadAndDelete(): String? =
        withContext(Dispatchers.IO) {
            val stack = file.takeIf { it.exists() }?.readText()
            file.delete()
            stack
        }
}

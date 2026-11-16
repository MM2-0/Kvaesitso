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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.quartz.utils.Log
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

/**
 * Platform-specific debug flag.
 * Android: checks BuildConfig.DEBUG
 * Desktop: can be configured via system property
 */
expect val isDebug: Boolean

inline fun <T> logTime(
    debugMessage: String,
    minToReportMs: Int = 1,
    block: () -> T,
): T =
    if (isDebug) {
        val (result, elapsed) = measureTimedValue(block)
        if (elapsed.inWholeMilliseconds > minToReportMs) {
            Log.d("DEBUG-TIME") { "${elapsed.toString(DurationUnit.MILLISECONDS, 3).padStart(12)}: $debugMessage" }
        }
        result
    } else {
        block()
    }

inline fun <T> logTime(
    debugMessage: (T) -> String,
    minToReportMs: Int = 1,
    block: () -> T,
): T =
    if (isDebug) {
        val (result, elapsed) = measureTimedValue(block)
        if (elapsed.inWholeMilliseconds > minToReportMs) {
            Log.d("DEBUG-TIME") { "${elapsed.toString(DurationUnit.MILLISECONDS, 3).padStart(12)}: ${debugMessage(result)}" }
        }
        result
    } else {
        block()
    }

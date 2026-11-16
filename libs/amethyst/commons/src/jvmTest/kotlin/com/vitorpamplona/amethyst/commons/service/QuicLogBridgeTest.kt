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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import com.vitorpamplona.quartz.utils.LogSink
import com.vitorpamplona.quic.QuicLog
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class QuicLogBridgeTest {
    private val originalSink = Log.sink
    private val originalMinLevel = Log.minLevel
    private val originalQuicSink = QuicLog.sink

    @AfterTest
    fun restore() {
        Log.sink = originalSink
        Log.minLevel = originalMinLevel
        QuicLog.sink = originalQuicSink
    }

    @Test
    fun quicLinesReachQuartzLogAtTheirLevel() {
        val lines = mutableListOf<String>()
        Log.sink = LogSink { level, tag, message, _ -> lines += "$level $tag $message" }
        Log.minLevel = LogLevel.DEBUG
        QuicLogBridge.install()

        QuicLog.d("Quic") { "one" }
        QuicLog.i("Quic") { "two" }
        QuicLog.w("Quic") { "three" }
        QuicLog.e("Quic") { "four" }

        assertEquals(listOf("DEBUG Quic one", "INFO Quic two", "WARN Quic three", "ERROR Quic four"), lines)
    }

    @Test
    fun linesBelowMinLevelAreNeverBuilt() {
        val lines = mutableListOf<String>()
        Log.sink = LogSink { level, _, message, _ -> lines += "$level $message" }
        Log.minLevel = LogLevel.WARN
        QuicLogBridge.install()

        var built = false
        QuicLog.d("Quic") {
            built = true
            "debug"
        }
        QuicLog.i("Quic") {
            built = true
            "info"
        }
        QuicLog.w("Quic") { "warn" }

        assertFalse(built)
        assertEquals(listOf("WARN warn"), lines)
    }
}

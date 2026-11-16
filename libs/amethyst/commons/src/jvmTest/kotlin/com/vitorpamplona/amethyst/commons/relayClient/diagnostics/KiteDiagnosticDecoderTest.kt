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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import com.vitorpamplona.quartz.utils.LogSink
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KiteDiagnosticDecoderTest {
    private val lines = mutableListOf<Pair<LogLevel, String>>()
    private val originalSink = Log.sink
    private val originalLevel = Log.minLevel

    private val failingKite = MessageDecoder { throw IllegalStateException("kite says no") }

    @BeforeTest
    fun setUp() {
        Log.minLevel = LogLevel.DEBUG
        Log.sink = LogSink { level, tag, message, _ -> if (tag == KiteDiagnosticDecoder.TAG) lines += level to message }
    }

    @AfterTest
    fun tearDown() {
        Log.sink = originalSink
        Log.minLevel = originalLevel
    }

    @Test
    fun kiteBugIsLoggedWithTheFrameAndKotlinxsMessageIsReturned() {
        val frame = """["EOSE","sub1"]"""
        val msg = KiteDiagnosticDecoder(failingKite).decode(frame)

        assertIs<EoseMessage>(msg)
        assertEquals("sub1", msg.subId)
        assertTrue(lines.all { it.first == LogLevel.WARN })
        assertTrue(lines[0].second.startsWith("Kite bug #1"), lines[0].second)
        assertTrue(lines[0].second.contains("kite says no"))
        assertEquals("Kite bug #1 frame 1/1: $frame", lines[1].second)
    }

    @Test
    fun aFrameBothRejectIsLoggedAsBadAndKitesErrorIsRethrown() {
        val error = assertFailsWith<IllegalStateException> { KiteDiagnosticDecoder(failingKite).decode("""["EOSE",""") }

        assertEquals("kite says no", error.message)
        assertEquals(1, lines.size)
        assertEquals(LogLevel.INFO, lines[0].first)
        assertTrue(lines[0].second.startsWith("Bad frame #1, rejected by Kite and kotlinx"), lines[0].second)
    }

    @Test
    fun aLongFrameIsSplitIntoNumberedParts() {
        val frame = """["EOSE","${"x".repeat(7000)}"]"""
        KiteDiagnosticDecoder(failingKite).decode(frame)

        val parts = lines.drop(1).map { it.second }
        assertEquals(3, parts.size)
        assertTrue(parts[2].startsWith("Kite bug #1 frame 3/3: "))
        assertEquals(frame, parts.joinToString("") { it.substringAfter(": ") })
    }

    @Test
    fun aRepeatedFrameIsLoggedOnce() {
        val decoder = KiteDiagnosticDecoder(failingKite)
        repeat(3) { decoder.decode("""["EOSE","sub1"]""") }
        decoder.decode("""["EOSE","sub2"]""")

        val headers = lines.map { it.second }.filterNot { Regex(" frame \\d+/\\d+: ").containsMatchIn(it) }
        assertEquals(listOf("Kite bug #1", "Kite bug #4"), headers.map { it.substringBefore(":") })
    }

    @Test
    fun theRealDecoderParsesGoodFramesSilently() {
        val msg = KiteDiagnosticDecoder().decode("""["EOSE","sub1"]""")
        assertIs<EoseMessage>(msg)
        assertTrue(lines.isEmpty())
    }
}

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
package com.vitorpamplona.nestsclient.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-string tests for [jsonStr] / [jsonArrStr] (the JSON-quoting
 * helpers used at every trace call site) and a small toggle test for
 * [NestsTrace.setRecording]'s state-machine.
 *
 * The actual `emit` log-output side is untestable in commonTest because
 * `com.vitorpamplona.quartz.utils.Log` writes to logcat / stdout via
 * platform actuals, not via an injectable sink. The schema correctness
 * we DO want to pin — a JSON-syntax bug at one of the call sites would
 * silently corrupt the trace file and break replay tooling — is covered
 * by exercising the quoting helpers exhaustively and asserting on
 * concatenated round-trip equality with hand-built JSON literals.
 */
class NestsTraceTest {
    @Test
    fun jsonStrEscapesQuotesAndBackslashes() {
        assertEquals("\"hello\"", jsonStr("hello"))
        assertEquals("\"with \\\"quotes\\\"\"", jsonStr("with \"quotes\""))
        assertEquals("\"backslash \\\\ here\"", jsonStr("backslash \\ here"))
    }

    @Test
    fun jsonStrEscapesControlCharacters() {
        assertEquals("\"line1\\nline2\"", jsonStr("line1\nline2"))
        assertEquals("\"col1\\tcol2\"", jsonStr("col1\tcol2"))
        assertEquals("\"crlf\\r\\n\"", jsonStr("crlf\r\n"))
    }

    @Test
    fun jsonStrEscapesLowControlCharsAsUnicode() {
        //  (start of heading) — must be  in JSON, not raw.
        val raw = "xy"
        val quoted = jsonStr(raw)
        assertEquals("\"x\\u0001y\"", quoted)
    }

    @Test
    fun jsonStrLeavesPrintableAsciiAlone() {
        // Every printable ASCII char that isn't `"` or `\` must round-trip
        // unmodified — most production trace fields are pubkey hex,
        // track names, event-kind enums.
        val allPrintable =
            (0x20..0x7e)
                .map { it.toChar() }
                .filter { it != '"' && it != '\\' }
                .joinToString("")
        val quoted = jsonStr(allPrintable)
        assertEquals("\"$allPrintable\"", quoted)
    }

    @Test
    fun jsonArrStrEmitsValidJsonArray() {
        assertEquals("[]", jsonArrStr(emptyList()))
        assertEquals("[\"a\"]", jsonArrStr(listOf("a")))
        assertEquals(
            "[\"alpha\",\"beta\",\"gamma\"]",
            jsonArrStr(listOf("alpha", "beta", "gamma")),
        )
    }

    @Test
    fun jsonArrStrEscapesElementsConsistentlyWithJsonStr() {
        // Each element runs through jsonStr — quotes and backslashes
        // inside an element must be escaped just like a stand-alone field.
        assertEquals(
            "[\"a\\\"b\",\"c\\\\d\"]",
            jsonArrStr(listOf("a\"b", "c\\d")),
        )
    }

    @Test
    fun setRecordingIsIdempotent() {
        // Set up clean state for the test — flip off in case a prior
        // test left the recorder enabled. (No reset() API by design;
        // tests share the singleton.)
        NestsTrace.setRecording(false)
        assertFalse(NestsTrace.isRecording())

        NestsTrace.setRecording(true)
        assertTrue(NestsTrace.isRecording())

        // Double-enable: no change in state, no error.
        NestsTrace.setRecording(true)
        assertTrue(NestsTrace.isRecording())

        NestsTrace.setRecording(false)
        assertFalse(NestsTrace.isRecording())

        // Double-disable: no change in state, no error.
        NestsTrace.setRecording(false)
        assertFalse(NestsTrace.isRecording())
    }

    @Test
    fun emitIsNoOpWhenDisabled() {
        // Lambda must not run when tracing is off — call sites pass
        // a non-trivial allocator (string concat) and we promise zero
        // work on the disabled path.
        NestsTrace.setRecording(false)
        var lambdaRanCount = 0
        NestsTrace.emit("would_have_recorded") {
            lambdaRanCount += 1
            ""
        }
        assertEquals(0, lambdaRanCount, "emit's fields lambda must not run when tracing is disabled")
    }

    @Test
    fun emitRunsLambdaWhenEnabled() {
        NestsTrace.setRecording(true)
        try {
            var lambdaRanCount = 0
            NestsTrace.emit("did_record") {
                lambdaRanCount += 1
                "\"k\":\"v\""
            }
            assertEquals(1, lambdaRanCount, "emit's fields lambda must run exactly once when enabled")
        } finally {
            NestsTrace.setRecording(false)
        }
    }
}

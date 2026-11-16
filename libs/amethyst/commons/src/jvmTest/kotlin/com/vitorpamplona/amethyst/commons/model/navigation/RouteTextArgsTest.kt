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
package com.vitorpamplona.amethyst.commons.model.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTextArgsTest {
    @Test
    fun measuresWhatUriEncodeWouldProduce() {
        // Unreserved characters cost one, everything else three per UTF-8 byte.
        assertEquals(3, encodedRouteArgLength("abc"))
        assertEquals(5, encodedRouteArgLength("a-b_c"))
        assertEquals(5, encodedRouteArgLength("a b"))
        assertEquals(3, encodedRouteArgLength("%"))
        assertEquals(3, encodedRouteArgLength("\n"))
        assertEquals(6, encodedRouteArgLength("é"))
        assertEquals(9, encodedRouteArgLength("☃"))
        assertEquals(12, encodedRouteArgLength("😀"))
    }

    @Test
    fun leavesValuesThatFitAlone() {
        val short = "hey, want to grab coffee?"
        assertSame(short, short.limitToRouteTextArg())

        val justUnder = "a".repeat(MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH)
        assertSame(justUnder, justUnder.limitToRouteTextArg())
    }

    @Test
    fun cutsAReportSizedValueDownToTheBudget() {
        // The shape that crashed navigation: a resource-usage report, mostly spaces, pipes and
        // newlines, each of which triples on the way into the route.
        val report = "| relay.connms.mobile.fg = 116944252 |\n".repeat(5_000)
        assertTrue(encodedRouteArgLength(report) > MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH)

        val limited = report.limitToRouteTextArg()

        assertTrue(encodedRouteArgLength(limited) <= MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH)
        assertTrue(limited.endsWith(ROUTE_TEXT_ARG_TRUNCATION_MARKER))
        assertTrue(report.startsWith(limited.removeSuffix(ROUTE_TEXT_ARG_TRUNCATION_MARKER)))
    }

    @Test
    fun cutsAHugeSharedPayloadWithoutScanningAllOfIt() {
        // What another app can hand us through a share: bounded only by Binder. The scan has to
        // stop at the budget, not walk the megabyte.
        val payload = "| a = 1 |\n".repeat(100_000)

        val limited = payload.limitToRouteTextArg()

        assertTrue(encodedRouteArgLength(limited) <= MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH)
        assertTrue(limited.endsWith(ROUTE_TEXT_ARG_TRUNCATION_MARKER))
    }

    @Test
    fun returnsNothingWhenTheBudgetCannotEvenHoldTheMarker() {
        assertEquals("", "a".repeat(100).limitToRouteTextArg(4))
    }

    @Test
    fun staysUnderBudgetForTextThatEncodesWide() {
        val emoji = "😀".repeat(50_000)
        val limited = emoji.limitToRouteTextArg()

        assertTrue(encodedRouteArgLength(limited) <= MAX_ROUTE_TEXT_ARG_ENCODED_LENGTH)
        // A cut in the middle of a surrogate pair would leave an unpaired char behind.
        assertEquals(0, limited.count { it.isSurrogate() } % 2)
        assertTrue(limited.removeSuffix(ROUTE_TEXT_ARG_TRUNCATION_MARKER).all { it.isSurrogate() })
    }

    @Test
    fun honoursASmallerBudget() {
        val text = "abcdefghij".repeat(100)
        val limited = text.limitToRouteTextArg(64)

        assertTrue(encodedRouteArgLength(limited) <= 64)
        assertTrue(limited.startsWith("abcdefghij"))
    }
}

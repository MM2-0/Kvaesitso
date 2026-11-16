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
package com.vitorpamplona.amethyst.commons.podcasts

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class Rfc2822DateTest {
    private fun javaTime(epochSeconds: Long) = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of("GMT")))

    @Test
    fun knownDate() {
        assertEquals("Tue, 24 Jun 2025 12:00:00 GMT", rfc2822Date(1_750_766_400L))
    }

    @Test
    fun matchesJavaTimeAcrossDates() {
        val samples =
            listOf(0L, 59L, 86_399L, 86_400L, 951_782_400L, 951_868_799L, 1_709_164_800L, 4_107_542_399L, 1_750_766_400L) +
                (0 until 2_000).map { it * 2_629_743L + it * 7_919L }
        samples.forEach { assertEquals(javaTime(it), rfc2822Date(it), "epoch $it") }
    }
}

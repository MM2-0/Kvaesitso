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
package com.vitorpamplona.amethyst.commons.frontpage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontPageScheduleTest {
    @Test
    fun theLanguageIsNamedOutsideTheCorpusAndOnlyWhenThereIsOne() {
        val message = userMessage("DIGEST", "Alice", "pt-BR")
        assertTrue(message.indexOf("Language: pt-BR") in 0 until message.indexOf("<corpus>"))
        assertFalse(userMessage("DIGEST", "Alice", null).contains("Language:"))
        assertFalse(userMessage("DIGEST", "Alice", "  ").contains("Language:"))
        // The brief says what to do with it; the brief itself names no language.
        assertTrue(systemPrompt().contains("## The reader's language"))
    }

    @Test
    fun weekdaysOfKeptEditions() {
        assertEquals(2, dayOfWeek("2026-11-10")) // a Tuesday
        assertEquals(7, dayOfWeek("2026-11-08")) // a Sunday
        assertEquals(1, dayOfWeek("2024-01-01")) // a Monday
        assertEquals(4, dayOfWeek("2024-02-29")) // a leap-day Thursday
        assertNull(dayOfWeek("not-a-day"))
    }

    @Test
    fun theScheduleSurvivesItsOwnEncoding() {
        val schedule = FrontPageSchedule.daily(enabled = true, hour = 6, minute = 45)
        assertEquals(schedule, DataStoreFrontPageScheduleStore.decode(DataStoreFrontPageScheduleStore.encode(schedule)))
        // The old per-device `on|7|30` is NIP-FB's "30 7 * * *".
        assertEquals(FrontPageSchedule(enabled = true, cron = "30 7 * * *"), DataStoreFrontPageScheduleStore.decode("on|7|30"))
        // Anything unreadable is the default: off.
        assertEquals(FrontPageSchedule(), DataStoreFrontPageScheduleStore.decode("on|25|00"))
        assertEquals(FrontPageSchedule(), DataStoreFrontPageScheduleStore.decode(null))
    }
}

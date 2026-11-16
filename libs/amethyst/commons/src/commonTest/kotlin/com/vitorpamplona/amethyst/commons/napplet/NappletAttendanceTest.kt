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
package com.vitorpamplona.amethyst.commons.napplet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NappletAttendanceTest {
    private val attendance = NappletAttendance<String>()

    @Test
    fun unattendedUntilTold() {
        assertFalse(attendance.isAttended("tab"))
        attendance.set("tab", true)
        assertTrue(attendance.isAttended("tab"))
        attendance.forget("tab")
        assertFalse(attendance.isAttended("tab"))
    }

    @Test
    fun awaitReturnsOnceTheUserIsBack() =
        runTest {
            val waiting = async { attendance.awaitAttended("tab", 10_000) }
            testScheduler.advanceTimeBy(1_000)
            attendance.set("other", true)
            testScheduler.advanceTimeBy(1_000)
            attendance.set("tab", true)
            assertTrue(waiting.await())
        }

    @Test
    fun awaitGivesUpAfterTheTimeout() =
        runTest {
            assertFalse(attendance.awaitAttended("tab", 5_000))
        }
}

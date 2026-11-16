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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class Nip46ActivityLogTest {
    private fun entry(i: Int) = Nip46ActivityEntry(atSeconds = i.toLong(), clientPubKey = "a".repeat(64), method = "m$i")

    @Test
    fun keepsOnlyTheNewestEntriesNewestFirst() =
        runTest {
            val log = Nip46ActivityLog(backgroundScope, capacity = 3, publishIntervalMs = 250)

            repeat(10) { log.record(entry(it)) }
            runCurrent()

            assertEquals(listOf("m9", "m8", "m7"), log.entries.value.map { it.method })
        }

    @Test
    fun publishesAtMostOncePerInterval() =
        runTest {
            val log = Nip46ActivityLog(backgroundScope, capacity = 10, publishIntervalMs = 250)

            log.record(entry(0))
            runCurrent()
            assertEquals(listOf("m0"), log.entries.value.map { it.method })

            // Recorded inside the interval: held back, then published together with the next snapshot.
            log.record(entry(1))
            log.record(entry(2))
            runCurrent()
            assertEquals(listOf("m0"), log.entries.value.map { it.method })

            advanceTimeBy(250)
            runCurrent()
            assertEquals(listOf("m2", "m1", "m0"), log.entries.value.map { it.method })
        }
}

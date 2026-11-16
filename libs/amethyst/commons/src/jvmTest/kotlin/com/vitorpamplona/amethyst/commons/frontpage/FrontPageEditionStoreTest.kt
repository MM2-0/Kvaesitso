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

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrontPageEditionStoreTest {
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)

    private fun store() = FrontPageEditionStore(Files.createTempDirectory("editions").toOkioPath(), FileSystem.SYSTEM)

    private fun edition(
        day: String,
        code: String = "C$day",
    ) = SavedEdition(day = day, code = code, printedAt = 0, model = "m", headline = "Lead of $day")

    @Test
    fun keepsTheLastSevenDaysNewestFirst() =
        runBlocking {
            val store = store()
            (1..9).forEach { store.save(alice, edition("2026-11-%02d".format(it)), "<p>$it</p>") }
            val kept = store.list(alice)
            assertEquals((9 downTo 3).map { "2026-11-%02d".format(it) }, kept.map { it.day })
            assertNull(store.html(alice, "2026-11-01"))
            assertEquals("<p>9</p>", store.html(alice, "2026-11-09"))
        }

    @Test
    fun printingAgainReplacesToday() =
        runBlocking {
            val store = store()
            store.save(alice, edition("2026-11-10", "FIRST"), "first")
            store.save(alice, edition("2026-11-10", "SECOND"), "second")
            assertEquals(listOf("SECOND"), store.list(alice).map { it.code })
            assertEquals("second", store.html(alice, "2026-11-10"))
        }

    @Test
    fun eachAccountHasItsOwnWeek() =
        runBlocking {
            val store = store()
            store.save(alice, edition("2026-11-10"), "alice's")
            assertEquals(emptyList(), store.list(bob))
            assertNull(store.html(bob, "2026-11-10"))
        }

    @Test
    fun onlyDaysAreFileNames() =
        runBlocking {
            val store = store()
            store.save(alice, edition("2026-11-10"), "page")
            // A day is read as a file name: nothing else may become one.
            assertNull(store.html(alice, "../../etc/passwd"))
        }

    @Test
    fun aRemovedAccountLeavesNoPapers() =
        runBlocking {
            val store = store()
            store.save(alice, edition("2026-11-10"), "alice's")
            store.save(bob, edition("2026-11-10"), "bob's")
            store.removeAccount(alice)
            assertEquals(emptyList(), store.list(alice))
            assertNull(store.html(alice, "2026-11-10"))
            assertEquals("bob's", store.html(bob, "2026-11-10"))
            // Twice is fine.
            store.removeAccount(alice)
        }
}

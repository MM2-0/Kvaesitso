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
package com.vitorpamplona.amethyst.commons.model.nip56Reports

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserReportCacheNamingIndexTest {
    private val noCtx = UserContext { Note("addr") }

    private fun user(seed: Char) = User(seed.toString().repeat(64), noCtx)

    private fun reportBy(
        id: String,
        reporter: User,
    ) = Note(id).apply { author = reporter }

    @Test
    fun addRecordsTheReportUnderItsReporter() {
        val cache = UserReportCache()
        val alice = user('a')

        cache.addReportNamingUser(reportBy("report1", alice))

        assertEquals(1, cache.reportsNaming(setOf(alice.pubkeyHex)).size)
    }

    @Test
    fun addingTheSameReportTwiceKeepsOneCopy() {
        val cache = UserReportCache()
        val alice = user('a')
        val report = reportBy("report1", alice)

        cache.addReportNamingUser(report)
        cache.addReportNamingUser(report)

        assertEquals(1, cache.reportsNaming(setOf(alice.pubkeyHex)).size)
    }

    @Test
    fun reportsNamingExcludesReportersOutsideTheGivenSet() {
        val cache = UserReportCache()
        val alice = user('a')
        val bob = user('b')

        cache.addReportNamingUser(reportBy("report1", alice))
        cache.addReportNamingUser(reportBy("report2", bob))

        val onlyAlice = cache.reportsNaming(setOf(alice.pubkeyHex))

        assertEquals(1, onlyAlice.size)
        assertEquals("report1", onlyAlice.first().idHex)
    }

    @Test
    fun removeDropsTheReport() {
        val cache = UserReportCache()
        val alice = user('a')
        val report = reportBy("report1", alice)

        cache.addReportNamingUser(report)
        cache.removeReportNamingUser(report)

        assertTrue(cache.reportsNaming(setOf(alice.pubkeyHex)).isEmpty())
    }

    @Test
    fun theNewIndexDoesNotFeedTheHideThresholdCount() {
        val cache = UserReportCache()
        val alice = user('a')

        cache.addReportNamingUser(reportBy("report1", alice))

        assertEquals(0, cache.countReportAuthorsBy(setOf(alice.pubkeyHex)))
        assertTrue(cache.reportsBy(setOf(alice.pubkeyHex)).isEmpty())
    }
}

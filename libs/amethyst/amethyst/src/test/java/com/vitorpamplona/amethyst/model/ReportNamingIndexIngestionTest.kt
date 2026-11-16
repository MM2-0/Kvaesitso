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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `LocalCache` is a process-wide object (`LocalCache.kt:353`) and JUnit 4's default method order is
 * hash-based, not source order. So each test method uses its **own** reporter, target and event ids —
 * sharing them across methods would let one test's consumed report inflate another's count depending
 * on which ran first. The ids must also be unused by every OTHER test class in the JVM: this suite
 * once shared `c3…`/`d3…`/`e5…` with the rating, appointment and calendar suites, and whenever one
 * of their notes was still alive the report here was treated as already seen and never indexed.
 *
 * Each test takes the reported users from the cache *before* consuming and keeps them. `LocalCache`
 * holds users weakly (LargeSoftCache) and nothing else references a user that is only named by a
 * report, so a GC before the read-back would drop the user and the report index with it. In the app
 * the screen showing the user is that strong referent.
 */
class ReportNamingIndexIngestionTest {
    private fun reportNamingBothNoteAndAuthor(
        id: String,
        reporter: String,
        target: String,
        reportedNoteId: String,
    ) = ReportEvent(
        id = id,
        pubKey = reporter,
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("e", reportedNoteId, ReportType.IMPERSONATION.code),
                arrayOf("p", target, ReportType.IMPERSONATION.code),
            ),
        content = "",
        sig = "sig",
    )

    @Test
    fun aReportFiledFromANoteStillReachesTheUserNamingIndex() {
        val reporter = "7a".repeat(32)
        val target = "7b".repeat(32)
        val reported = LocalCache.getOrCreateUser(target)

        LocalCache.consume(
            reportNamingBothNoteAndAuthor("7c".repeat(32), reporter, target, "7d".repeat(32)),
            null,
            true,
        )

        assertEquals(1, reported.reports().reportsNaming(setOf(reporter)).size)
    }

    @Test
    fun theHideThresholdCountIsUnaffectedByNoteFiledReports() {
        val reporter = "8a".repeat(32)
        val target = "8b".repeat(32)
        val reported = LocalCache.getOrCreateUser(target)

        LocalCache.consume(
            reportNamingBothNoteAndAuthor("8c".repeat(32), reporter, target, "8d".repeat(32)),
            null,
            true,
        )

        assertEquals(1, reported.reports().reportsNaming(setOf(reporter)).size)
        assertEquals(0, reported.reports().countReportAuthorsBy(setOf(reporter)))
    }

    @Test
    fun aBareCoMentionedPTagIsNotIndexedWhileTheExplicitlyTypedOffenderIs() {
        val reporter = "9a".repeat(32)
        val offender = "9b".repeat(32)
        val bystander = "9c".repeat(32)
        val reportedNoteId = "9e".repeat(32)

        val report =
            ReportEvent(
                id = "7e".repeat(32),
                pubKey = reporter,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("e", reportedNoteId, ReportType.IMPERSONATION.code),
                        // offender: explicitly typed, own report type
                        arrayOf("p", offender, ReportType.IMPERSONATION.code),
                        // bystander: bare 2-element p tag, no explicit type of its own
                        arrayOf("p", bystander),
                    ),
                content = "",
                sig = "sig",
            )

        val reportedOffender = LocalCache.getOrCreateUser(offender)
        val reportedBystander = LocalCache.getOrCreateUser(bystander)

        LocalCache.consume(report, null, true)

        assertEquals(1, reportedOffender.reports().reportsNaming(setOf(reporter)).size)
        assertEquals(0, reportedBystander.reports().reportsNaming(setOf(reporter)).size)
    }
}

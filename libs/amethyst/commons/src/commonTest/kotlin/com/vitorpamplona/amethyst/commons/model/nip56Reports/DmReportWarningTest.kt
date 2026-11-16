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
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DmReportWarningTest {
    private val noCtx = UserContext { Note("addr") }

    private val me = "1".repeat(64)
    private val counterpartHex = "2".repeat(64)

    private fun user(hex: String) = User(hex, noCtx)

    /** A kind-1984 note by [reporter] naming [targetHex] with [type]. */
    private fun report(
        id: String,
        reporter: User,
        type: ReportType,
        targetHex: String = counterpartHex,
    ): Note {
        val event =
            ReportEvent(
                id = id,
                pubKey = reporter.pubkeyHex,
                createdAt = 1_700_000_000L,
                tags = arrayOf(arrayOf("p", targetHex, type.code)),
                content = "",
                sig = "sig",
            )
        return Note(id).apply {
            this.author = reporter
            this.event = event
        }
    }

    /** A counterpart whose naming index already holds [reports]. */
    private fun counterpartReportedBy(vararg reports: Note): User =
        user(counterpartHex).also { target ->
            reports.forEach { target.reports().addReportNamingUser(it) }
        }

    @Test
    fun noReportsIsSilent() {
        val state =
            dmReportWarningFor(
                counterpart = user(counterpartHex),
                loggedInPubKey = me,
                followingKeySet = setOf("a".repeat(64)),
                warnAboutReports = true,
            )

        assertFalse(state.shouldWarn)
    }

    @Test
    fun reportsOnlyFromNonFollowsAreSilent() {
        val stranger = user("9".repeat(64))
        val counterpart = counterpartReportedBy(report("r1", stranger, ReportType.IMPERSONATION))

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = setOf("a".repeat(64)),
                warnAboutReports = true,
            )

        assertFalse(state.shouldWarn)
    }

    @Test
    fun oneReportFromAFollowWarnsAndCarriesItsType() {
        val alice = user("a".repeat(64))
        val counterpart = counterpartReportedBy(report("r1", alice, ReportType.IMPERSONATION))

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = setOf(alice.pubkeyHex),
                warnAboutReports = true,
            )

        assertTrue(state.shouldWarn)
        assertEquals(1, state.reporterCount)
        assertEquals(setOf(ReportType.IMPERSONATION), state.types.toSet())
    }

    @Test
    fun multipleReportersAggregateAndTypesDeduplicate() {
        val alice = user("a".repeat(64))
        val bob = user("b".repeat(64))
        val counterpart =
            counterpartReportedBy(
                report("r1", alice, ReportType.IMPERSONATION),
                report("r2", bob, ReportType.IMPERSONATION),
                report("r3", bob, ReportType.SPAM),
            )

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = setOf(alice.pubkeyHex, bob.pubkeyHex),
                warnAboutReports = true,
            )

        // Bob filed two of the three; the state keeps one report per reporter, for one avatar each.
        assertEquals(2, state.reports.size)
        assertEquals(2, state.reporterCount)
        assertEquals(setOf(ReportType.IMPERSONATION, ReportType.SPAM), state.types.toSet())
    }

    @Test
    fun aMultiTargetReportOnlyContributesTheCounterpartsOwnReason() {
        // One kind-1984 event naming two different people with two different reasons. It lands in
        // the counterpart's index because it names them, but the other person's reason must not
        // leak into this warning.
        val alice = user("a".repeat(64))
        val someoneElse = "8".repeat(64)
        val multiTarget =
            Note("r1").apply {
                author = alice
                event =
                    ReportEvent(
                        id = "r1",
                        pubKey = alice.pubkeyHex,
                        createdAt = 1_700_000_000L,
                        tags =
                            arrayOf(
                                arrayOf("p", counterpartHex, ReportType.IMPERSONATION.code),
                                arrayOf("p", someoneElse, ReportType.NUDITY.code),
                            ),
                        content = "",
                        sig = "sig",
                    )
            }
        val counterpart = counterpartReportedBy(multiTarget)

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = setOf(alice.pubkeyHex),
                warnAboutReports = true,
            )

        assertTrue(state.shouldWarn)
        assertEquals(setOf(ReportType.IMPERSONATION), state.types.toSet())
    }

    @Test
    fun theLoggedInUserIsNeverWarnedAboutThemselves() {
        val alice = user("a".repeat(64))
        val self =
            user(me).also { it.reports().addReportNamingUser(report("r1", alice, ReportType.SPAM, targetHex = me)) }

        val state =
            dmReportWarningFor(
                counterpart = self,
                loggedInPubKey = me,
                followingKeySet = setOf(alice.pubkeyHex),
                warnAboutReports = true,
            )

        assertFalse(state.shouldWarn)
    }

    @Test
    fun aFollowedCounterpartIsNeverWarnedAbout() {
        val alice = user("a".repeat(64))
        val counterpart = counterpartReportedBy(report("r1", alice, ReportType.IMPERSONATION))

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                // The real caller passes one set that contains both the reporters it filters by and,
                // when applicable, the counterpart themselves. This is that state.
                followingKeySet = setOf(alice.pubkeyHex, counterpartHex),
                warnAboutReports = true,
            )

        assertFalse(state.shouldWarn)
    }

    @Test
    fun theWarnToggleOffSilencesEverything() {
        val alice = user("a".repeat(64))
        val counterpart = counterpartReportedBy(report("r1", alice, ReportType.IMPERSONATION))

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = setOf(alice.pubkeyHex),
                warnAboutReports = false,
            )

        assertFalse(state.shouldWarn)
    }

    @Test
    fun manyReportsStillWarnRatherThanDeferringToTheHideThreshold() {
        // Guards the regression the first draft of the spec would have shipped: a counterpart far
        // above reportWarningThreshold must still produce a warning, because nothing hides a DM
        // room row.
        val reporters = listOf('a', 'b', 'c', 'd', 'e', 'f', 'g').map { user(it.toString().repeat(64)) }
        val counterpart =
            counterpartReportedBy(
                *reporters
                    .mapIndexed { index, reporter -> report("r$index", reporter, ReportType.IMPERSONATION) }
                    .toTypedArray(),
            )

        val state =
            dmReportWarningFor(
                counterpart = counterpart,
                loggedInPubKey = me,
                followingKeySet = reporters.mapTo(mutableSetOf()) { it.pubkeyHex },
                warnAboutReports = true,
            )

        assertTrue(state.shouldWarn)
        assertEquals(7, state.reporterCount)
    }
}

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
package com.vitorpamplona.amethyst.ui.note.elements

import com.vitorpamplona.amethyst.commons.ui.note.elements.StaleRelayThresholds
import com.vitorpamplona.amethyst.commons.ui.note.elements.isStaleByLatestMonitorReports
import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleRelayHintTest {
    private val now = 1_730_000_000L
    private val freshSecsAgo = TimeUtils.ONE_DAY.toLong() // 1 day ago — fresh
    private val staleSecsAgo = StaleRelayThresholds.DEFAULT_THRESHOLD_SECS + TimeUtils.ONE_DAY // > 14d ago

    @Test
    fun `empty relay set is never stale`() {
        assertFalse(isStaleByLatestMonitorReports(emptyList(), now))
    }

    @Test
    fun `single fresh relay is not stale`() {
        assertFalse(
            isStaleByLatestMonitorReports(listOf(now - freshSecsAgo), now),
        )
    }

    @Test
    fun `single stale relay is stale`() {
        assertTrue(
            isStaleByLatestMonitorReports(listOf(now - staleSecsAgo), now),
        )
    }

    @Test
    fun `single never-monitored relay (null) is treated as stale`() {
        assertTrue(
            isStaleByLatestMonitorReports(listOf(null), now),
        )
    }

    @Test
    fun `mixed fresh-and-stale set is not stale (any-fresh wins)`() {
        assertFalse(
            isStaleByLatestMonitorReports(
                listOf(now - staleSecsAgo, now - freshSecsAgo),
                now,
            ),
        )
    }

    @Test
    fun `mixed null-and-fresh set is not stale (any-fresh wins)`() {
        assertFalse(
            isStaleByLatestMonitorReports(
                listOf(null, now - freshSecsAgo),
                now,
            ),
        )
    }

    @Test
    fun `all-stale-or-null set is stale`() {
        assertTrue(
            isStaleByLatestMonitorReports(
                listOf(null, now - staleSecsAgo, now - staleSecsAgo - TimeUtils.ONE_DAY),
                now,
            ),
        )
    }

    @Test
    fun `relay exactly at threshold cutoff is considered fresh`() {
        // cutoff = now - thresholdSecs; helper uses strict `<`, so equal-to-cutoff is fresh
        val cutoff = now - StaleRelayThresholds.DEFAULT_THRESHOLD_SECS
        assertFalse(
            isStaleByLatestMonitorReports(listOf(cutoff), now),
        )
    }

    @Test
    fun `custom threshold is honored`() {
        // 1-hour threshold: 30 minutes ago is fresh, 2 hours ago is stale.
        assertFalse(
            isStaleByLatestMonitorReports(
                listOf(now - 30 * 60),
                now,
                thresholdSecs = 60 * 60,
            ),
        )
        assertTrue(
            isStaleByLatestMonitorReports(
                listOf(now - 2 * 60 * 60),
                now,
                thresholdSecs = 60 * 60,
            ),
        )
    }
}

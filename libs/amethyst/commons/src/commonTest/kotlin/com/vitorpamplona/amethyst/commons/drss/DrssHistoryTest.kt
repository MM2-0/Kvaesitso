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
package com.vitorpamplona.amethyst.commons.drss

import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressAppDataEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class DrssHistoryTest {
    private val me = "a".repeat(64)
    private val showA = "b".repeat(64)
    private val showB = "c".repeat(64)

    private fun record(
        show: HexKey,
        episode: String,
        position: Long? = null,
        duration: Long? = null,
        completedAt: Long? = null,
        plays: Long? = null,
    ): DrssListenProgressAppDataEvent {
        val template = DrssListenProgressAppDataEvent.build(ATag(LongFormContentEvent.KIND, show, episode, null), position, duration, completedAt, plays, createdAt = 1)
        return DrssListenProgressAppDataEvent("0".repeat(64), me, 1, template.tags, "", "0".repeat(128))
    }

    @Test
    fun aFinishedEpisodeCountsItsLengthOncePerPlay() {
        assertEquals(1200, DrssHistory.heardSeconds(record(showA, "e1", duration = 600, completedAt = 10, plays = 2)))
        assertEquals(600, DrssHistory.heardSeconds(record(showA, "e1", duration = 600, completedAt = 10)))
        assertEquals(700, DrssHistory.heardSeconds(record(showA, "e1", position = 100, duration = 600, completedAt = 10, plays = 1)))
        assertEquals(90, DrssHistory.heardSeconds(record(showA, "e1", position = 90)))
    }

    @Test
    fun feedsAreRankedByTimeHeard() {
        val feeds =
            DrssHistory.feedsHeard(
                listOf(
                    record(showA, "e1", position = 300, duration = 600),
                    record(showB, "e2", duration = 600, completedAt = 10, plays = 1),
                    record(showA, "e3", position = 60, duration = 600),
                ),
            )
        assertEquals(listOf(showB, showA), feeds.map { it.feed })
        assertEquals(DrssFeedHeard(showA, 360, 2, 0), feeds[1])
        assertEquals(1, feeds[0].finished)
    }
}

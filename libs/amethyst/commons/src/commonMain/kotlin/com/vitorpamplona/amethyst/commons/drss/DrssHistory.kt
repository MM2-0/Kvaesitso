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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgress
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** How much of one feed the user has heard: time, episodes started, and episodes finished. */
@Immutable
data class DrssFeedHeard(
    val feed: HexKey,
    val heardSeconds: Long,
    val episodes: Int,
    val finished: Int,
)

/** drss.io's history figures, read from the user's listening records. */
object DrssHistory {
    /**
     * About how long the user has listened to one episode: every finished play is its whole
     * length, plus where they are in the current one.
     */
    fun heardSeconds(record: DrssListenProgress): Long {
        val position = record.position() ?: 0
        val duration = record.durationInSeconds() ?: 0
        val plays = record.plays() ?: if (record.completedAt() != null) 1 else 0
        return plays * duration + position
    }

    /** One line per feed the records belong to, the most heard first. */
    fun feedsHeard(records: List<DrssListenProgress>): List<DrssFeedHeard> =
        records
            .groupBy { it.episode()?.pubKeyHex }
            .mapNotNull { (feed, own) ->
                if (feed == null) return@mapNotNull null
                DrssFeedHeard(
                    feed = feed,
                    heardSeconds = own.sumOf { heardSeconds(it) },
                    episodes = own.size,
                    finished = own.count { it.completedAt() != null },
                )
            }.sortedWith(compareByDescending<DrssFeedHeard> { it.heardSeconds }.thenByDescending { it.episodes })
}

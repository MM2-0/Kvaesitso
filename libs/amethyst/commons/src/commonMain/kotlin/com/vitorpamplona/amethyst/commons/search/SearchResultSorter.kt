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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.sortedBySnapshot
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.utils.currentTimeSeconds

object SearchResultSorter {
    fun sortPeople(
        people: List<User>,
        order: SearchSortOrder,
    ): List<User> =
        when (order) {
            SearchSortOrder.NAME_AZ -> people.sortedBySnapshot { it.toBestDisplayName().lowercase() }
            SearchSortOrder.NAME_ZA -> people.sortedByDescending { it.toBestDisplayName().lowercase() }
            else -> people
        }

    fun scoreEvent(
        event: Event,
        searchText: String,
    ): Double {
        val query = searchText.trim().lowercase()
        if (query.isEmpty()) return event.createdAt.toDouble()

        var score = 0.0
        val content = event.content.lowercase()
        val tokens = query.split(WHITESPACE)

        // Exact phrase match in content
        if (content.contains(query)) {
            score += 10.0
        }

        // Per-token scoring
        for (token in tokens) {
            if (token.isEmpty()) continue
            if (wordBoundaryRegex(token).containsMatchIn(content)) {
                score += 5.0
            } else if (content.contains(token)) {
                score += 2.0
            }
        }

        // Article title boost
        if (event is LongFormContentEvent) {
            val title = event.title()?.lowercase()
            if (title != null) {
                if (title.contains(query)) {
                    score += 15.0
                }
                for (token in tokens) {
                    if (token.isEmpty()) continue
                    if (title.contains(token)) {
                        score += 3.0
                    }
                }
            }
        }

        // Recency tiebreaker (normalized 0..1)
        val now = currentTimeSeconds()
        val age = (now - event.createdAt).coerceAtLeast(1)
        val maxAge = 365L * 24 * 3600 // 1 year
        score += (1.0 - (age.toDouble() / maxAge).coerceIn(0.0, 1.0))

        return score
    }

    private val WHITESPACE = Regex("\\s+")

    // scoreEvent runs once per result with the same query tokens; compiling a
    // Pattern per (event, token) pair made a 500-result sort build thousands.
    // Small, lock-guarded (sorts can run from several search coroutines).
    private val cacheLock = KmpLock()
    private val wordBoundaryCache = mutableMapOf<String, Regex>()

    private fun wordBoundaryRegex(token: String): Regex =
        cacheLock.withLock {
            wordBoundaryCache[token] ?: Regex("\\b${Regex.escape(token)}\\b").also {
                if (wordBoundaryCache.size > 64) wordBoundaryCache.clear()
                wordBoundaryCache[token] = it
            }
        }
}

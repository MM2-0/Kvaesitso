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

/** A chapter of an episode: where it starts, in seconds, and its title. */
@Immutable
data class DrssChapter(
    val seconds: Long,
    val title: String,
)

/**
 * The chapters in an episode's show notes, as drss.io writes them: lines that open with a
 * timestamp (`00:01:08 - On design`, `12:30 Questions`, `[1:02:03] Outro`), sorted by start.
 */
object DrssChapters {
    /** How far into a chapter "previous" restarts it instead of going to the one before. */
    const val RESTART_SECONDS = 3L

    private val LINE = Regex("""^\s*(?:[-*•]\s*)?[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*(?:[-–—:|.]\s*)?(.*)$""")

    fun parse(content: String): List<DrssChapter> {
        val chapters = mutableListOf<DrssChapter>()
        content.lineSequence().forEach { line ->
            val match = LINE.matchEntire(line) ?: return@forEach
            val seconds = parseClock(match.groupValues[1]) ?: return@forEach
            chapters.add(DrssChapter(seconds, match.groupValues[2].trim()))
        }
        return chapters.sortedBy { it.seconds }.distinctBy { it.seconds }
    }

    /** `1:02:03` or `12:03` in seconds, or null when a field is out of range. */
    fun parseClock(clock: String): Long? {
        val parts = clock.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.drop(1).any { it >= 60 }) return null
        return parts.fold(0L) { acc, part -> acc * 60 + part }
    }

    /** The last chapter starting at or before [positionSeconds], or -1 before the first. */
    fun currentIndex(
        chapters: List<DrssChapter>,
        positionSeconds: Long,
    ): Int = chapters.indexOfLast { it.seconds <= positionSeconds }

    /**
     * Where "previous chapter" goes from [positionSeconds]: the start of the current chapter, or
     * the one before it within [RESTART_SECONDS] of that start; zero before the first chapter.
     */
    fun previousStart(
        chapters: List<DrssChapter>,
        positionSeconds: Long,
    ): Long {
        val index = currentIndex(chapters, positionSeconds)
        if (index < 0) return 0
        val start = chapters[index].seconds
        return if (positionSeconds - start < RESTART_SECONDS && index > 0) chapters[index - 1].seconds else start
    }

    /** The next chapter's start after [positionSeconds], or null at the last. */
    fun nextStart(
        chapters: List<DrssChapter>,
        positionSeconds: Long,
    ): Long? = chapters.getOrNull(currentIndex(chapters, positionSeconds) + 1)?.seconds
}

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
package com.vitorpamplona.amethyst.commons.model.backups

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.utils.Log

/**
 * How an undecided backup conflict is written to storage.
 *
 * A conflict is a question put to the user, and the backup it holds back stays held back until
 * they answer. That outlives the process it was raised in, so the three events it is made of —
 * the saved version, the incoming one, and the change that caused it — are stored and read back.
 *
 * The diff is **not** stored. It is recomputed from the two versions on load, so a conflict whose
 * incoming version no longer removes anything (the other app put it back) simply stops being one
 * instead of being resurrected from a stale description of itself.
 *
 * Kept out of `LocalPreferences` so it is reachable from a plain JVM test.
 */
object BackupConflictStorage {
    // Every settings save writes the open conflicts, and each can hold a follow list with
    // thousands of entries: reuse the last encoding while the conflicts themselves are the same.
    @Volatile
    private var lastEncoded: Pair<List<String>, String>? = null

    fun encode(conflicts: List<Triple<Event, Event, Event>>): String {
        val ids = conflicts.flatMap { (saved, incoming, cause) -> listOf(saved.id, incoming.id, cause.id) }
        lastEncoded?.let { (cachedIds, encoded) -> if (cachedIds == ids) return encoded }
        return encodeUncached(conflicts).also { lastEncoded = ids to it }
    }

    private fun encodeUncached(conflicts: List<Triple<Event, Event, Event>>): String =
        JsonMapper.toJson(
            conflicts.map { (saved, incoming, cause) ->
                listOf(
                    OptimizedJsonMapper.toJson(saved),
                    OptimizedJsonMapper.toJson(incoming),
                    OptimizedJsonMapper.toJson(cause),
                )
            },
        )

    /**
     * Unreadable entries are dropped one at a time: a conflict that cannot be parsed is not one
     * the user can be asked about, and it must not take the readable ones down with it.
     */
    fun decode(stored: String?): List<Triple<Event, Event, Event>> {
        if (stored.isNullOrBlank()) return emptyList()
        val rows =
            try {
                JsonMapper.fromJson<List<List<String>>>(stored)
            } catch (e: Exception) {
                Log.w("BackupConflictStorage", "Could not read the stored backup conflicts", e)
                return emptyList()
            }

        return rows.mapNotNull { row ->
            if (row.size != 3) return@mapNotNull null
            try {
                Triple(
                    OptimizedJsonMapper.fromJson(row[0]),
                    OptimizedJsonMapper.fromJson(row[1]),
                    OptimizedJsonMapper.fromJson(row[2]),
                )
            } catch (e: Exception) {
                Log.w("BackupConflictStorage", "Could not read a stored backup conflict", e)
                null
            }
        }
    }
}

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

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Where a platform keeps two strings. Two reads at startup, one write per change.
 *
 * Deliberately this small: the whole of what a search history *is* — the cap, the
 * most-recent-first ordering, the de-duplication, the encoding — is the same everywhere, and the
 * only genuine difference between a desktop and a phone here is which file the bytes land in.
 */
interface SearchHistoryStorage {
    suspend fun read(key: String): String?

    suspend fun write(
        key: String,
        value: String?,
    )
}

/**
 * The searches a reader has run, and the ones they kept.
 *
 * Desktop has had both for a while; Android had neither, because the code was written into a
 * desktop `object` sitting on `java.util.prefs`. Nothing about the behaviour was desktop-specific
 * — it is a capped list and a labelled list — so it moves here whole and Android gains it by
 * being handed a different [SearchHistoryStorage].
 *
 * Queries are stored as their serialized text, which is the same text the box holds. That is what
 * makes a history entry re-runnable by simply putting it back in the field, and it means a stored
 * search survives a change to [SearchQuery]'s shape as long as the token language still parses.
 */
class SearchHistory(
    private val storage: SearchHistoryStorage,
    private val scope: CoroutineScope,
) {
    private val _recent = MutableStateFlow<List<SearchQuery>>(emptyList())

    /** Most recent first, capped at [MAX_RECENT]. */
    val recent: StateFlow<List<SearchQuery>> = _recent.asStateFlow()

    private val _saved = MutableStateFlow<List<SavedSearch>>(emptyList())
    val saved: StateFlow<List<SavedSearch>> = _saved.asStateFlow()

    init {
        // Fire and forget, as the drawer's collapse state is: the lists read as empty until disk
        // answers, and the worst case is a recent-searches row that appears a frame late.
        //
        // Merged rather than assigned. A reader can press Enter inside the window between the
        // screen opening and the file being parsed, and an assignment would drop that search on
        // the floor — in memory *and* on disk, since the next write persists whatever survived.
        // What was remembered in the meantime is newer than the file, so it stays in front.
        scope.launch {
            val storedRecentRaw = readOrNull(KEY_RECENT)
            val merged = _recent.updateAndGet { pending -> mostRecentFirst(pending, decodeQueries(storedRecentRaw)) }
            // Written back when the merge changed anything, because the pending entry was only
            // ever persisted against an empty list — leaving it would drop the file's contents on
            // the next write instead of the reader's search.
            encodeQueries(merged).let { if (it != storedRecentRaw.orEmpty()) persist(KEY_RECENT, it) }

            val storedSavedRaw = readOrNull(KEY_SAVED)
            val mergedSaved = _saved.updateAndGet { pending -> (decodeSaved(storedSavedRaw) + pending).distinctBy { it.id } }
            encodeSaved(mergedSaved).let { if (it != storedSavedRaw.orEmpty()) persist(KEY_SAVED, it) }
        }
    }

    /**
     * Records a search the reader actually ran.
     *
     * Re-running something already in the list moves it to the top rather than adding it twice —
     * compared on the serialized form, so two queries that mean the same thing count as one
     * however they were typed.
     */
    fun remember(query: SearchQuery) {
        if (query.isEmpty) return
        val next = mostRecentFirst(listOf(query), _recent.value)
        _recent.value = next
        persist(KEY_RECENT, encodeQueries(next))
    }

    /**
     * [newer] in front of [older], one entry per distinct query, capped.
     *
     * Compared on the serialized form so two queries that mean the same thing are one entry
     * however they were typed — and so that a list which reaches the screen can never carry two
     * rows with the same key, which a lazy list treats as a crash rather than a duplicate.
     */
    private fun mostRecentFirst(
        newer: List<SearchQuery>,
        older: List<SearchQuery>,
    ): List<SearchQuery> = (newer + older).distinctBy { QuerySerializer.serialize(it) }.take(MAX_RECENT)

    fun clearRecent() {
        _recent.value = emptyList()
        persist(KEY_RECENT, null)
    }

    /** Keeps [query] under a name the reader chose. */
    fun save(
        query: SearchQuery,
        label: String,
    ) {
        if (query.isEmpty) return
        val now = TimeUtils.now()
        val next = _saved.value + SavedSearch(id = newId(now), label = label, query = query, createdAt = now)
        _saved.value = next
        persist(KEY_SAVED, encodeSaved(next))
    }

    /**
     * An id no other saved search has.
     *
     * The timestamp alone is not enough and neither is the timestamp plus the list's size: two
     * searches saved in the same second — or one saved after another was deleted — landed on the
     * same id, and an id is what [forget] deletes by and what the restore merge de-duplicates by.
     * The random half makes a collision across two runs of the app about as likely as one inside
     * a single list, which is to say not.
     */
    private fun newId(now: Long): String = "$now-${Random.nextLong().toULong().toString(36)}"

    fun forget(id: String) {
        val next = _saved.value.filter { it.id != id }
        _saved.value = next
        persist(KEY_SAVED, encodeSaved(next))
    }

    private suspend fun readOrNull(key: String): String? =
        try {
            storage.read(key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SearchHistory") { "Could not read $key: ${e.message}" }
            null
        }

    private fun persist(
        key: String,
        value: String?,
    ) {
        scope.launch {
            try {
                storage.write(key, value)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SearchHistory") { "Could not write $key: ${e.message}" }
            }
        }
    }

    companion object {
        const val KEY_RECENT = "search_history"
        const val KEY_SAVED = "saved_searches"

        /** Long enough to find what you ran this morning, short enough to scan. */
        const val MAX_RECENT = 20

        private const val RECORD = "\n"
        private const val FIELD = "\t"

        fun encodeQueries(queries: List<SearchQuery>) = queries.joinToString(RECORD) { QuerySerializer.serialize(it) }

        fun decodeQueries(raw: String?): List<SearchQuery> =
            raw
                ?.split(RECORD)
                ?.mapNotNull { line -> QueryParser.parse(line).takeUnless { it.isEmpty } }
                .orEmpty()

        fun encodeSaved(searches: List<SavedSearch>) =
            searches.joinToString(RECORD) {
                listOf(escape(it.id), escape(it.label), it.createdAt.toString(), QuerySerializer.serialize(it.query)).joinToString(FIELD)
            }

        /**
         * Keeps a reader's label out of the separators.
         *
         * A label is the one free-text field here, and a tab in it shifted every field after it —
         * so naming a saved search "mine\tyours" lost the query it named. Only the two separators
         * and the escape character itself are touched, so a label that contains none of them
         * encodes to exactly itself and an already-stored history reads back unchanged.
         */
        private fun escape(value: String) = value.replace("\\", "\\\\").replace(FIELD, "\\t").replace(RECORD, "\\n")

        /** Anything else after a backslash is left alone, so a label stored before [escape] existed survives. */
        private fun unescape(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c != '\\' || i == value.lastIndex) {
                    out.append(c)
                    i++
                    continue
                }
                when (val next = value[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(c).append(next)
                }
                i += 2
            }
            return out.toString()
        }

        fun decodeSaved(raw: String?): List<SavedSearch> =
            raw
                ?.split(RECORD)
                ?.mapNotNull { line ->
                    val parts = line.split(FIELD)
                    if (parts.size < 4) return@mapNotNull null
                    val createdAt = parts[2].toLongOrNull() ?: return@mapNotNull null
                    val query = QueryParser.parse(parts[3])
                    if (query.isEmpty) null else SavedSearch(id = unescape(parts[0]), label = unescape(parts[1]), query = query, createdAt = createdAt)
                }.orEmpty()
    }
}

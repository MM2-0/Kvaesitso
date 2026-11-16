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
package com.vitorpamplona.amethyst.commons.cashu

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * DataStore-backed NUT-13 counter store, shared by every front end that has one.
 *
 * # Why the counters are not secret
 *
 * They are indices, not key material: they derive nothing without the wallet
 * seed, carry no value, and a leak would at most reveal how many proofs the
 * wallet has minted at each keyset.
 *
 * # Why every write is awaited
 *
 * Reusing a counter makes the mint reply `outputs already signed` and strands
 * the proofs, so [reserve] must reach disk before the secrets derived from it
 * reach the mint. DataStore's `edit` suspends until its write completes and
 * swaps the file atomically — the same guarantee
 * `SharedPreferences.edit(commit = true)` gave, paid at a suspension rather
 * than a blocked thread.
 *
 * Read and write live inside one `edit`, so two concurrent mints cannot
 * observe the same starting index; that is what the previous implementation's
 * `@Synchronized` was for.
 */
class DataStoreCashuCounterStore(
    private val store: DataStore<Preferences>,
) : CashuKeysetCounterStore {
    companion object {
        const val COUNTER_PREFIX = "counter_"

        fun counterKey(keysetId: String) = longPreferencesKey(COUNTER_PREFIX + keysetId)
    }

    private suspend fun read(): Preferences =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    override suspend fun peek(keysetId: String): Long = read()[counterKey(keysetId)] ?: 0L

    override suspend fun reserve(
        keysetId: String,
        count: Int,
    ): Long {
        require(count > 0) { "Counter reservation must be positive" }
        var first = 0L
        store.edit { prefs ->
            val key = counterKey(keysetId)
            first = prefs[key] ?: 0L
            prefs[key] = first + count.toLong()
        }
        return first
    }

    /**
     * Carry a counter forward from an older store, never backwards.
     *
     * Writes the value directly in one atomic edit rather than advancing
     * through [reserve], and compares inside that edit so a concurrent
     * reservation cannot be undone by a stale read.
     */
    override suspend fun seedIfMissing(
        keysetId: String,
        legacyValue: Long,
    ) {
        if (legacyValue <= 0L) return
        store.edit { prefs ->
            val key = counterKey(keysetId)
            if ((prefs[key] ?: 0L) < legacyValue) prefs[key] = legacyValue
        }
    }
}

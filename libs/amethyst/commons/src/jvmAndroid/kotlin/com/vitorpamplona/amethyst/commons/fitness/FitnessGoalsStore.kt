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
package com.vitorpamplona.amethyst.commons.fitness

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * Where an account's weekly [FitnessGoals] live: one key in that account's own preference store
 * ([com.vitorpamplona.amethyst.commons.model.preferences.AccountPreferenceStores]), so the goals
 * are per account, stay on the device, and go when the account is removed.
 *
 * Deliberately not a Nostr event yet. Goals are personal, and a public one would broadcast a health
 * target; syncing them across devices belongs in an encrypted NIP-78 app-data event, which is the
 * follow-up in `commons/plans/2026-10-06-my-fitness-redesign.md`.
 */
class FitnessGoalsStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        private val goalsKey = stringPreferencesKey("my_fitness_goals")
    }

    /** The saved goals, or the defaults when none are; a read error falls back to the defaults too. */
    val flow: Flow<FitnessGoals> =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { FitnessGoals.decode(it[goalsKey]) }

    suspend fun load(): FitnessGoals = flow.first()

    suspend fun save(goals: FitnessGoals) {
        store.edit { it[goalsKey] = goals.encode() }
    }
}

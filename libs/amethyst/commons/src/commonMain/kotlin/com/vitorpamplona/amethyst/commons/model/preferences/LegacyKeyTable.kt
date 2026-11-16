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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * The older preference file a migration reads from, reduced to the four calls
 * a migration makes.
 *
 * An interface because the legacy store is an Android
 * `EncryptedSharedPreferences`, which commonMain cannot name — and because a
 * test wants to hand a migration a map rather than a file.
 *
 * Every getter returns null for an absent key rather than a default, so a
 * migration can tell "the user turned this off" from "the user never touched
 * it" — the distinction the whole copy depends on, since a key left absent
 * keeps reading as unset and falls back to its own default.
 */
interface LegacyPreferenceSource {
    /** Every key the file holds, used to spot ones no migration claims. */
    fun keys(): Set<String>

    fun getBoolean(name: String): Boolean?

    fun getString(name: String): String?

    fun getStringSet(name: String): Set<String>?
}

/**
 * One legacy key, and the [Preferences.Key] it lands on.
 *
 * The legacy name is a compatibility surface: it is the string the Android app
 * has written since its first release, and it is frequently *not* the new key's
 * name (`has_donated_in_version` became `hasDonatedInVersion`, and five of
 * [DialogDismissalStore]'s nine keys were renamed like that). So both names are
 * spelled out here rather than derived from one another.
 */
sealed class LegacyKey<T : Any>(
    val legacyName: String,
    val key: Preferences.Key<T>,
) {
    abstract fun read(source: LegacyPreferenceSource): T?

    /** Absent stays absent — see [LegacyPreferenceSource]. */
    fun copyInto(
        source: LegacyPreferenceSource,
        out: MutablePreferences,
    ) {
        read(source)?.let { out[key] = it }
    }
}

class LegacyBooleanKey(
    legacyName: String,
    key: Preferences.Key<Boolean>,
) : LegacyKey<Boolean>(legacyName, key) {
    override fun read(source: LegacyPreferenceSource) = source.getBoolean(legacyName)
}

class LegacyStringKey(
    legacyName: String,
    key: Preferences.Key<String>,
) : LegacyKey<String>(legacyName, key) {
    override fun read(source: LegacyPreferenceSource) = source.getString(legacyName)
}

class LegacyStringSetKey(
    legacyName: String,
    key: Preferences.Key<Set<String>>,
) : LegacyKey<Set<String>>(legacyName, key) {
    override fun read(source: LegacyPreferenceSource) = source.getStringSet(legacyName)
}

/**
 * The keys one migration carries, as data.
 *
 * The point of the table is that the copy and the later *check* that the copy
 * happened read from the same list. A migration written as a block of
 * `if (legacy.contains(k)) out[key] = legacy.getBoolean(k)` lines cannot be
 * asked what it covers, so anything verifying it has to restate the list — and
 * a key added to one copy and not the other is exactly the silent hole that
 * makes deleting the legacy file unsafe.
 *
 * @param markerName the key recording, in the destination, that this copy has
 *   run. Distinct per table, so several can run against one store.
 */
class LegacyKeyTable(
    val markerName: String,
    val keys: List<LegacyKey<*>>,
) {
    private val marker = booleanPreferencesKey(markerName)

    val legacyNames: Set<String> = keys.mapTo(mutableSetOf()) { it.legacyName }

    /**
     * Builds the one-shot copy.
     *
     * [openSource] is called only when the migration actually runs, so opening
     * the legacy file is not a cost paid on every launch — decrypting an
     * `EncryptedSharedPreferences` is not free.
     */
    fun migration(openSource: () -> LegacyPreferenceSource): DataMigration<Preferences> =
        CopyOnceMigration(markerName) { out ->
            withContext(Dispatchers.IO) {
                val source = openSource()
                keys.forEach { it.copyInto(source, out) }
            }
        }

    /**
     * Whether the copy has run against [destination].
     *
     * This, and not a value-by-value comparison, is what says the legacy keys
     * made it across. [CopyOnceMigration] writes the values and this marker as
     * one `Preferences`, which DataStore commits atomically, so the marker
     * being set means every value the copy read was written with it.
     *
     * A comparison would be the wrong question anyway: once migrated, these
     * groups are written *only* to the new store, so the legacy file is a
     * frozen snapshot and the two are expected to diverge the moment the user
     * changes a setting.
     */
    fun hasRun(destination: Preferences): Boolean = destination[marker] == true
}

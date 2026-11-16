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
package com.vitorpamplona.amethyst.commons.napplet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed [NappletStorage]. Every key is prefixed with the **active account** and then the
 * applet's coordinate, so one napplet's keys can never collide with another's, one account's data is
 * never visible to another, and this store is entirely separate from the app's own preferences.
 *
 * [accountPubKey] is read at call time rather than captured, so switching accounts moves reads and
 * writes to the new namespace with no rebuild — an embedded applet always sees the current account's
 * data and never the previous one's.
 */
class DataStoreNappletStorage(
    private val dataStore: DataStore<Preferences>,
    private val accountPubKey: () -> String,
) : NappletStorage {
    /** The same file, read and written for [pubKey] alone, whoever is logged in. */
    fun forAccount(pubKey: String): DataStoreNappletStorage = DataStoreNappletStorage(dataStore) { pubKey }

    override suspend fun get(
        coordinate: String,
        key: String,
    ): String? = dataStore.data.first()[keyOf(coordinate, key)]

    override suspend fun set(
        coordinate: String,
        key: String,
        value: String,
    ) = set(coordinate, key, value, quotaCoordinate = coordinate)

    override suspend fun set(
        coordinate: String,
        key: String,
        value: String,
        quotaCoordinate: String,
    ) {
        dataStore.edit { preferences ->
            // One quota for the app's shared namespace and every instance namespace under it: each
            // launch has its own instance id (an inline Run makes a new one), so a quota per instance
            // let an app write 512 KB more every time it was run, into a store held in memory. Both
            // prefixes end where the coordinate does, so no other app's namespace can match them.
            val sharedPrefix = prefixOf(quotaCoordinate)
            val instancePrefix = "${accountPubKey()}\u0000$quotaCoordinate$INSTANCE_MARK"
            val target = keyOf(coordinate, key)
            val currentBytes =
                preferences
                    .asMap()
                    .entries
                    .asSequence()
                    .filter { it.key.name.startsWith(sharedPrefix) || it.key.name.startsWith(instancePrefix) }
                    .sumOf { (storedKey, storedValue) ->
                        val name = storedKey.name
                        // The key the app chose, after its namespace (an instance one carries its id).
                        val appKey = if (name.startsWith(sharedPrefix)) name.removePrefix(sharedPrefix) else name.removePrefix(instancePrefix).substringAfter('\u0000')
                        appKey.encodeToByteArray().size +
                            ((storedValue as? String)?.encodeToByteArray()?.size ?: 0)
                    }
            val replacedBytes = if (target in preferences) key.encodeToByteArray().size + (preferences[target]?.encodeToByteArray()?.size ?: 0) else 0
            val proposedBytes = currentBytes - replacedBytes + key.encodeToByteArray().size + value.encodeToByteArray().size
            require(proposedBytes <= MAX_STORAGE_BYTES) { "Napplet storage quota exceeded." }
            preferences[target] = value
        }
    }

    override suspend fun remove(
        coordinate: String,
        key: String,
    ) {
        dataStore.edit { it.remove(keyOf(coordinate, key)) }
    }

    override suspend fun keys(coordinate: String): List<String> {
        // Must match keyOf's separator exactly. This filtered on a space while keys are written
        // with NUL, so no key could ever match and keys() always returned an empty list.
        val prefix = prefixOf(coordinate)
        return dataStore.data
            .first()
            .asMap()
            .keys
            .map { it.name }
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }

    /** Account first, then applet: isolates accounts from each other, and applets within an account. */
    private fun prefixOf(coordinate: String) = "${accountPubKey()}\u0000$coordinate\u0000"

    private fun keyOf(
        coordinate: String,
        key: String,
    ) = stringPreferencesKey(prefixOf(coordinate) + key)

    companion object {
        /** NAP-STORAGE's recommended per-napplet UTF-8 quota. */
        const val MAX_STORAGE_BYTES = 512 * 1024

        /** How [NappletIdentity.instanceStorageCoordinate] extends the shared one. */
        const val INSTANCE_MARK = ":instance:"
    }
}

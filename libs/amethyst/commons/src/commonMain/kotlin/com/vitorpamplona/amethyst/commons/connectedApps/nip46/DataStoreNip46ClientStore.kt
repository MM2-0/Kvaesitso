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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.flow.first

/**
 * Single-file DataStore-backed [Nip46ClientStore]. Every connected client's
 * display + relay info lives in one `datastore/nip46_clients.preferences_pb`
 * file; a SHA-256 prefix of the coordinate is the key so the (already public)
 * coordinate is kept alongside for [all]'s reverse lookup. Fields are stored
 * individually so no serialization library is needed; [relays] is newline-joined.
 */
class DataStoreNip46ClientStore(
    private val dataStore: DataStore<Preferences>,
) : Nip46ClientStore {
    override suspend fun load(coordinate: String): Nip46ClientInfo? {
        val prefs = dataStore.data.first()
        if (prefs[coordKey(coordinate)] == null) return null
        return Nip46ClientInfo(
            name = prefs[nameKey(coordinate)],
            url = prefs[urlKey(coordinate)],
            image = prefs[imageKey(coordinate)],
            relays = prefs[relaysKey(coordinate)].toRelaySet(),
        )
    }

    override suspend fun store(
        coordinate: String,
        info: Nip46ClientInfo,
    ) {
        dataStore.edit { prefs ->
            prefs[coordKey(coordinate)] = coordinate
            info.name?.let { prefs[nameKey(coordinate)] = it } ?: prefs.remove(nameKey(coordinate))
            info.url?.let { prefs[urlKey(coordinate)] = it } ?: prefs.remove(urlKey(coordinate))
            info.image?.let { prefs[imageKey(coordinate)] = it } ?: prefs.remove(imageKey(coordinate))
            if (info.relays.isNotEmpty()) prefs[relaysKey(coordinate)] = info.relays.joinToString("\n") else prefs.remove(relaysKey(coordinate))
        }
    }

    override suspend fun remove(coordinate: String) {
        dataStore.edit { prefs ->
            prefs.remove(coordKey(coordinate))
            prefs.remove(nameKey(coordinate))
            prefs.remove(urlKey(coordinate))
            prefs.remove(imageKey(coordinate))
            prefs.remove(relaysKey(coordinate))
        }
    }

    override suspend fun all(): Map<String, Nip46ClientInfo> {
        val prefs = dataStore.data.first()
        val result = mutableMapOf<String, Nip46ClientInfo>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(COORD_PREFIX)) continue
            val coordinate = value as? String ?: continue
            result[coordinate] =
                Nip46ClientInfo(
                    name = prefs[nameKey(coordinate)],
                    url = prefs[urlKey(coordinate)],
                    image = prefs[imageKey(coordinate)],
                    relays = prefs[relaysKey(coordinate)].toRelaySet(),
                )
        }
        return result
    }

    private fun String?.toRelaySet(): Set<String> = this?.split("\n")?.filterTo(mutableSetOf()) { it.isNotEmpty() } ?: emptySet()

    private fun coordKey(coordinate: String) = stringPreferencesKey("$COORD_PREFIX${hash(coordinate)}")

    private fun nameKey(coordinate: String) = stringPreferencesKey("name:${hash(coordinate)}")

    private fun urlKey(coordinate: String) = stringPreferencesKey("url:${hash(coordinate)}")

    private fun imageKey(coordinate: String) = stringPreferencesKey("img:${hash(coordinate)}")

    private fun relaysKey(coordinate: String) = stringPreferencesKey("relays:${hash(coordinate)}")

    companion object {
        const val FILE_NAME = "nip46_clients"

        private const val COORD_PREFIX = "coord:"

        /**
         * The first 8 bytes of the coordinate's SHA-256, lower-case hex.
         *
         * This is a stored key, so it must keep producing exactly what
         * `MessageDigest.getInstance("SHA-256")` plus `"%02x".format(byte)` did
         * on Android — a different digest here would orphan every client a user
         * has already authorized rather than fail loudly.
         * `DataStoreNip46ClientStoreTest` pins three coordinates against hashes
         * computed outside this codebase.
         */
        internal fun hash(coordinate: String): String = sha256(coordinate.encodeToByteArray()).copyOfRange(0, 8).toHexKey()
    }
}

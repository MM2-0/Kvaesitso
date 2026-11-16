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
package com.vitorpamplona.amethyst.commons.napplet.permissions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import kotlinx.coroutines.flow.first

/**
 * Persists the standing napplet grants ([GrantState.ALLOW_ALWAYS] / [GrantState.DENY]) in a
 * dedicated DataStore. Keyed by `"<coordinate>\u0000<capability>"` so a coordinate's grants can
 * be read and cleared as a group. Session and one-shot grants are not persisted — the ledger
 * keeps those in memory.
 */
class DataStoreNappletPermissionStore(
    private val dataStore: DataStore<Preferences>,
    private val accountPubKey: () -> String,
) : NappletPermissionStore {
    // The same file, read for one account: still one DataStore instance per file.
    override fun forAccount(pubKey: String): NappletPermissionStore = DataStoreNappletPermissionStore(dataStore) { pubKey }

    /**
     * Grants belong to one account. [accountPubKey] is read at call time, so an account switch moves
     * every read and write to that account's namespace with no rebuild — a grant made by one account
     * can never authorize another.
     */
    private fun scoped(coordinate: String) = "${accountPubKey()}$SEP$coordinate"

    override suspend fun load(coordinate: String): Map<NappletCapability, GrantState> {
        val prefs = dataStore.data.first()
        val prefix = "${scoped(coordinate)}$SEP"
        val result = mutableMapOf<NappletCapability, GrantState>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(prefix)) continue
            val capability = runCatching { NappletCapability.valueOf(name.substring(prefix.length)) }.getOrNull() ?: continue
            val grant = runCatching { GrantState.valueOf(value as String) }.getOrNull() ?: continue
            if (grant == GrantState.ALLOW_ALWAYS || grant == GrantState.DENY) result[capability] = grant
        }
        return result
    }

    override suspend fun store(
        coordinate: String,
        capability: NappletCapability,
        grant: GrantState,
    ) {
        if (grant != GrantState.ALLOW_ALWAYS && grant != GrantState.DENY) return
        dataStore.edit { it[keyOf(coordinate, capability)] = grant.name }
    }

    override suspend fun clear(coordinate: String) {
        val prefix = "${scoped(coordinate)}$SEP"
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    override suspend fun all(): Map<String, Map<NappletCapability, GrantState>> {
        val prefs = dataStore.data.first()
        val result = mutableMapOf<String, MutableMap<NappletCapability, GrantState>>()
        val accountPrefix = "${accountPubKey()}$SEP"
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            // Key is "<account><SEP><coordinate><SEP><CAPABILITY>". Only the active account's grants
            // are listed, so the Connected Apps screen never surfaces another account's permissions.
            if (!name.startsWith(accountPrefix)) continue
            val scoped = name.removePrefix(accountPrefix)
            val capName = scoped.substringAfterLast(SEP, "")
            val coordinate = scoped.substringBeforeLast(SEP, "")
            if (capName.isEmpty() || coordinate.isEmpty()) continue
            val capability = runCatching { NappletCapability.valueOf(capName) }.getOrNull() ?: continue
            val grant = runCatching { GrantState.valueOf(value as String) }.getOrNull() ?: continue
            if (grant == GrantState.ALLOW_ALWAYS || grant == GrantState.DENY) {
                result.getOrPut(coordinate) { mutableMapOf() }[capability] = grant
            }
        }
        return result
    }

    override suspend fun remove(
        coordinate: String,
        capability: NappletCapability,
    ) {
        dataStore.edit { it.remove(keyOf(coordinate, capability)) }
    }

    private fun keyOf(
        coordinate: String,
        capability: NappletCapability,
    ) = stringPreferencesKey("${scoped(coordinate)}$SEP${capability.name}")

    companion object {
        private const val SEP = "\u0000"
    }
}

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

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-account persistence for the set of joined `block/buzz` workspaces ([BuzzWorkspaces]), so the
 * app knows which relays to connect + NIP-42-authenticate + run member-channel discovery against on
 * a cold start — Buzz membership is server-side (granted by the HTTP invite claim), with no
 * NIP-51/kind-10009 join event to rebuild the set from.
 *
 * **Per account, not per device.** The set used to be one device-global key shared by every logged-in
 * account, on the reasoning that restoring only marks relays to sync and the relay gates each
 * read/write by the authenticated key anyway. That missed one consumer: the joined set also makes a
 * relay first-party in `AuthCoordinator.isFirstParty`, so one account joining a workspace silently
 * gave *every* other logged-in account an automatic NIP-42 login there — the bystander-account leak
 * the per-account gate exists to prevent. The key is namespaced by pubkey for the same reason the
 * relay-auth overrides moved to a per-account file.
 *
 * Still on the app-wide [sharedPreferencesDataStore] file — the namespacing, not the file, is what
 * separates accounts, and one file avoids a second DataStore per logged-in account.
 *
 * On construction it loads this account's saved relay URLs into [workspaces] (re-normalizing each,
 * dropping any that no longer parse), then mirrors every later change back to disk. Construct once
 * per account, eagerly.
 */
@Stable
class BuzzWorkspaceStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val pubKeyHex: HexKey,
    private val workspaces: BuzzWorkspaces,
) {
    private val key = stringSetPreferencesKey("$KEY_PREFIX$pubKeyHex")

    init {
        scope.launch {
            restoreFromDisk()
            // Persist on every change AFTER the initial restore (drop(1) skips the value present
            // at collection start, which restoreFromDisk already wrote).
            workspaces.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = store.data.first()
            // Fall back to the pre-namespacing device-global key so an upgrade doesn't empty the
            // workspaces hub. That set is whatever any account joined, which is exactly what every
            // account already saw before this became per-account — so seeding from it changes
            // nothing that was true yesterday, and the first join after the upgrade writes to this
            // account's own key and takes over. The legacy key is left in place for the other
            // accounts to seed from; nothing writes it again.
            val raw = prefs[key] ?: prefs[LEGACY_KEY] ?: return
            val relays = raw.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
            if (relays.isNotEmpty()) workspaces.restore(relays)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzWorkspacePrefs") { "Error reading joined workspaces: ${e.message}" }
        }
    }

    private suspend fun persist(relays: Set<NormalizedRelayUrl>) {
        try {
            // Always write the joined set, empty included — never remove the key. An absent key
            // means "never migrated" and re-seeds from the legacy one above, so removing it
            // would undo the user's last removal on the next launch.
            store.edit { prefs ->
                prefs[key] = relays.map { it.url }.toSet()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzWorkspacePrefs") { "Error writing joined workspaces: ${e.message}" }
        }
    }

    companion object {
        private const val KEY_PREFIX = "buzz.joinedWorkspaces."

        /** The device-global key written before the set became per-account; read-only now. */
        private val LEGACY_KEY = stringSetPreferencesKey("buzz.joinedWorkspaces")
    }
}

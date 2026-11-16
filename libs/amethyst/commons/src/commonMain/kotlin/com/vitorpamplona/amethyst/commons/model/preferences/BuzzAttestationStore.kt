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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-account persistence for the NIP-OA attestation this account holds
 * ([BuzzHeldAttestations]), so a held credential survives an app restart instead of needing to be
 * re-pasted. The key is namespaced by pubkey; the store used to be one device-global list because
 * each entry carried the agent key it authorized, which made the file a per-account store with
 * extra steps.
 *
 * On construction it loads this account's saved attestation and mirrors every later change back to
 * disk. Re-verification on restore is no longer done here: [BuzzHeldAttestations.put] verifies
 * against the agent key itself and rejects what fails, so a tampered on-disk credential is dropped
 * by the same gate that rejects a mistyped one. Construct once per account, eagerly.
 */
@Stable
class BuzzAttestationStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val pubKeyHex: HexKey,
    private val attestation: BuzzHeldAttestations,
) {
    private val key = stringPreferencesKey("$KEY_PREFIX$pubKeyHex")

    @Serializable
    private data class Entry(
        val owner: HexKey,
        val conditions: String,
        val sig: HexKey,
    )

    /** The pre-namespacing on-disk shape: one list for the whole device, each entry agent-keyed. */
    @Serializable
    private data class LegacyEntry(
        val agent: HexKey,
        val owner: HexKey,
        val conditions: String,
        val sig: HexKey,
    )

    init {
        scope.launch {
            restoreFromDisk()
            // Persist on every change AFTER the initial restore (drop(1) skips the value
            // present at collection start, which restoreFromDisk already wrote).
            attestation.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = store.data.first()
            // put() verifies, so a credential that no longer checks out is dropped either way.
            restoreFrom(prefs[key], prefs[LEGACY_KEY], pubKeyHex)?.let(attestation::put)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error reading held attestation: ${e.message}" }
        }
    }

    private suspend fun persist(held: OwnerAttestation?) {
        try {
            store.edit { prefs ->
                // Write [NONE] rather than removing the key: removing it is indistinguishable from
                // never having migrated, which would let the legacy list re-seed a credential the
                // user just deleted. See [restoreFrom].
                prefs[key] = if (held == null) NONE else json.encodeToString(Entry(held.ownerPubKey, held.conditions, held.sig))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error writing held attestation: ${e.message}" }
        }
    }

    companion object {
        private const val KEY_PREFIX = "buzz.heldAttestation."

        /** The device-global key written before the store became per-account; read-only now. */
        private val LEGACY_KEY = stringPreferencesKey("buzz.heldAttestations")

        /**
         * Tombstone for "this account has been migrated and holds nothing", which an *absent* key
         * cannot express — absent still means "never migrated" and is allowed to seed from
         * [LEGACY_KEY]. Without it, removing a held attestation lasted only until the next launch,
         * because nothing ever clears the legacy list. (The starred-channel and joined-workspace
         * stores get this for free: they persist an empty *set*, which reads back present.)
         */
        private const val NONE = ""

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Which attestation to reinstate, given this account's saved value and the pre-namespacing
         * device-global list. Pure, so the migration precedence is testable without a store.
         *
         * [saved] wins whenever it is present, [NONE] included. Only a never-migrated account falls
         * back to [legacy], and it takes just the entry issued to its own key — that list was
         * already agent-keyed, so no other account's credential can match. Nothing is verified here;
         * [BuzzHeldAttestations.put] is the gate that rejects a tampered credential.
         */
        internal fun restoreFrom(
            saved: String?,
            legacy: String?,
            agentPubKey: HexKey,
        ): OwnerAttestation? {
            if (saved != null) {
                if (saved == NONE) return null
                val entry = json.decodeFromString<Entry>(saved)
                return OwnerAttestation(entry.owner, entry.conditions, entry.sig)
            }
            val list = legacy ?: return null
            return json
                .decodeFromString<List<LegacyEntry>>(list)
                .firstOrNull { it.agent == agentPubKey }
                ?.let { OwnerAttestation(it.owner, it.conditions, it.sig) }
        }
    }
}

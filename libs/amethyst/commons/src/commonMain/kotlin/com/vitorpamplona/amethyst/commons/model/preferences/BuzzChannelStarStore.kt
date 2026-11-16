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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelStars
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-account persistence for the set of starred Buzz workspace channels ([BuzzChannelStars]), so
 * favorites survive a restart. Mirrors [BuzzWorkspacePreferences] in both shape and reasoning: the
 * key is namespaced by pubkey because a star is personal — it says which channels *this* user wants
 * pinned — and one device-global set meant one account's favorites reordered and badged every other
 * logged-in account's channel list. Loads this account's saved ids into [stars] on construction,
 * then writes every later change back. Construct once per account, eagerly.
 */
@Stable
class BuzzChannelStarStore(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val pubKeyHex: HexKey,
    private val stars: BuzzChannelStars,
) {
    private val key = stringSetPreferencesKey("$KEY_PREFIX$pubKeyHex")

    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already wrote.
            stars.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = store.data.first()
            // Fall back to the pre-namespacing device-global key so an upgrade doesn't unpin
            // everything. That set is what every account already saw; the next toggle writes to this
            // account's own key and takes over. The legacy key is left for other accounts to seed
            // from and is never written again.
            val raw = prefs[key] ?: prefs[LEGACY_KEY] ?: return
            if (raw.isNotEmpty()) stars.restore(raw)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzChannelStarPrefs") { "Error reading starred channels: ${e.message}" }
        }
    }

    private suspend fun persist(ids: Set<String>) {
        try {
            // Always write the starred set, empty included — never remove the key. An absent key
            // means "never migrated" and re-seeds from the legacy one above, so removing it
            // would undo the user's last removal on the next launch.
            store.edit { prefs -> prefs[key] = ids }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzChannelStarPrefs") { "Error writing starred channels: ${e.message}" }
        }
    }

    companion object {
        private const val KEY_PREFIX = "buzz.starredChannels."

        /** The device-global key written before the set became per-account; read-only now. */
        private val LEGACY_KEY = stringSetPreferencesKey("buzz.starredChannels")
    }
}

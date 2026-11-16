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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-nSite network routing: whether a site's traffic goes through **Tor** or over the **open web**. Keyed by the site's coordinate (`author:identifier`), like the permission ledger, so
 * the choice survives across launches and routine code updates.
 *
 * When Tor is active, a site the user never set follows the Tor settings' "Applets and Websites"
 * switch ([defaultUseTor]); the user can set a specific site either way from its window. "Open web"
 * makes *everything* for that site direct: its live web traffic and its blob fetches.
 *
 * The in-memory map is authoritative for the session, so a launch can read it synchronously, with
 * write-through persistence. Until the on-disk choices have loaded every site is on Tor, so a site
 * pinned to Tor never silently starts on the open web.
 */
class NsiteNetworkChoices(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    /**
     * Where a site the user never set starts: the Tor settings' "Applets and Websites" switch. Read
     * only once the stored choices have loaded; until then every site is on Tor.
     */
    private val defaultUseTor: () -> Boolean = { true },
) {
    // coordinate -> useTor. Absent means "never chosen" -> [defaultUseTor].
    private val modes = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val hydration: Job =
        scope.launch {
            val stored =
                dataStore.data
                    .first()
                    .asMap()
                    .entries
                    .associate { (key, value) -> key.name to (value != OPEN_WEB) }
            // Never clobber a choice made in this session before loading finished.
            modes.update { stored + it }
        }

    /**
     * Suspends until the on-disk choices have loaded, so [useTor] reflects what the user picked
     * instead of the bare Tor default. Returns at once afterwards.
     */
    suspend fun awaitReady() {
        hydration.join()
    }

    /**
     * Whether [coordinate] routes through Tor: the user's choice for it, else [defaultUseTor]. Before
     * the stored choices have loaded, Tor: a site pinned to Tor must not start on the open web
     * because its choice wasn't read yet.
     */
    fun useTor(coordinate: String): Boolean = modes.value[coordinate] ?: (!hydration.isCompleted || defaultUseTor())

    /** Records [useTor] for [coordinate] in memory and persists it. */
    fun set(
        coordinate: String,
        useTor: Boolean,
    ) {
        modes.update { it + (coordinate to useTor) }
        scope.launch {
            dataStore.edit { it[stringPreferencesKey(coordinate)] = if (useTor) TOR else OPEN_WEB }
        }
    }

    private companion object {
        const val OPEN_WEB = "OPEN"
        const val TOR = "TOR"
    }
}

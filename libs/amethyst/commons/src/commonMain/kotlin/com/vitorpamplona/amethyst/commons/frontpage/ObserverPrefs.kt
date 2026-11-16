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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Whether an account's editions go to its public Observer site. On by default, but nothing is
 * published until the reader has seen, once, that the site is public ([noticeSeen]): the screen
 * asks before the first print, and an unattended print never publishes before that.
 */
@Immutable
data class ObserverPrefs(
    val publish: Boolean = true,
    val noticeSeen: Boolean = false,
) {
    val publishes get() = publish && noticeSeen
}

interface ObserverPrefsStore {
    fun flow(account: HexKey): Flow<ObserverPrefs>

    suspend fun get(account: HexKey): ObserverPrefs = flow(account).first()

    suspend fun set(
        account: HexKey,
        prefs: ObserverPrefs,
    )

    object None : ObserverPrefsStore {
        override fun flow(account: HexKey): Flow<ObserverPrefs> = flowOf(ObserverPrefs(publish = false))

        override suspend fun set(
            account: HexKey,
            prefs: ObserverPrefs,
        ) {}
    }
}

/** In the app-wide shared settings file: `frontpage.observer.<pubkey>` = `on|seen`. Per device, like the schedule. */
class DataStoreObserverPrefsStore(
    private val stores: AppPreferenceStores,
) : ObserverPrefsStore {
    private val store get() = stores.getDataStore(AppPreferenceStores.SHARED_SETTINGS)

    private fun keyFor(account: HexKey) = stringPreferencesKey(PREFIX + account.lowercase())

    override fun flow(account: HexKey): Flow<ObserverPrefs> = store.data.map { decode(it[keyFor(account)]) }

    override suspend fun set(
        account: HexKey,
        prefs: ObserverPrefs,
    ) {
        store.edit { it[keyFor(account)] = encode(prefs) }
    }

    companion object {
        private const val PREFIX = "frontpage.observer."

        fun encode(prefs: ObserverPrefs) = "${if (prefs.publish) "on" else "off"}|${if (prefs.noticeSeen) "seen" else "new"}"

        fun decode(raw: String?): ObserverPrefs {
            val parts = raw?.split('|') ?: return ObserverPrefs()
            if (parts.size != 2) return ObserverPrefs()
            return ObserverPrefs(publish = parts[0] != "off", noticeSeen = parts[1] == "seen")
        }
    }
}

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

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.first

/**
 * The model the reader picked for each AI endpoint, on this install: the kind 12000 event stores
 * none. The shared choice is now the schedule's `model` param (`ObserverParams`); this is read as
 * the fallback, and moved into the schedule once per account on this install's first edit of it.
 */
interface FrontPageModelMemory {
    suspend fun get(url: String): String?

    suspend fun set(
        url: String,
        model: String,
    )

    /** Whether this install already moved its remembered model into [account]'s schedule. */
    suspend fun isMigrated(account: HexKey): Boolean = false

    suspend fun setMigrated(account: HexKey) {}
}

/** In the app-wide shared settings file, under a `frontpage.model.` prefix per endpoint URL. */
class DataStoreFrontPageModelMemory(
    private val stores: AppPreferenceStores,
) : FrontPageModelMemory {
    private val store get() = stores.getDataStore(AppPreferenceStores.SHARED_SETTINGS)

    private fun keyFor(url: String) = stringPreferencesKey("frontpage.model." + url.trim().trimEnd('/').lowercase())

    override suspend fun get(url: String): String? = store.data.first()[keyFor(url)]

    override suspend fun set(
        url: String,
        model: String,
    ) {
        store.edit { it[keyFor(url)] = model }
    }

    private fun migratedKey(account: HexKey) = booleanPreferencesKey("frontpage.model.migrated." + account.lowercase())

    override suspend fun isMigrated(account: HexKey): Boolean = store.data.first()[migratedKey(account)] == true

    override suspend fun setMigrated(account: HexKey) {
        store.edit { it[migratedKey(account)] = true }
    }
}

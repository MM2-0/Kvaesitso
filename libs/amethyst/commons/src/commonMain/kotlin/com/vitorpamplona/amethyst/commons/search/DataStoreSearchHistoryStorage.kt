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
package com.vitorpamplona.amethyst.commons.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.flow.first

/**
 * [SearchHistoryStorage] on a DataStore of its own — `search_history` under the
 * front end's datastore directory.
 *
 * Takes the store rather than a directory: the search screen is rebuilt per
 * seeded query, and DataStore refuses a second live instance on a path that
 * already has one, so who owns the instance matters. Handing it in means the
 * caller's store holder is the single registry rather than this class keeping a
 * private one of its own.
 */
class DataStoreSearchHistoryStorage(
    private val store: DataStore<Preferences>,
) : SearchHistoryStorage {
    override suspend fun read(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    override suspend fun write(
        key: String,
        value: String?,
    ) {
        store.edit {
            if (value.isNullOrBlank()) it.remove(stringPreferencesKey(key)) else it[stringPreferencesKey(key)] = value
        }
    }

    companion object {
        const val FILE_NAME = "search_history"
    }
}

/** The search history kept in this app's own preference file. */
fun AppPreferenceStores.searchHistoryStorage(): DataStoreSearchHistoryStorage = DataStoreSearchHistoryStorage(getDataStore(DataStoreSearchHistoryStorage.FILE_NAME))

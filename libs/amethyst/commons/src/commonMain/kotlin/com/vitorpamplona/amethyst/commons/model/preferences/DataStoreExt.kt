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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * Exposes one DataStore key as an [UpdatablePropertyFlow].
 *
 * A missing key, a blank serialization and an explicit null all mean the same
 * thing here — the property is absent — so each of them removes the key rather
 * than storing an empty string that would later parse into a bogus value.
 *
 * Uses okio's [IOException] rather than `java.io.IOException`: on JVM targets
 * okio aliases it to exactly that type, so the read-error branch keeps catching
 * what DataStore throws while the file stays compilable for Apple targets.
 */
fun <T> DataStore<Preferences>.getProperty(
    key: Preferences.Key<String>,
    parser: (String) -> T,
    serializer: (T) -> String,
    scope: CoroutineScope,
): UpdatablePropertyFlow<T> =
    UpdatablePropertyFlow(
        flow =
            data
                .catch { e ->
                    if (e is IOException) emit(emptyPreferences()) else throw e
                }.map { prefs ->
                    prefs[key]?.let(parser)
                },
        update = { newValue ->
            val serialized = newValue?.let(serializer)
            if (serialized != null && serialized.isNotBlank()) {
                edit { prefs -> prefs[key] = serialized }
            } else {
                edit { prefs -> prefs.remove(key) }
            }
        },
        scope = scope,
    )

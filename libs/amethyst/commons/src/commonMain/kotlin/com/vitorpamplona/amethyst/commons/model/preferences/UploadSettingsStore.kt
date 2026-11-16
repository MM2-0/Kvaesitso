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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * How this account uploads media: which server, what is stripped, what is
 * mirrored and what is cached locally.
 *
 * Defaults match what the SharedPreferences implementation returned for a
 * missing key, so an account that never touched a setting behaves identically
 * before and after the migration.
 */
data class UploadSettings(
    val stripLocationOnUpload: Boolean = true,
    val optimizeMediaOnUpload: Boolean = false,
    val mirrorUploadsToAllServers: Boolean = true,
    val useLocalBlossomCache: Boolean = true,
    val localBlossomCacheProfilePicturesOnly: Boolean = false,
    val defaultFileServerJson: String? = null,
)

/** Reads and writes [UploadSettings] in the account's DataStore. */
class UploadSettingsStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val stripLocationOnUpload = booleanPreferencesKey("stripLocationOnUpload")
        val optimizeMediaOnUpload = booleanPreferencesKey("optimizeMediaOnUpload")
        val mirrorUploadsToAllServers = booleanPreferencesKey("mirrorUploadsToAllServers")
        val useLocalBlossomCache = booleanPreferencesKey("useLocalBlossomCache")
        val localBlossomCacheProfilePicturesOnly = booleanPreferencesKey("localBlossomCacheProfilePicturesOnly")
        val defaultFileServerJson = stringPreferencesKey("defaultFileServer")

        /** What the `secret_keeper_<npub>` file called these, for the one-shot copy. */
        val legacyTable =
            LegacyKeyTable(
                "migrated.uploadSettings",
                listOf(
                    LegacyBooleanKey("stripLocationOnUpload", stripLocationOnUpload),
                    LegacyBooleanKey("optimizeMediaOnUpload", optimizeMediaOnUpload),
                    LegacyBooleanKey("mirrorUploadsToAllServers", mirrorUploadsToAllServers),
                    LegacyBooleanKey("useLocalBlossomCache", useLocalBlossomCache),
                    LegacyBooleanKey("localBlossomCacheProfilePicturesOnly", localBlossomCacheProfilePicturesOnly),
                    LegacyStringKey("defaultFileServer", defaultFileServerJson),
                ),
            )
    }

    suspend fun load(): UploadSettings {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return UploadSettings(
            stripLocationOnUpload = prefs[stripLocationOnUpload] ?: true,
            optimizeMediaOnUpload = prefs[optimizeMediaOnUpload] ?: false,
            mirrorUploadsToAllServers = prefs[mirrorUploadsToAllServers] ?: true,
            useLocalBlossomCache = prefs[useLocalBlossomCache] ?: true,
            localBlossomCacheProfilePicturesOnly = prefs[localBlossomCacheProfilePicturesOnly] ?: false,
            defaultFileServerJson = prefs[defaultFileServerJson],
        )
    }

    /** Writes the whole group in one edit, so a crash cannot half-apply it. */
    suspend fun save(value: UploadSettings) {
        store.edit { prefs ->
            prefs[stripLocationOnUpload] = value.stripLocationOnUpload
            prefs[optimizeMediaOnUpload] = value.optimizeMediaOnUpload
            prefs[mirrorUploadsToAllServers] = value.mirrorUploadsToAllServers
            prefs[useLocalBlossomCache] = value.useLocalBlossomCache
            prefs[localBlossomCacheProfilePicturesOnly] = value.localBlossomCacheProfilePicturesOnly
            value.defaultFileServerJson.let { if (it != null) prefs[defaultFileServerJson] = it else prefs.remove(defaultFileServerJson) }
        }
    }
}

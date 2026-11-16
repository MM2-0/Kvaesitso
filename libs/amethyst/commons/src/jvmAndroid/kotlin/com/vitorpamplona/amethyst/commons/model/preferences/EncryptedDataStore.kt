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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import okio.IOException
import kotlin.io.encoding.Base64

/**
 * A DataStore whose values are encrypted with [SecretEncryption] and stored
 * Base64-encoded, for anything that must not sit in plaintext on disk.
 *
 * Keys stay in the clear — only values are encrypted — so the set of keys an
 * account has is visible even though their contents are not.
 */
class EncryptedDataStore(
    private val store: DataStore<Preferences>,
    private val encryption: SecretEncryption = sharedSecretEncryption,
    private val scope: CoroutineScope,
) {
    private fun encrypt(value: String): String = Base64.encode(encryption.encrypt(value.encodeToByteArray()))

    private fun decrypt(value: String): String? = encryption.decrypt(Base64.decode(value))?.decodeToString()

    suspend fun remove(key: Preferences.Key<String>) {
        store.edit { prefs -> prefs.remove(key) }
    }

    suspend fun save(
        key: Preferences.Key<String>,
        value: String,
    ) {
        store.edit { prefs -> prefs[key] = encrypt(value) }
    }

    /**
     * The value, or null when the key is absent **or unreadable**.
     *
     * A read error is reported as absence, which is what most callers want.
     * Anything that must not mistake a failure for an empty store — a probe
     * deciding whether to create a replacement key, say — needs [getOrThrow].
     */
    suspend fun get(key: Preferences.Key<String>): String? =
        store.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.firstOrNull()
            ?.get(key)
            ?.let { decrypt(it) }

    /**
     * Whether the key is present, without decrypting it.
     *
     * For callers that only need presence — deleting, say. [get] would report a
     * value it cannot decrypt as absent, which is the wrong answer when the
     * decision being made is whether to remove it.
     */
    suspend fun contains(key: Preferences.Key<String>): Boolean =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .firstOrNull()
            ?.contains(key) == true

    /**
     * The value, or null only when the key is genuinely absent.
     *
     * Unlike [get], a failure to read propagates rather than being flattened
     * into null. The difference matters wherever null means "nothing was ever
     * stored" and the caller acts on that — overwriting a key that is present
     * but temporarily unreadable is not recoverable.
     */
    suspend fun getOrThrow(key: Preferences.Key<String>): String? = store.data.first()[key]?.let { decrypt(it) }

    /**
     * Reads or writes several keys against one snapshot of the store.
     *
     * A key at a time costs a full DataStore round trip each — a transform, a
     * serialize, a temp-file write, an fsync and a rename to save; a fresh flow
     * collection to read. Worse for writes, AES-GCM re-randomises the IV, so the
     * ciphertext differs every time and DataStore's "value unchanged, skip the
     * write" shortcut never fires: all of them always reach disk.
     *
     * One [edit] is also a single transaction, which is what lets a group be
     * written with its own migration marker and never be seen half-applied.
     */
    suspend fun edit(block: Editor.() -> Unit) {
        store.edit { prefs -> Editor(prefs, ::encrypt).block() }
    }

    class Editor internal constructor(
        private val prefs: MutablePreferences,
        private val encrypt: (String) -> String,
    ) {
        fun put(
            key: Preferences.Key<String>,
            value: String,
        ) {
            prefs[key] = encrypt(value)
        }

        fun remove(key: Preferences.Key<String>) {
            prefs.remove(key)
        }

        fun putOrRemove(
            key: Preferences.Key<String>,
            value: String?,
        ) {
            if (value != null) put(key, value) else remove(key)
        }
    }

    /**
     * One snapshot of the store, decrypting on access.
     *
     * Reads every key of a group against the same collection, and — since the
     * values are one atomic write — against the same version of it.
     */
    suspend fun snapshot(): Snapshot =
        Snapshot(
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .firstOrNull() ?: emptyPreferences(),
            ::decrypt,
        )

    class Snapshot internal constructor(
        private val prefs: Preferences,
        private val decrypt: (String) -> String?,
    ) {
        operator fun get(key: Preferences.Key<String>): String? = prefs[key]?.let(decrypt)
    }

    fun <T> getProperty(
        key: Preferences.Key<String>,
        parser: (String) -> T,
        serializer: (T) -> String,
    ): UpdatablePropertyFlow<T> =
        UpdatablePropertyFlow(
            flow =
                store.data
                    .catch { e ->
                        if (e is IOException) emit(emptyPreferences()) else throw e
                    }.map { prefs ->
                        prefs[key]?.let { decrypt(it) }?.takeIf { it.isNotBlank() }?.let(parser)
                    },
            update = { newValue ->
                val serialized = newValue?.let(serializer)
                if (serialized != null && serialized.isNotBlank()) {
                    save(key, serialized)
                } else {
                    remove(key)
                }
            },
            scope = scope,
        )
}

/**
 * The one [SecretEncryption] every encrypted store shares.
 *
 * Its constructor loads the AndroidKeyStore and its first use probes the key's
 * security level, and each instance keeps its own per-thread Cipher cache — all
 * for a single key alias. There were eight instances doing that independently.
 * Both actuals are documented as safe for concurrent use, so one will do.
 */
internal val sharedSecretEncryption: SecretEncryption by lazy { SecretEncryption() }

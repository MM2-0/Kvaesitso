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
package com.vitorpamplona.amethyst.commons.keystorage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.EncryptedDataStore
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Android implementation of [SecureKeyStorage]: an encrypted DataStore whose
 * values are sealed with a key held in the AndroidKeyStore.
 *
 * ## Why not EncryptedSharedPreferences
 *
 * This used to be `androidx.security.crypto`, which Google deprecated with no
 * drop-in successor. [SecretEncryption] talks to the AndroidKeyStore directly —
 * AES-256-GCM, StrongBox-backed where the device offers it — so the key still
 * never enters app memory, and the library goes away.
 *
 * Nothing is migrated from the old `amethyst_secure_keys` file because nothing
 * ever wrote to it: this class is used by the desktop app, and the Android app
 * has its own key storage in LocalPreferences. Were that to change, a migration
 * would have to come first.
 *
 * ## Security note
 *
 * Only values are encrypted; the key names are not. That reveals which npubs
 * this installation holds keys for, but not the keys themselves — the same
 * trade-off the rest of the encrypted stores make.
 *
 * The String memory limitation described on [SecureKeyStorage] still applies:
 * a decrypted private key cannot be zeroed from a JVM String.
 */
actual class SecureKeyStorage private actual constructor() {
    actual companion object {
        private const val STORE_FILE = "datastore/secure_keys.preferences_pb"
        private const val KEY_PREFIX = "privkey_"

        private lateinit var appContext: Context

        actual fun create(context: Any?): SecureKeyStorage {
            require(context is Context) { "Android requires a valid Context" }
            appContext = context.applicationContext
            return SecureKeyStorage()
        }

        /**
         * One scope and one store for the whole process, not one per instance.
         *
         * [create] hands out a new [SecureKeyStorage] on every call — harmless
         * when the store was `EncryptedSharedPreferences.create`, which is
         * idempotent, but DataStore keeps a process-wide registry keyed by file
         * path and only releases an entry when the owning scope ends. A
         * per-instance store over a fixed path meant the second instance threw
         * "multiple DataStores active for the same file" on its first read —
         * which, for this store, reads as the account having no private key.
         */
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val sharedStore by lazy {
            EncryptedDataStore(
                PreferenceDataStoreFactory.createWithPath(
                    scope = scope,
                    produceFile = { File(appContext.filesDir, STORE_FILE).toOkioPath() },
                ),
                scope = scope,
            )
        }
    }

    private val store get() = sharedStore

    private fun keyFor(npub: String) = stringPreferencesKey(KEY_PREFIX + npub)

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ) {
        try {
            store.save(keyFor(npub), privKeyHex)
        } catch (e: Exception) {
            throw SecureStorageException("Failed to save private key", e)
        }
    }

    actual suspend fun getPrivateKey(npub: String): String? =
        try {
            store.get(keyFor(npub))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw SecureStorageException("Failed to retrieve private key", e)
        }

    /**
     * Unlike [getPrivateKey], this reads through [EncryptedDataStore.getOrThrow]
     * so a store that cannot be read raises instead of reporting the key as
     * absent. That distinction is the whole point of this method: callers use
     * it to decide whether a key needs creating, and treating a transient read
     * failure as "no key here" would overwrite a live one.
     */
    actual suspend fun getPrivateKeyOrThrow(npub: String): String? =
        try {
            store.getOrThrow(keyFor(npub))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw SecureStorageException("Failed to retrieve private key", e)
        }

    /**
     * Removes the key unconditionally, and reports whether one was there.
     *
     * The presence test deliberately does not decrypt. Gating the removal on a
     * successful decrypting read meant a rotated or wiped AndroidKeyStore —
     * exactly when the value is unreadable — skipped the delete, leaving the
     * private key of a deleted account on disk.
     */
    actual suspend fun deletePrivateKey(npub: String): Boolean =
        try {
            val existed = store.contains(keyFor(npub))
            store.remove(keyFor(npub))
            existed
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw SecureStorageException("Failed to delete private key", e)
        }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKey(npub) != null
}

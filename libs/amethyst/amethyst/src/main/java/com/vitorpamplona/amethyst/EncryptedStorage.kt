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
package com.vitorpamplona.amethyst

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The legacy encrypted preference files, and a permanent read-only migration
 * source.
 *
 * # This class cannot be deleted
 *
 * Every migration in the preference layer is *lazy*: it reads the legacy store
 * at the moment it runs, not when the app is installed. Nine of them come
 * through here — the seven CopyOnceMigrations under LocalPreferences plus the
 * key, secret and roster stores.
 *
 * So deleting this class does not only affect installs that have not upgraded
 * yet. It strands anyone who **skips** the release that introduced the new
 * stores: they move from a pre-migration build straight to a post-deletion one,
 * the migration runs against a reader that no longer exists, and their keys,
 * accounts, wallets and settings sit encrypted on disk with nothing able to
 * read them. The app opens as a fresh install. That is not rare — auto-update
 * off, the F-Droid cadence, or a restore from backup all skip releases.
 *
 * What *can* go, once the new path has shipped and held, is the legacy
 * **writes**. Dropping those stops new data landing here while this stays able
 * to read what is already here. The `androidx.security.crypto` dependency has
 * to stay for as long as this does; it is an unmaintained-library risk rather
 * than an active vulnerability, and a far smaller cost than stranding users.
 *
 * # Before any legacy file is deleted
 *
 * Deletion is only safe for an account whose every key has been migrated, and
 * that is not yet true — see `amethyst/plans/2026-09-23-encrypted-storage-retirement.md`
 * for what is still outstanding. NOSTR_PUBKEY is the one to watch: without it
 * `loadAccountConfigFromEncryptedStorage` returns null and the account
 * disappears whether or not its private key survived.
 */
class EncryptedStorage {
    companion object {
        private const val PREFERENCES_NAME = "secret_keeper"

        // returns the preferences for each account or a global file if null.
        fun prefsFileName(npub: String? = null): String = if (npub == null) PREFERENCES_NAME else "${PREFERENCES_NAME}_$npub"

        // androidx.security.crypto is deprecated with no drop-in successor; migrating the
        // on-disk key store is a separate, security-sensitive effort.
        @Suppress("DEPRECATION")
        fun preferences(
            applicationContext: Context,
            npub: String? = null,
        ): EncryptedSharedPreferences {
            val masterKey: MasterKey =
                MasterKey
                    .Builder(applicationContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            return EncryptedSharedPreferences.create(
                applicationContext,
                prefsFileName(npub),
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as EncryptedSharedPreferences
        }
    }
}

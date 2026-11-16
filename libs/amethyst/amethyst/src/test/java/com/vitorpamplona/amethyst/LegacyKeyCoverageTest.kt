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

import com.vitorpamplona.amethyst.commons.model.preferences.LegacyAccountSecretNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Every key the app has ever written to a `secret_keeper` file has to be
 * accounted for somewhere, and this is what says so.
 *
 * [LegacyPreferenceCleanup] refuses to delete a file holding a key it does not
 * recognise, which is the right runtime behaviour but a slow way to find out.
 * A key added to `PrefKeys` and to neither a migration table nor the accepted
 * list fails here instead — at the commit that adds it, naming it.
 */
class LegacyKeyCoverageTest {
    /** Read off the object rather than restated, so the test cannot go stale. */
    private val allPrefKeys: Set<String> =
        PrefKeys::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map {
                it.isAccessible = true
                it.get(null) as String
            }.toSet()

    /**
     * Keys of the *global* `secret_keeper` file, which has no per-account
     * counterpart and is not what the cleanup deletes.
     * `notification_service_enabled` is not even in it — it lives in a plain
     * file, deliberately, because it is read synchronously in fresh processes.
     */
    private val globalFileKeys =
        setOf(
            PrefKeys.CURRENT_ACCOUNT,
            PrefKeys.SAVED_ACCOUNTS,
            PrefKeys.ALL_ACCOUNT_INFO,
            PrefKeys.SHARED_SETTINGS,
            PrefKeys.NOTIFICATION_SERVICE_ENABLED,
        )

    @Test
    fun thePrefKeysListWasActuallyRead() {
        assertTrue(allPrefKeys.size.toString(), allPrefKeys.size > 100)
        assertTrue(PrefKeys.NOSTR_PUBKEY in allPrefKeys)
    }

    @Test
    fun everyLegacyKeyIsEitherMigratedOrDeliberatelyDropped() {
        val migrated = LegacyAccountKeys.tables.flatMapTo(mutableSetOf()) { it.legacyNames }
        val classified = migrated + LegacyAccountSecretNames.all + LegacyAccountKeys.accepted + globalFileKeys

        assertEquals(
            "Unclassified legacy keys. Add each to a migration table, or to LegacyAccountKeys.accepted if losing it is deliberate.",
            emptySet<String>(),
            allPrefKeys - classified,
        )
    }

    /**
     * The reverse direction: a table claiming a key `PrefKeys` no longer has
     * means the copy is reading a name nothing writes.
     */
    @Test
    fun noTableClaimsAKeyThatNoLongerExists() {
        val migrated = LegacyAccountKeys.tables.flatMapTo(mutableSetOf()) { it.legacyNames }

        assertEquals(emptySet<String>(), migrated - allPrefKeys)
        assertEquals(emptySet<String>(), LegacyAccountSecretNames.all - allPrefKeys)
        assertEquals(emptySet<String>(), LegacyAccountKeys.accepted - allPrefKeys)
    }

    /**
     * The seven that were still read only from the legacy file. Named
     * individually because `nostr_pubkey` is the one whose loss empties the
     * app: without it the loader returns null and the account disappears, with
     * its private key sitting safe and unreachable in the key store.
     */
    @Test
    fun theLastSevenKeysAreMigrated() {
        val migrated = LegacyAccountKeys.tables.flatMapTo(mutableSetOf()) { it.legacyNames }

        listOf(
            PrefKeys.NOSTR_PUBKEY,
            PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER,
            PrefKeys.SIGNER_PACKAGE_NAME,
            PrefKeys.HAS_BACKED_UP_KEYS,
            PrefKeys.LOCAL_RELAY_SERVERS,
            PrefKeys.OPEN_BACKUP_CONFLICTS,
        ).forEach { assertTrue(it, it in migrated) }

        // The seventh is global, and moved into the UI settings DataStore
        // rather than a per-account one.
        assertTrue(PrefKeys.SHARED_SETTINGS in globalFileKeys)
    }
}

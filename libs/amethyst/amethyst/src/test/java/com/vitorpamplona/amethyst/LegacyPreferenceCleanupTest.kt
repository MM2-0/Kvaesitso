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

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyBooleanKey
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyKeyTable
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyPreferenceSource
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyStringKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NPUB = "npub1test"

private class MapSource(
    private val values: Map<String, Any>,
) : LegacyPreferenceSource {
    override fun keys() = values.keys

    override fun getBoolean(name: String) = values[name] as Boolean?

    override fun getString(name: String) = values[name] as String?

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(name: String) = values[name] as Set<String>?
}

private class FakeFiles(
    private val values: Map<String, Any>,
    private val geohashValues: Map<String, Any> = emptyMap(),
) : LegacyAccountFiles {
    var deleted = false
        private set

    /** The `secret_keeper_<pubkey hex>` file goes with the account's own. */
    var deletedGeohash = false
        private set

    var present = true

    /**
     * Tracked apart from [present]: the two are different files, and the gate
     * has to stay reachable while only one of them is left.
     */
    var geohashPresent = true

    override fun source(npub: String) = MapSource(values)

    override fun geohashSource(npub: String) = MapSource(geohashValues)

    override fun exists(npub: String) = present || geohashPresent

    override suspend fun delete(npub: String): Boolean {
        deleted = true
        deletedGeohash = true
        present = false
        geohashPresent = false
        return true
    }
}

private class FakeSecrets(
    private val stored: AccountSecrets? = AccountSecrets(),
    private val key: String? = null,
    private val throws: Boolean = false,
    private val geohash: GeohashIdentitySecrets? = GeohashIdentitySecrets(),
) : MigratedSecrets {
    override suspend fun secrets(npub: String): AccountSecrets? {
        if (throws) throw IllegalStateException("keystore unavailable")
        return stored
    }

    override suspend fun privateKey(npub: String): String? {
        if (throws) throw IllegalStateException("keystore unavailable")
        return key
    }

    override suspend fun geohashIdentity(npub: String): GeohashIdentitySecrets? {
        if (throws) throw IllegalStateException("keystore unavailable")
        return geohash
    }
}

/**
 * The gate in front of deleting an account's `secret_keeper_<npub>` file.
 *
 * Every test here is a way the deletion could destroy something, so the
 * assertions are mostly that it did *not* happen.
 */
class LegacyPreferenceCleanupTest {
    private val flag = booleanPreferencesKey("flag")
    private val text = stringPreferencesKey("text")

    private val table =
        LegacyKeyTable(
            "migrated.group",
            listOf(LegacyBooleanKey("legacy_flag", flag), LegacyStringKey("legacy_text", text)),
        )

    private val migrated = mutablePreferencesOf().also { it[booleanPreferencesKey("migrated.group")] = true }

    private fun cleanup(
        values: Map<String, Any>,
        current: Preferences = migrated,
        secrets: MigratedSecrets = FakeSecrets(),
        files: FakeFiles = FakeFiles(values),
        retired: Boolean = true,
        accepted: Set<String> = setOf("pending_attestations"),
    ) = files to
        LegacyPreferenceCleanup(
            tables = listOf(table),
            accepted = accepted,
            files = files,
            currentStore = { current },
            secrets = secrets,
            legacyWritesRetired = retired,
        )

    @Test
    fun deletesOnceEverythingIsAccountedFor() =
        runTest {
            val (files, subject) = cleanup(mapOf("legacy_flag" to true, "pending_attestations" to "[]"))

            assertEquals(emptyList<String>(), subject.verify(NPUB))
            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue(files.deleted)
        }

    /**
     * The hole a hand-maintained checklist leaves: a key added later that no
     * migration carries. The check runs from the file's own keys so that it
     * cannot be missed.
     */
    @Test
    fun anUnrecognisedKeyStopsTheDeletion() =
        runTest {
            val (files, subject) = cleanup(mapOf("legacy_flag" to true, "something_new" to "value"))

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(LegacyCleanupResult.Kept(listOf("no migration claims 'something_new'")), result)
            assertTrue(!files.deleted)
        }

    @Test
    fun aCopyThatHasNotRunStopsTheDeletion() =
        runTest {
            val (files, subject) = cleanup(mapOf("legacy_flag" to true), current = emptyPreferences())

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(LegacyCleanupResult.Kept(listOf("the 'migrated.group' copy has not run")), result)
            assertTrue(!files.deleted)
        }

    /**
     * A file that never held a group's keys has nothing for that copy to prove,
     * so an account predating a setting is not held back by it forever.
     */
    @Test
    fun aGroupTheFileNeverHeldDoesNotBlock() =
        runTest {
            val (_, subject) = cleanup(mapOf("pending_attestations" to "[]"), current = emptyPreferences())

            assertEquals(emptyList<String>(), subject.verify(NPUB))
        }

    @Test
    fun secretsThatHaveNotBeenCopiedStopTheDeletion() =
        runTest {
            val (files, subject) = cleanup(mapOf("legacy_flag" to true), secrets = FakeSecrets(stored = null))

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(LegacyCleanupResult.Kept(listOf("the secrets have not been copied across")), result)
            assertTrue(!files.deleted)
        }

    /**
     * A migrated secrets group that has since moved on from the legacy file
     * must not block deletion.
     *
     * This check only ever runs in the release that stopped writing the legacy
     * file, so from then on that copy is frozen while the live one keeps
     * changing. Comparing the two would mean any account that re-pairs a bunker
     * or adds a wallet after upgrading never gets its file deleted. The gate is
     * the migration marker, which is what a non-null read reports.
     */
    @Test
    fun secretsThatHaveMovedOnSinceTheCopyDoNotBlock() =
        runTest {
            val (files, subject) =
                cleanup(
                    mapOf("legacy_flag" to true, "nip46BunkerSecret" to "what-the-file-still-says"),
                    secrets = FakeSecrets(stored = AccountSecrets(nip46BunkerSecret = "re-paired since")),
                )

            assertEquals(emptyList<String>(), subject.verify(NPUB))
            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue(files.deleted)
        }

    @Test
    fun aPrivateKeyThatHasNotBeenCopiedStopsTheDeletion() =
        runTest {
            val (files, subject) =
                cleanup(
                    mapOf("nostr_privkey" to "abc123"),
                    secrets = FakeSecrets(key = null),
                )

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(LegacyCleanupResult.Kept(listOf("the private key has not been copied across")), result)
            assertTrue(!files.deleted)
        }

    @Test
    fun aPrivateKeyThatDisagreesStopsTheDeletion() =
        runTest {
            val (_, subject) = cleanup(mapOf("nostr_privkey" to "abc123"), secrets = FakeSecrets(key = "def456"))

            assertEquals(listOf("the stored private key differs from the legacy file"), subject.verify(NPUB))
        }

    @Test
    fun aMatchingPrivateKeyPasses() =
        runTest {
            val (_, subject) = cleanup(mapOf("nostr_privkey" to "abc123"), secrets = FakeSecrets(key = "abc123"))

            assertEquals(emptyList<String>(), subject.verify(NPUB))
        }

    /**
     * An external-signer account has no private key in either store, and must
     * not be held back for the one it never had.
     */
    @Test
    fun anAccountWithNoPrivateKeyIsNotHeldBack() =
        runTest {
            val (_, subject) = cleanup(mapOf("legacy_flag" to true), secrets = FakeSecrets(key = null))

            assertEquals(emptyList<String>(), subject.verify(NPUB))
        }

    /** "The check itself failed" is not "the check passed". */
    @Test
    fun aStoreThatCannotBeReadStopsTheDeletion() =
        runTest {
            val (files, subject) = cleanup(mapOf("nostr_privkey" to "abc123"), secrets = FakeSecrets(throws = true))

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(
                LegacyCleanupResult.Kept(listOf("the secrets store could not be read", "the key store could not be read")),
                result,
            )
            assertTrue(!files.deleted)
        }

    /**
     * While the app still mirrors into this file, deleting it achieves nothing
     * — the next save recreates it — and would look like it had worked.
     */
    @Test
    fun nothingIsDeletedWhileTheLegacyFileIsStillWritten() =
        runTest {
            val (files, subject) = cleanup(mapOf("legacy_flag" to true), retired = false)

            val result = subject.deleteIfVerified(NPUB)

            assertEquals(LegacyCleanupResult.Kept(listOf(LegacyPreferenceCleanup.STILL_WRITTEN)), result)
            assertTrue(!files.deleted)
        }

    /** Neither file — the account's own nor the location-chat one. */
    @Test
    fun anAccountWithNoLegacyFileIsAlreadyDone() =
        runTest {
            val files =
                FakeFiles(emptyMap()).also {
                    it.present = false
                    it.geohashPresent = false
                }
            val (_, subject) = cleanup(emptyMap(), files = files)

            assertEquals(LegacyCleanupResult.NothingToDelete, subject.deleteIfVerified(NPUB))
        }

    /** Every reason is reported, so one fix does not merely reveal the next. */
    @Test
    fun everyReasonIsReportedAtOnce() =
        runTest {
            val (_, subject) =
                cleanup(
                    mapOf("legacy_flag" to true, "mystery" to "x", "nostr_privkey" to "abc123"),
                    current = emptyPreferences(),
                    secrets = FakeSecrets(stored = null, key = null),
                )

            assertEquals(
                listOf(
                    "no migration claims 'mystery'",
                    "the 'migrated.group' copy has not run",
                    "the secrets have not been copied across",
                    "the private key has not been copied across",
                ),
                subject.verify(NPUB),
            )
        }
    // ── the location-chat identity's own legacy file ──────────────────

    /**
     * The seed is in `secret_keeper_<pubkey hex>`, and [delete] removes that
     * file too. So the gate has to refuse while it holds something the current
     * store does not — otherwise every geohash identity the account has would
     * change on the next launch.
     */
    @Test
    fun anUncopiedLocationChatIdentityBlocksDeletion() =
        runTest {
            val (files, subject) =
                cleanup(
                    values = emptyMap(),
                    files = FakeFiles(emptyMap(), mapOf("geohash_chat_device_seed" to "a".repeat(64))),
                    secrets = FakeSecrets(geohash = null),
                )

            val result = subject.deleteIfVerified(NPUB)

            assertTrue(result is LegacyCleanupResult.Kept)
            assertTrue(
                "was ${(result as LegacyCleanupResult.Kept).reasons}",
                result.reasons.any { it.contains("location-chat identity") },
            )
            assertTrue(!files.deleted)
        }

    /** Copied across: nothing to lose, so it must not block. */
    @Test
    fun aCopiedLocationChatIdentityDoesNotBlockDeletion() =
        runTest {
            val (files, subject) =
                cleanup(
                    values = emptyMap(),
                    files = FakeFiles(emptyMap(), mapOf("geohash_chat_device_seed" to "a".repeat(64))),
                    secrets = FakeSecrets(geohash = GeohashIdentitySecrets(deviceSeed = "a".repeat(64))),
                )

            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue(files.deleted)
        }

    /**
     * An account that never opened a location chat holds neither key. That is a
     * real answer, not "not migrated", and must not hold the file hostage.
     */
    @Test
    fun anAccountWithNoLocationChatIdentityIsNotBlocked() =
        runTest {
            val (files, subject) =
                cleanup(
                    values = emptyMap(),
                    files = FakeFiles(emptyMap(), emptyMap()),
                    secrets = FakeSecrets(geohash = null),
                )

            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue(files.deleted)
        }

    /**
     * Both files go, or the hex one is an orphan nothing will ever remove —
     * the whole reason it is wired into this gate.
     */
    @Test
    fun deletingTheAccountFileAlsoRemovesTheLocationChatFile() =
        runTest {
            val (files, subject) = cleanup(values = emptyMap())

            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue("the hex-keyed file must be deleted with the account's own", files.deletedGeohash)
        }

    /**
     * Once the npub file is gone, the hex-keyed one is all that is left — and
     * it still has to be removable. Gating on the account file alone returned
     * NothingToDelete and stranded it forever.
     */
    @Test
    fun theGateStillRunsWhenOnlyTheLocationChatFileIsLeft() =
        runTest {
            val files = FakeFiles(emptyMap(), mapOf("geohash_chat_nickname" to "vitor"))
            files.present = false
            val (_, subject) =
                cleanup(
                    values = emptyMap(),
                    files = files,
                    secrets = FakeSecrets(geohash = GeohashIdentitySecrets(nickname = "vitor")),
                )

            assertEquals(LegacyCleanupResult.Deleted, subject.deleteIfVerified(NPUB))
            assertTrue(files.deletedGeohash)
        }
}

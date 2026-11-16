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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The per-account secrets group: NIP-46 bunker material and wallet strings.
 *
 * These are encrypted at rest and reached through string keys, so a booleans
 * and a set have to survive an encode/decode they did not before — that is
 * what most of this covers.
 */
class AccountSecretsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun stores(): AccountSecretsEncryptedStores {
        val root = File(folder.root, "acct_${seq++}").apply { mkdirs() }
        return AccountSecretsEncryptedStores(
            rootFilesDir = { root.toOkioPath() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            encryption = SecretEncryption(File(root, "secret.key")),
        )
    }

    private val npub = "npub1abc"

    private val filled =
        AccountSecrets(
            nip46SignerEnabled = true,
            nip46BunkerSecret = "bunker-secret",
            nip46TransportKey = "transport-key",
            nip46SeenRequestIds = setOf("aa11", "bb22", "cc33"),
            nwcWalletsJson = """[{"uri":"nostr+walletconnect://x"}]""",
            clinkDebitWalletsJson = """[{"id":"debit1"}]""",
            defaultPaymentSourceId = "source-1",
        )

    /**
     * Absent must read as null, not as a default-filled group: the caller uses
     * null to decide whether to run the one-off copy out of the legacy file.
     */
    @Test
    fun anUnmigratedAccountReadsAsNull() =
        runTest {
            assertNull(stores().loadSecrets(npub))
        }

    @Test
    fun theGroupRoundTrips() =
        runTest {
            val subject = stores()

            subject.saveSecrets(npub, filled)

            assertEquals(filled, subject.loadSecrets(npub))
        }

    /** An account that genuinely holds nothing must still read as migrated, not as null forever. */
    @Test
    fun anEmptyGroupStillCountsAsMigrated() =
        runTest {
            val subject = stores()

            subject.saveSecrets(npub, AccountSecrets())

            assertEquals(AccountSecrets(), subject.loadSecrets(npub))
        }

    /** The boolean and the set go through a string encoding that did not exist before. */
    @Test
    fun theBooleanAndSetSurviveEncoding() =
        runTest {
            val subject = stores()

            subject.saveSecrets(npub, AccountSecrets(nip46SignerEnabled = true, nip46SeenRequestIds = setOf("a", "b")))
            val loaded = subject.loadSecrets(npub)!!

            assertTrue(loaded.nip46SignerEnabled)
            assertEquals(setOf("a", "b"), loaded.nip46SeenRequestIds)
        }

    @Test
    fun anEmptySetDoesNotBecomeASetHoldingAnEmptyString() =
        runTest {
            val subject = stores()

            subject.saveSecrets(npub, AccountSecrets(nip46SeenRequestIds = emptySet()))

            assertTrue(subject.loadSecrets(npub)!!.nip46SeenRequestIds.isEmpty())
        }

    /** Clearing a wallet must remove it, not leave the previous JSON behind. */
    @Test
    fun aClearedWalletIsRemoved() =
        runTest {
            val subject = stores()
            subject.saveSecrets(npub, filled)

            subject.saveSecrets(npub, filled.copy(nwcWalletsJson = null, defaultPaymentSourceId = null))
            val loaded = subject.loadSecrets(npub)!!

            assertNull(loaded.nwcWalletsJson)
            assertNull(loaded.defaultPaymentSourceId)
            assertEquals("siblings untouched", filled.clinkDebitWalletsJson, loaded.clinkDebitWalletsJson)
        }

    @Test
    fun accountsAreIsolated() =
        runTest {
            val subject = stores()

            subject.saveSecrets("npub1aaa", filled)

            assertNull(subject.loadSecrets("npub1bbb"))
        }

    /** The values must not be sitting in the store file in the clear. */
    @Test
    fun secretsAreNotStoredInPlaintext() =
        runTest {
            val root = File(folder.root, "plain").apply { mkdirs() }
            val subject =
                AccountSecretsEncryptedStores(
                    rootFilesDir = { root.toOkioPath() },
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    encryption = SecretEncryption(File(root, "secret.key")),
                )

            subject.saveSecrets(npub, filled)

            val onDisk =
                subject
                    .file(npub)
                    .toFile()
                    .readBytes()
                    .decodeToString()
            assertFalse("the bunker secret must not be readable", onDisk.contains("bunker-secret"))
            assertFalse("the wallet string must not be readable", onDisk.contains("nostr+walletconnect"))
        }

    @Test
    fun removingAnAccountDropsItsSecrets() =
        runTest {
            val subject = stores()
            subject.saveSecrets(npub, filled)
            assertNotNull(subject.loadSecrets(npub))

            subject.removeAccount(npub)

            assertNull(subject.loadSecrets(npub))
        }

    /**
     * Delete an account, then add the same npub back.
     *
     * DataStore keeps a process-wide registry keyed by file path and releases
     * an entry only when the owning scope's job *completes* — cancelling it is
     * just the request. [AccountSecretsEncryptedStores.removeAccount] waits for
     * that, and without the wait this throws "multiple DataStores active for
     * the same file" whenever the next open wins the race, which on a loaded
     * machine it does.
     */
    @Test
    fun anAccountCanBeAddedBackAfterBeingRemoved() =
        runTest {
            val subject = stores()
            subject.saveSecrets(npub, filled)
            subject.removeAccount(npub)

            subject.saveSecrets(npub, filled)

            assertEquals(filled, subject.loadSecrets(npub))
        }
    // ── the location-chat identity ────────────────────────────────────

    @Test
    fun anUnmigratedGeohashIdentityReadsAsNull() =
        runTest {
            assertNull(stores().loadGeohashIdentity(npub))
        }

    @Test
    fun theGeohashIdentityRoundTrips() =
        runTest {
            val subject = stores()
            val value = GeohashIdentitySecrets(deviceSeed = "a".repeat(64), nickname = "vitor")

            subject.saveGeohashIdentity(npub, value)

            assertEquals(value, subject.loadGeohashIdentity(npub))
        }

    /**
     * An account that opened a location chat under a bunker signer has a seed but
     * never set a handle. Absent must come back absent rather than as "".
     */
    @Test
    fun aSeedWithNoNicknameRoundTrips() =
        runTest {
            val subject = stores()
            val value = GeohashIdentitySecrets(deviceSeed = "b".repeat(64), nickname = null)

            subject.saveGeohashIdentity(npub, value)

            assertEquals(value, subject.loadGeohashIdentity(npub))
        }

    /** An account that holds neither key still counts as migrated, or the copy runs forever. */
    @Test
    fun anEmptyGeohashIdentityStillCountsAsMigrated() =
        runTest {
            val subject = stores()

            subject.saveGeohashIdentity(npub, GeohashIdentitySecrets())

            assertEquals(GeohashIdentitySecrets(), subject.loadGeohashIdentity(npub))
        }

    /**
     * The two groups migrate out of *different* legacy files — the secrets from
     * `secret_keeper_<npub>`, the identity from `secret_keeper_<pubkey hex>` —
     * so neither marker may stand in for the other. Saving one must leave the
     * other reading as not-yet-copied.
     */
    @Test
    fun theTwoGroupsMigrateIndependently() =
        runTest {
            val subject = stores()

            subject.saveSecrets(npub, filled)

            assertNull("saving the secrets must not mark the identity migrated", subject.loadGeohashIdentity(npub))
        }

    @Test
    fun savingTheIdentityDoesNotMarkTheSecretsMigrated() =
        runTest {
            val subject = stores()

            subject.saveGeohashIdentity(npub, GeohashIdentitySecrets(deviceSeed = "c".repeat(64)))

            assertNull(subject.loadSecrets(npub))
        }

    /**
     * The seed must survive an unrelated account save. This is the whole reason
     * the identity is its own group: every save mirrors a full AccountSecrets
     * built from AccountSettings, which does not hold the seed, and the group
     * save removes keys whose value is null. Folded into that group, the seed
     * would be deleted here — and every geohash identity the user has would
     * silently change.
     */
    @Test
    fun anAccountSaveLeavesTheGeohashIdentityAlone() =
        runTest {
            val subject = stores()
            val identity = GeohashIdentitySecrets(deviceSeed = "d".repeat(64), nickname = "vitor")
            subject.saveGeohashIdentity(npub, identity)

            subject.saveSecrets(npub, filled)

            assertEquals(identity, subject.loadGeohashIdentity(npub))
        }

    /**
     * The copy has to be idempotent, because the loader now runs it on every
     * account load rather than only when a location chat is opened. A second
     * run must not overwrite what the user has changed since the first.
     */
    @Test
    fun recopyingDoesNotClobberALaterEdit() =
        runTest {
            val subject = stores()
            subject.saveGeohashIdentity(npub, GeohashIdentitySecrets(deviceSeed = "a".repeat(64), nickname = "old"))

            // What a fresh load would find in the legacy file: the pre-migration value.
            val stored = subject.loadGeohashIdentity(npub)
            assertEquals("old", stored?.nickname)

            subject.saveGeohashIdentity(npub, GeohashIdentitySecrets(deviceSeed = "a".repeat(64), nickname = "new"))

            assertEquals("new", subject.loadGeohashIdentity(npub)?.nickname)
            assertEquals("a".repeat(64), subject.loadGeohashIdentity(npub)?.deviceSeed)
        }
}

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

import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for [SecureKeyStorage.enableConsolidatedVault].
 *
 * macOS Keychain gates access per item, not per session, so caching the
 * [Keyring] handle alone (see [SecureKeyStorageKeyringCacheTest]) cannot
 * collapse the cold-boot double prompt (metadata AES key plus the active
 * account's nsec). The vault consolidates every alias Amethyst owns into a
 * single keychain item so the OS sees one ACL to gate.
 *
 * Fake [KeyringHandle] backend keeps every case hermetic; the real macOS /
 * Windows / Linux backends are only exercised by manual QA.
 */
class SecureKeyStorageVaultTest {
    private class CountingKeyring : KeyringHandle {
        val store: ConcurrentHashMap<Pair<String, String>, String> = ConcurrentHashMap()
        val readsBySlot: ConcurrentHashMap<Pair<String, String>, AtomicInteger> = ConcurrentHashMap()
        val writesBySlot: ConcurrentHashMap<Pair<String, String>, AtomicInteger> = ConcurrentHashMap()

        @Volatile
        var failWrites = false

        override fun getPassword(
            service: String,
            account: String,
        ): String {
            readsBySlot.computeIfAbsent(service to account) { AtomicInteger(0) }.incrementAndGet()
            return store[service to account] ?: throw PasswordAccessException("No stored credentials match $service account: $account")
        }

        override fun setPassword(
            service: String,
            account: String,
            password: String,
        ) {
            writesBySlot.computeIfAbsent(service to account) { AtomicInteger(0) }.incrementAndGet()
            if (failWrites) throw PasswordAccessException("denied")
            store[service to account] = password
        }

        override fun deletePassword(
            service: String,
            account: String,
        ) {
            if (store.remove(service to account) == null) {
                throw PasswordAccessException("No stored credentials match $service account: $account")
            }
        }

        fun reads(alias: String): Int = readsBySlot[SERVICE to alias]?.get() ?: 0

        fun writes(alias: String): Int = writesBySlot[SERVICE to alias]?.get() ?: 0

        fun snapshotAliases(): Set<String> =
            store.keys
                .filter { it.first == SERVICE }
                .map { it.second }
                .toSet()

        companion object {
            const val SERVICE = "amethyst-desktop"
        }
    }

    private fun newStorageWith(
        opens: AtomicInteger = AtomicInteger(0),
        prepopulate: (CountingKeyring) -> Unit = {},
    ): Pair<SecureKeyStorage, CountingKeyring> {
        val backend = CountingKeyring()
        prepopulate(backend)
        val storage = SecureKeyStorage.create()
        storage.keyringFactory = {
            opens.incrementAndGet()
            backend
        }
        return storage to backend
    }

    /** Envelope used to seed a pre-existing vault item in tests. */
    private fun seedVault(entries: Map<String, String>): String {
        val body =
            entries.entries.joinToString(",") { (k, v) ->
                "\"" + k + "\":\"" + Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8)) + "\""
            }
        return "{\"schemaVersion\":1,\"entries\":{$body}}"
    }

    @Test
    fun `enableConsolidatedVault fresh install writes an empty vault item`() =
        runBlocking {
            val (storage, backend) = newStorageWith()
            storage.enableConsolidatedVault(candidateAliases = emptyList())
            assertTrue(storage.isVaultActive())
            // Fresh install: vault item exists so future writes stay inside it.
            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            assertEquals(1, backend.writes("vault-v1"))
        }

    @Test
    fun `enableConsolidatedVault migrates legacy items and deletes originals`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "AES=="
                    b.store[CountingKeyring.SERVICE to "npub1alice"] = "aaaa"
                    b.store[CountingKeyring.SERVICE to "npub1bob"] = "bbbb"
                }

            storage.enableConsolidatedVault(
                candidateAliases =
                    listOf(
                        "account-metadata-key",
                        "npub1alice",
                        "npub1bob",
                        "npub1missing",
                        "bunker_ephemeral_npub1alice",
                    ),
            )

            // Consolidation: only vault-v1 remains, legacy items unlinked.
            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            assertTrue(storage.isVaultActive())

            // Reads still serve the correct secrets from the in-memory vault.
            assertEquals("AES==", storage.getPrivateKey("account-metadata-key"))
            assertEquals("aaaa", storage.getPrivateKey("npub1alice"))
            assertEquals("bbbb", storage.getPrivateKey("npub1bob"))
            assertNull(storage.getPrivateKey("npub1missing"))

            // Post-migration savePrivateKey stays inside the vault.
            storage.savePrivateKey("npub1carol", "cccc")
            assertEquals(
                "Post-migration writes must stay inside vault-v1, not create per-alias items",
                setOf("vault-v1"),
                backend.snapshotAliases(),
            )
            assertEquals("cccc", storage.getPrivateKey("npub1carol"))
        }

    @Test
    fun `enableConsolidatedVault existing vault is loaded without probing aliases it already contains`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "vault-v1"] =
                        seedVault(
                            mapOf(
                                "account-metadata-key" to "SEED",
                                "npub1alice" to "AAAA",
                            ),
                        )
                    // A leftover legacy item not in the candidate list on this boot,
                    // e.g. an npub the user removed from accounts.json.enc.
                    b.store[CountingKeyring.SERVICE to "npub1stale"] = "STALE"
                }

            storage.enableConsolidatedVault(
                candidateAliases = listOf("account-metadata-key", "npub1alice"),
            )

            assertTrue(storage.isVaultActive())
            assertEquals(1, backend.reads("vault-v1"))
            // Aliases that ARE the candidate set must not be re-probed once the vault has them,
            // otherwise the cold-boot prompt count would scale with the account count again.
            assertEquals(0, backend.reads("account-metadata-key"))
            assertEquals(0, backend.reads("npub1alice"))
            // Aliases outside the candidate list are ignored entirely on this boot.
            assertEquals(0, backend.reads("npub1stale"))
            assertEquals("SEED", storage.getPrivateKey("account-metadata-key"))
            assertEquals("AAAA", storage.getPrivateKey("npub1alice"))
        }

    @Test
    fun `enableConsolidatedVault absorbs legacy leftovers when candidate list still names them`() =
        runBlocking {
            // Interrupted earlier migration: vault-v1 exists but npub1stale is still
            // on disk. If the current AccountManager still has npub1stale in its
            // candidate list, cold-boot 2 must absorb it.
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "vault-v1"] =
                        seedVault(mapOf("account-metadata-key" to "SEED"))
                    b.store[CountingKeyring.SERVICE to "npub1stale"] = "STALE"
                }

            storage.enableConsolidatedVault(
                candidateAliases = listOf("account-metadata-key", "npub1stale"),
            )

            assertTrue(storage.isVaultActive())
            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            assertEquals("STALE", storage.getPrivateKey("npub1stale"))
        }

    @Test
    fun `savePrivateKey after vault enabled persists to vault`() =
        runBlocking {
            val (storage, backend) = newStorageWith()
            storage.enableConsolidatedVault(emptyList())

            storage.savePrivateKey("npub1new", "1111")
            storage.savePrivateKey("npub1other", "2222")

            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            // Empty-vault seed (1) plus two follow-up writes.
            assertEquals(3, backend.writes("vault-v1"))
            assertEquals("1111", storage.getPrivateKey("npub1new"))
            assertEquals("2222", storage.getPrivateKey("npub1other"))
        }

    @Test
    fun `saving the same key again does not touch the keychain`() =
        runBlocking {
            val (storage, backend) = newStorageWith()
            storage.enableConsolidatedVault(emptyList())
            storage.savePrivateKey("npub1new", "1111")
            val reads = backend.reads("vault-v1")

            repeat(5) { storage.savePrivateKey("npub1new", "1111") }

            // Empty-vault seed (1) plus the first save; the repeats are no-ops.
            assertEquals(2, backend.writes("vault-v1"))
            assertEquals(reads, backend.reads("vault-v1"))

            storage.savePrivateKey("npub1new", "2222")
            assertEquals(3, backend.writes("vault-v1"))
        }

    @Test
    fun `a denied vault write is retried by the next save`() =
        runBlocking {
            val (storage, backend) = newStorageWith()
            storage.enableConsolidatedVault(emptyList())

            backend.failWrites = true
            assertTrue(runCatching { storage.savePrivateKey("npub1new", "1111") }.isFailure)
            assertNull(storage.getPrivateKey("npub1new"))

            backend.failWrites = false
            storage.savePrivateKey("npub1new", "1111")
            assertEquals("1111", storage.getPrivateKey("npub1new"))
            assertTrue(backend.store.values.any { it.contains(Base64.getEncoder().encodeToString("1111".toByteArray())) })
        }

    @Test
    fun `getPrivateKey after vault enabled reads from vault contents in memory`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "vault-v1"] =
                        seedVault(mapOf("npub1alice" to "aaaa"))
                }
            storage.enableConsolidatedVault(listOf("npub1alice"))

            val readsAfterLoad = backend.reads("vault-v1")
            repeat(10) {
                assertEquals("aaaa", storage.getPrivateKey("npub1alice"))
                assertNull(storage.getPrivateKey("npub1missing"))
            }
            assertEquals(
                "Post-load reads must be served entirely from memory",
                readsAfterLoad,
                backend.reads("vault-v1"),
            )
        }

    @Test
    fun `enableConsolidatedVault is idempotent when called repeatedly`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "npub1a"] = "AA"
                }
            storage.enableConsolidatedVault(listOf("npub1a"))
            val writesAfterMigration = backend.writes("vault-v1")
            storage.enableConsolidatedVault(listOf("npub1a"))
            storage.enableConsolidatedVault(listOf("npub1a"))
            // No further writes: idempotent second-phase calls with no new legacy
            // aliases are a pure no-op. Migration writes the vault once total.
            assertEquals(
                "enableConsolidatedVault must be safe to call repeatedly (cold-boot invariant)",
                writesAfterMigration,
                backend.writes("vault-v1"),
            )
        }

    @Test
    fun `enableConsolidatedVault two phase migration folds in later aliases`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "K"
                    b.store[CountingKeyring.SERVICE to "npub1alice"] = "aaaa"
                    b.store[CountingKeyring.SERVICE to "npub1bob"] = "bbbb"
                }
            // Phase 1: bootstrap with just the metadata key, mimicking
            // AccountManager.create() before accounts.json.enc is decrypted.
            storage.enableConsolidatedVault(listOf("account-metadata-key"))
            assertTrue(storage.isVaultActive())
            assertEquals("K", storage.getPrivateKey("account-metadata-key"))
            // Legacy nsecs still on disk because they weren't in phase-1 candidates.
            assertTrue("npub1alice" in backend.snapshotAliases())

            // Phase 2: full list once npubs are known.
            storage.enableConsolidatedVault(listOf("account-metadata-key", "npub1alice", "npub1bob"))
            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            assertEquals("aaaa", storage.getPrivateKey("npub1alice"))
            assertEquals("bbbb", storage.getPrivateKey("npub1bob"))
        }

    @Test
    fun `enableConsolidatedVault partial legacy migration survives relaunch`() =
        runBlocking {
            // Cold boot 1: only metadata key gets migrated; nsec stays on disk.
            val opens1 = AtomicInteger(0)
            val (storage1, backend) =
                newStorageWith(opens1) { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "K"
                    b.store[CountingKeyring.SERVICE to "npub1alice"] = "aaaa"
                }
            storage1.enableConsolidatedVault(listOf("account-metadata-key"))
            assertTrue("npub1alice" in backend.snapshotAliases())
            assertEquals("K", storage1.getPrivateKey("account-metadata-key"))

            // Cold boot 2: fresh SecureKeyStorage against the same backing store.
            val opens2 = AtomicInteger(0)
            val storage2 = SecureKeyStorage.create()
            storage2.keyringFactory = {
                opens2.incrementAndGet()
                backend
            }
            storage2.enableConsolidatedVault(listOf("account-metadata-key", "npub1alice"))
            assertEquals(setOf("vault-v1"), backend.snapshotAliases())
            assertEquals("K", storage2.getPrivateKey("account-metadata-key"))
            assertEquals("aaaa", storage2.getPrivateKey("npub1alice"))
        }

    @Test
    fun `delete removes from vault and unlinks item when the last key goes`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "K"
                    b.store[CountingKeyring.SERVICE to "npub1x"] = "V"
                }
            storage.enableConsolidatedVault(listOf("account-metadata-key", "npub1x"))
            assertTrue(storage.isVaultActive())

            assertTrue(storage.deletePrivateKey("npub1x"))
            assertTrue("vault-v1" in backend.snapshotAliases())

            assertTrue(storage.deletePrivateKey("account-metadata-key"))
            assertFalse(
                "Emptying the vault must unlink the keychain item so a fresh install path can re-migrate cleanly",
                "vault-v1" in backend.snapshotAliases(),
            )
        }

    @Test
    fun `vault survives keys with quotes newlines and unicode`() =
        runBlocking {
            val weirdAlias = "weird\"key\n\u2603"
            val weirdValue = "value with \"quotes\" and \\backslashes and \u2603 snowmen"
            val (storage, _) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to weirdAlias] = weirdValue
                }
            storage.enableConsolidatedVault(listOf(weirdAlias))
            assertEquals(weirdValue, storage.getPrivateKey(weirdAlias))
        }

    // --- Interaction with the strict getOrCreate path (proposal 5d31b68e) ---

    /**
     * Wires the strict macOS probe to the fake backend so the test models real
     * macOS: `security find-generic-password` sees the OS keychain, returning
     * exit 0 (Found) while a per-alias item exists and exit 44 (NotFound) once
     * the migration has deleted it.
     */
    private fun wireMacProbe(
        storage: SecureKeyStorage,
        backend: CountingKeyring,
    ) {
        storage.macSecurityLookup = { service, account ->
            backend.store[service to account]
                ?.let { MacSecurityResult.Found(it) }
                ?: MacSecurityResult.NotFound
        }
    }

    @Test
    fun `strict lookup reads through the vault after migration`() =
        runBlocking {
            val alias = "account-metadata-key"
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to alias] = "THE-AES-KEY"
                }
            wireMacProbe(storage, backend)

            assertEquals("THE-AES-KEY", storage.getPrivateKeyOrThrow(alias))

            storage.enableConsolidatedVault(listOf(alias))

            // The migration deleted the per-alias item, so an unvaulted strict probe
            // would answer "definitively absent" -- and DesktopAccountStorage would
            // mint a fresh AES key over the one that decrypts accounts.json.enc,
            // wiping every account. The vault must answer instead.
            assertFalse(alias in backend.snapshotAliases())
            assertEquals(
                "Strict lookup must read through the vault, not report the migrated alias as absent",
                "THE-AES-KEY",
                storage.getPrivateKeyOrThrow(alias),
            )
        }

    @Test
    fun `strict lookup keeps the strict contract for aliases outside the vault`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "THE-AES-KEY"
                }
            storage.enableConsolidatedVault(listOf("account-metadata-key"))

            // An alias the vault never covered still falls through to the strict probe. Off macOS
            // that probe is javakeyring: its "No stored credentials match" (Windows: error 1168) is
            // a miss, and anything else, a denial, throws (SecureKeyStorageOrThrowTest). A miss
            // used to throw too, which left every view-only or bunker login unloadable there.
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) {
                assertNull(storage.getPrivateKeyOrThrow("npub1neverseen"))
                return@runBlocking
            }

            // On macOS a confirmed miss is null, an ambiguous answer still throws.
            wireMacProbe(storage, backend)
            assertNull(storage.getPrivateKeyOrThrow("npub1neverseen"))

            storage.macSecurityLookup = { _, _ -> MacSecurityResult.Ambiguous(128, "user cancelled Keychain dialog") }
            try {
                storage.getPrivateKeyOrThrow("npub1neverseen")
                throw AssertionError("Expected SecureStorageException for an ambiguous answer")
            } catch (e: SecureStorageException) {
                assertTrue(e.message?.contains("cancelled") == true)
            }
        }

    @Test
    fun `getPrivateKey falls back to a legacy item the vault does not cover`() =
        runBlocking {
            // Phase 1 activates the vault with only the metadata key; a phase 2 that
            // never ran (or threw, which AccountManager swallows) leaves every nsec
            // outside it. Those must stay readable, not read as absent.
            val (storage, _) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "THE-AES-KEY"
                    b.store[CountingKeyring.SERVICE to "npub1someaccount"] = "THE-NSEC"
                }

            storage.enableConsolidatedVault(listOf("account-metadata-key"))

            assertTrue(storage.isVaultActive())
            assertEquals("THE-AES-KEY", storage.getPrivateKey("account-metadata-key"))
            assertEquals(
                "An uncovered alias must fall back to its legacy per-alias item",
                "THE-NSEC",
                storage.getPrivateKey("npub1someaccount"),
            )
            assertTrue(storage.hasPrivateKey("npub1someaccount"))
        }

    @Test
    fun `delete unlinks a legacy item the vault does not cover`() =
        runBlocking {
            val (storage, backend) =
                newStorageWith { b ->
                    b.store[CountingKeyring.SERVICE to "account-metadata-key"] = "THE-AES-KEY"
                    b.store[CountingKeyring.SERVICE to "npub1someaccount"] = "THE-NSEC"
                }
            storage.enableConsolidatedVault(listOf("account-metadata-key"))

            // Logging out of an account whose nsec never reached the vault must not
            // leave the secret orphaned in the OS keychain.
            assertTrue(storage.deletePrivateKey("npub1someaccount"))
            assertFalse("npub1someaccount" in backend.snapshotAliases())
            assertNull(storage.getPrivateKey("npub1someaccount"))
        }
}

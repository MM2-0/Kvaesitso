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

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account index.
 *
 * Every branch here decides whether the app opens to the user's accounts or to
 * an empty screen. A read that comes back empty is indistinguishable from a
 * store that has not been populated, and only one of those is safe to act on —
 * so the legacy file wins every tie.
 */
class AccountRosterTest {
    private val accountsJson = """[{"npub":"npub1a","hasPrivKey":true}]"""

    private class FakeStorage(
        var migrated: Boolean = false,
        var current: String? = null,
        var allJson: String? = null,
        var failReads: Boolean = false,
        var failWrites: Boolean = false,
    ) : RosterStorage {
        var cleared = 0

        override suspend fun hasMigrated(): Boolean {
            if (failReads) throw IllegalStateException("unreadable")
            return migrated
        }

        override suspend fun markMigrated() {
            if (failWrites) throw IllegalStateException("unwritable")
            migrated = true
        }

        override suspend fun currentAccount(): String? {
            if (failReads) throw IllegalStateException("unreadable")
            return current
        }

        override suspend fun setCurrentAccount(npub: String?) {
            if (failWrites) throw IllegalStateException("unwritable")
            current = npub
        }

        override suspend fun allAccountInfoJson(): String? {
            if (failReads) throw IllegalStateException("unreadable")
            return allJson
        }

        override suspend fun setAllAccountInfoJson(json: String?) {
            if (failWrites) throw IllegalStateException("unwritable")
            allJson = json
        }

        override suspend fun clear() {
            cleared++
            migrated = false
            current = null
            allJson = null
        }
    }

    private fun legacy(
        current: String? = "npub1a",
        all: String? = accountsJson,
    ) = Pair<() -> String?, () -> String?>({ current }, { all })

    // ── first run after the upgrade ───────────────────────────────────

    @Test
    fun theLegacyRosterIsCopiedOnFirstRead() =
        runTest {
            val store = FakeStorage()
            val (c, a) = legacy()

            assertEquals("npub1a", AccountRoster(store).currentAccount(c, a))

            assertTrue(store.migrated)
            assertEquals("npub1a", store.current)
            assertEquals(accountsJson, store.allJson)
        }

    @Test
    fun anAlreadyMigratedRosterIsReadFromTheNewStore() =
        runTest {
            val store = FakeStorage(migrated = true, current = "npub1z", allJson = """[{"npub":"npub1z"}]""")
            val (c, a) = legacy()

            assertEquals("npub1z", AccountRoster(store).currentAccount(c, a))
            assertEquals("""[{"npub":"npub1z"}]""", AccountRoster(store).allAccountInfoJson(c, a))
        }

    // ── the failure modes that empty the account list ─────────────────

    /** An unreadable store must never present as "no accounts". */
    @Test
    fun anUnreadableStoreFallsBackToLegacy() =
        runTest {
            val store = FakeStorage(failReads = true)
            val (c, a) = legacy()

            assertEquals("npub1a", AccountRoster(store).currentAccount(c, a))
            assertEquals(accountsJson, AccountRoster(store).allAccountInfoJson(c, a))
        }

    /** Nor must a migration that could not be written. */
    @Test
    fun aFailedMigrationFallsBackToLegacy() =
        runTest {
            val store = FakeStorage(failWrites = true)
            val (c, a) = legacy()

            assertEquals("npub1a", AccountRoster(store).currentAccount(c, a))
            assertEquals(accountsJson, AccountRoster(store).allAccountInfoJson(c, a))
        }

    /**
     * A migrated-but-empty list is indistinguishable from one that was never
     * populated, so it falls through rather than being taken as truth.
     */
    @Test
    fun anEmptyAccountListFallsBackToLegacy() =
        runTest {
            val (c, a) = legacy()

            assertEquals(accountsJson, AccountRoster(FakeStorage(migrated = true, allJson = "[]")).allAccountInfoJson(c, a))
            assertEquals(accountsJson, AccountRoster(FakeStorage(migrated = true, allJson = "")).allAccountInfoJson(c, a))
            assertEquals(accountsJson, AccountRoster(FakeStorage(migrated = true, allJson = null)).allAccountInfoJson(c, a))
        }

    @Test
    fun anAbsentCurrentAccountFallsBackToLegacy() =
        runTest {
            val (c, a) = legacy()

            assertEquals("npub1a", AccountRoster(FakeStorage(migrated = true, current = null)).currentAccount(c, a))
        }

    /** A genuinely fresh install has nothing anywhere, and must not invent an account. */
    @Test
    fun aFreshInstallReadsAsNothing() =
        runTest {
            val (c, a) = legacy(current = null, all = null)

            assertNull(AccountRoster(FakeStorage()).currentAccount(c, a))
            assertNull(AccountRoster(FakeStorage()).allAccountInfoJson(c, a))
        }

    // ── writes ────────────────────────────────────────────────────────

    @Test
    fun mirroredWritesReachTheStore() =
        runTest {
            val store = FakeStorage()
            val subject = AccountRoster(store)

            subject.mirrorCurrentAccount("npub1b")
            subject.mirrorAllAccountInfoJson(accountsJson)

            assertEquals("npub1b", store.current)
            assertEquals(accountsJson, store.allJson)
        }

    @Test
    fun clearingWipesTheRoster() =
        runTest {
            val store = FakeStorage(migrated = true, current = "npub1a", allJson = accountsJson)

            AccountRoster(store).clear()

            assertEquals(1, store.cleared)
            assertNull(store.current)
            assertNull(store.allJson)
        }

    /** A write failure must never fail the save: the legacy file is still written. */
    @Test
    fun aWriteFailureIsSwallowed() =
        runTest {
            val subject = AccountRoster(FakeStorage(failWrites = true))

            subject.mirrorCurrentAccount("npub1b")
            subject.mirrorAllAccountInfoJson(accountsJson)
        }
}

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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.tor.SignerRelayRouting
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountSessionManagerTest {
    private val events = mutableListOf<String>()

    private val store =
        object : AccountSessionStore {
            override suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = null

            override suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? = null

            override suspend fun setDefaultAccount(accountSettings: AccountSettings) = accountSettings

            override suspend fun setHasBackedUpKeys(
                value: Boolean,
                npub: String,
            ) {}

            override suspend fun currentAccount(): String? = null

            override suspend fun allSavedAccounts(): List<AccountInfo> = emptyList()

            override fun accountsFlow(): StateFlow<List<AccountInfo>?> = MutableStateFlow(emptyList())

            override suspend fun switchToAccount(accountInfo: AccountInfo) {
                events += "switch"
            }

            override suspend fun deleteAccount(accountInfo: AccountInfo) {
                events += "store.delete"
            }
        }

    private val cache =
        object : AccountCache {
            override fun loadAccount(accountSettings: AccountSettings): Account = error("no account is loaded in these tests")

            override fun removeAccount(pubkey: HexKey) {
                events += "cache.remove"
            }

            override fun deleteAccountFiles(pubkey: HexKey) {
                events += "cache.files"
            }
        }

    private val hooks =
        object : AccountSessionHooks {
            override fun onSessionEnding() {
                events += "ending"
            }

            override suspend fun onAccountRemoving(npub: String) {
                events += "removing"
            }

            override suspend fun onAccountRemoved(pubkey: HexKey) {
                events += "removed"
            }
        }

    private val scope = CoroutineScope(Job())

    private val manager =
        AccountSessionManager(
            accountsCache = cache,
            nip05ClientBuilder = { error("unused") },
            clientBuilder = { error("unused") },
            localPreferences = store,
            scope = scope,
            hooks = hooks,
        )

    private suspend fun settle() =
        scope.coroutineContext.job.children
            .toList()
            .joinAll()

    @Test
    fun withNothingSavedTheSessionLogsOff() =
        runTest {
            manager.loginWithDefaultAccountIfLoggedOff()
            settle()
            assertIs<AccountState.LoggedOff>(manager.accountContent.value)
        }

    @Test
    fun deletingAnotherAccountCleansUpWithoutEndingTheSession() =
        runTest {
            val npub = KeyPair().pubKey.toNpub()
            manager.logOff(AccountInfo(npub))
            settle()
            assertEquals(listOf("removing", "cache.remove", "store.delete", "cache.files", "removed"), events)
        }

    @Test
    fun aWrongNcryptsecPasswordReportsAnError() =
        runTest {
            var error: String? = null
            // NIP-49's test vector, decrypted with the wrong password.
            manager.login(
                key = "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p",
                password = "wrong",
                transientAccount = true,
                onError = { error = it },
            )
            settle()
            assertTrue(error != null)
            assertTrue(events.isEmpty())
        }

    @Test
    fun aNostrConnectLoginRoutesItsRelaysAsTheUsersOwnUntilItEnds() =
        runTest {
            val registered = mutableMapOf<NormalizedRelayUrl, Int>()
            val routing =
                object : SignerRelayRouting {
                    override fun registerSignerRelays(relays: Set<NormalizedRelayUrl>) = relays.forEach { registered[it] = (registered[it] ?: 0) + 1 }

                    override fun unregisterSignerRelays(relays: Set<NormalizedRelayUrl>) = relays.forEach { registered[it] = registered.getValue(it) - 1 }
                }
            val failingLogin =
                AccountSessionManager(
                    accountsCache = cache,
                    nip05ClientBuilder = { error("unused") },
                    clientBuilder = { error("no relays in this test") },
                    localPreferences = store,
                    scope = scope,
                    signerRelayRouting = routing,
                )
            val relay = NormalizedRelayUrl("wss://relay.nip46.com/")

            var registeredWhileShown: Map<NormalizedRelayUrl, Int>? = null
            assertFails {
                failingLogin.loginWithNostrConnect(listOf(relay.url), transientAccount = true) {
                    registeredWhileShown = registered.toMap()
                }
            }

            assertEquals(mapOf(relay to 1), registeredWhileShown)
            // A login that ends without an account has nothing to hand over to: released at once.
            assertEquals(mapOf(relay to 0), registered)
        }

    @Test
    fun everyLoginCarriesTheHostsCashuCounterStore() =
        runTest {
            val counters =
                object : CashuKeysetCounterStore {
                    override suspend fun peek(keysetId: String) = 0L

                    override suspend fun reserve(
                        keysetId: String,
                        count: Int,
                    ) = 0L
                }
            val saved = mutableListOf<AccountSettings>()
            val savingStore =
                object : AccountSessionStore by store {
                    override suspend fun setDefaultAccount(accountSettings: AccountSettings) = accountSettings.also { saved += it }
                }
            val withCounters =
                AccountSessionManager(
                    accountsCache = cache,
                    nip05ClientBuilder = { error("unused") },
                    clientBuilder = { error("unused") },
                    localPreferences = savingStore,
                    scope = scope,
                    hooks =
                        object : AccountSessionHooks {
                            override fun cashuCountersFor(npub: String) = counters
                        },
                )

            val keyPair = KeyPair()
            // The cache refuses to load an account here, so each login ends in onError after saving.
            listOf(keyPair.privKey!!.toNsec(), keyPair.pubKey.toNpub(), keyPair.pubKey.toHexKey()).forEach {
                withCounters.loginSync(it, transientAccount = true, onError = {})
            }
            withCounters.loginSync(keyPair.pubKey.toHexKey(), transientAccount = true, loginWithExternalSigner = true, onError = {})

            assertEquals(4, saved.size)
            saved.forEach { assertSame(counters, it.cashuCounters) }
            assertSame(counters, withCounters.createNewAccount().cashuCounters)
        }

    @Test
    fun aTransientSignUpKeepsTheSignUpDefaults() {
        val kept = manager.createNewAccount("Test")
        val transient = manager.createNewAccount("Test", transientAccount = true)

        assertTrue(!kept.transientAccount)
        assertTrue(transient.transientAccount)
        assertTrue(transient.isWriteable())
        assertNotNull(transient.backupUserMetadata)
        assertNotNull(transient.backupContactList)
        assertNotNull(transient.backupNIP65RelayList)
    }

    @Test
    fun anEmailShapedKeyIsANip05Address() {
        assertTrue(EMAIL_PATTERN.matches("vitor@vitorpamplona.com"))
        assertTrue(!EMAIL_PATTERN.matches("npub1abc"))
        assertTrue(!EMAIL_PATTERN.matches(KeyPair().pubKey.toHexKey()))
    }
}

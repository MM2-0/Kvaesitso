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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Fetcher
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `name@domain` in the login field: a domain that advertises a NIP-46 signer gets a signing account
 * through it; one that advertises none still gets the read-only account it always did.
 */
class AccountSessionManagerNip05LoginTest {
    private val relay = NormalizedRelayUrl("wss://relay.signer.example/")

    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

    private object AllowAll : Nip46RequestAuthorizer {
        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept(request.secret ?: "ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {}
    }

    /** Stops at the settings the login saved: what it made of the address. */
    private class Recording : AccountSessionStore {
        val saved = CompletableDeferred<AccountSettings>()

        override suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = null

        override suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? = null

        override suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings {
            saved.complete(accountSettings)
            return accountSettings
        }

        override suspend fun setHasBackedUpKeys(
            value: Boolean,
            npub: String,
        ) {}

        override suspend fun currentAccount(): String? = null

        override suspend fun allSavedAccounts(): List<AccountInfo> = emptyList()

        override fun accountsFlow(): StateFlow<List<AccountInfo>?> = MutableStateFlow(emptyList())

        override suspend fun switchToAccount(accountInfo: AccountInfo) {}

        override suspend fun deleteAccount(accountInfo: AccountInfo) {}
    }

    private object NoAccounts : AccountCache {
        override fun loadAccount(accountSettings: AccountSettings): Account = error("not loaded in this test")

        override fun removeAccount(pubkey: HexKey) {}

        override fun deleteAccountFiles(pubkey: HexKey) {}
    }

    private fun loginWith(nostrJson: String): AccountSettings =
        runBlocking(Dispatchers.Default) {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val server = NostrServer(EventStore(null))
            val client =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = if (url == relay) InProcessWebSocket(server, out) else NeverConnects
                    },
                    scope,
                )
            val service = NostrConnectSignerService(client, user, BunkerRequestProcessor(user, { setOf(relay) }, AllowAll), setOf(relay))
            scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

            val store = Recording()
            val manager =
                AccountSessionManager(
                    accountsCache = NoAccounts,
                    nip05ClientBuilder = {
                        Nip05Client(
                            object : Nip05Fetcher {
                                override suspend fun fetch(url: String) = if (url.endsWith("?name=bob")) nostrJson else throw IllegalStateException("404")
                            },
                        )
                    },
                    clientBuilder = { client },
                    localPreferences = store,
                    scope = scope,
                )
            try {
                manager.login(key = "bob@signer.example", password = "", transientAccount = false) {}
                withTimeout(20_000) { store.saved.await() }
            } finally {
                scope.cancel()
                server.close()
            }
        }

    private val user = NostrSignerInternal(KeyPair())

    @Test
    fun anAddressWithASignerGetsASigningAccount() {
        val settings = loginWith("""{"names":{"bob":"${user.pubKey}"},"nip46":{"${user.pubKey}":["${relay.url}"]}}""")

        assertEquals(user.pubKey, settings.keyPair.pubKey.toHexKey())
        assertEquals(NostrConnectURI.buildBunker(user.pubKey, setOf(relay), secret = null), settings.remoteSignerBunkerUri)
    }

    @Test
    fun anAddressWithoutASignerIsStillReadOnly() {
        val settings = loginWith("""{"names":{"bob":"${user.pubKey}"}}""")

        assertEquals(user.pubKey, settings.keyPair.pubKey.toHexKey())
        assertNull(settings.remoteSignerBunkerUri)
        assertNull(settings.keyPair.privKey)
    }
}

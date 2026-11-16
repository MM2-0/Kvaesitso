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
package com.vitorpamplona.amethyst.commons.account.ui.login

import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `nostrconnect://` code names the relays the signer must reach. A signer that can't reach the
 * defaults never sees the request, so the user can name the relay their signer uses instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelNostrConnectRelayTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        LoginViewModel().apply {
            acceptedTerms = true
            init(
                AccountSessionManager(
                    accountsCache = mockk(relaxed = true),
                    nip05ClientBuilder = { mockk() },
                    // Nobody answers: the connection stays pending until cancelled.
                    clientBuilder = { EmptyNostrClient() },
                    localPreferences = mockk(relaxed = true),
                    scope = scope,
                ),
            )
        }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(10) }

    /** The relays a `nostrconnect://` code names, decoded the way a signer reads them. */
    private fun relaysIn(uri: String?) =
        uri
            ?.let { NostrConnectURI.parseNostrConnect(it) }
            ?.relays
            ?.map { it.url }
            ?.toSet()

    @Test
    fun pickingARelayReissuesThePendingCodeOnIt() =
        runBlocking {
            val model = viewModel()

            model.loginWithNostrConnect()
            until { model.nostrConnectUri != null }
            val first = model.nostrConnectUri!!
            assertEquals(LoginViewModel.DEFAULT_NOSTR_CONNECT_RELAYS.map { "$it/" }.toSet(), relaysIn(first), first)

            assertTrue(model.useNostrConnectRelay("relay.example.com"))
            until { model.nostrConnectUri.let { it != null && it != first } }
            val second = model.nostrConnectUri!!
            assertEquals(setOf("wss://relay.example.com/"), relaysIn(second), second)
            assertEquals(listOf("wss://relay.example.com/"), model.nostrConnectRelays)
            assertFalse(model.usesDefaultNostrConnectRelays)
            // The dialog stays up across the switch; the cancelled attempt doesn't close it.
            delay(200)
            assertTrue(model.nostrConnectPending)
            assertTrue(model.processingLogin)

            model.useDefaultNostrConnectRelays()
            until { relaysIn(model.nostrConnectUri)?.contains("wss://relay.nip46.com/") == true }
            assertTrue(model.usesDefaultNostrConnectRelays)

            model.cancelNostrConnect()
            until { !model.nostrConnectPending }
            assertFalse(model.processingLogin)
            assertNull(model.nostrConnectUri)
        }

    @Test
    fun aBlankRelayIsRefusedAndKeepsTheRelays() {
        val model = viewModel()

        assertFalse(model.useNostrConnectRelay("   "))
        assertEquals(LoginViewModel.DEFAULT_NOSTR_CONNECT_RELAYS, model.nostrConnectRelays)
    }

    /** Picked before asking for a code, the relay is the one the first code names. */
    @Test
    fun aRelayPickedBeforeConnectingIsUsed() =
        runBlocking {
            val model = viewModel()

            assertTrue(model.useNostrConnectRelay("wss://relay.example.com"))
            assertFalse(model.nostrConnectPending)

            model.loginWithNostrConnect()
            until { model.nostrConnectUri != null }
            assertEquals(setOf("wss://relay.example.com/"), relaysIn(model.nostrConnectUri))

            model.cancelNostrConnect()
            until { !model.nostrConnectPending }
        }
}

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
package com.vitorpamplona.geode.testing

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After

/**
 * Base class for tests that drive a real [NostrClient] against an
 * in-process [InProcessRelays]. Owns the lifecycle of the four pieces every
 * such test needs:
 *
 *  - [hub] — the registry of in-process relays (also serves as
 *    `WebsocketBuilder` for [NostrClient]).
 *  - [scope] — application coroutine scope for the client.
 *  - [client] — a [NostrClient] wired to [hub] and [scope].
 *  - [defaultRelay] / [defaultRelayUrl] — convenience handles for the
 *    single-relay case (the most common in tests).
 *
 * Cleanup happens in [tearDownRelayClientTest], registered with
 * JUnit's [@After][After], so an assertion failure does NOT leak the
 * scope, the SQLite event store, or the WebSocket bridge — a recurring
 * problem with the previous "clean up at the end of the test body"
 * pattern.
 *
 * Subclasses that need their own setup/teardown should add their own
 * `@Before` / `@After` methods; JUnit runs all of them.
 *
 * Multi-relay tests use [hub] directly:
 * ```
 * val relayA = RelayUrlNormalizer.normalize("ws://relay-a/")
 * hub.getOrCreate(relayA).preload(eventA)
 * hub.getOrCreate(relayB).preload(eventB)
 * ```
 */
open class RelayClientTest {
    val hub: InProcessRelays = InProcessRelays()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val client: NostrClient = NostrClient(hub, scope)

    /** Stable URL for the single-relay case — see [InProcessRelays.DEFAULT_URL]. */
    val defaultRelayUrl: NormalizedRelayUrl get() = InProcessRelays.DEFAULT_URL

    /** Lazy handle to the relay at [defaultRelayUrl]. Auto-created on first read. */
    val defaultRelay: RelayEngine get() = hub.getOrCreate(defaultRelayUrl)

    @After
    fun tearDownRelayClientTest() {
        client.disconnect()
        scope.cancel()
        hub.close()
    }
}

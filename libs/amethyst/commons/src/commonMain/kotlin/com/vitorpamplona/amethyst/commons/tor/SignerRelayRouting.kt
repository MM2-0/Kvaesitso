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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Puts the relays a NIP-46 login talks to under the trusted-relay Tor preference while the login
 * runs, before any account exists to list them. The user picked these relays (a bunker address
 * they pasted, a `nostrconnect://` code they chose to show), so they are not strangers: left to
 * the new-relay policy, a fresh install waits for Tor before the signer can even answer, and
 * waits forever when Tor cannot start. Registrations are counted, so each [registerSignerRelays]
 * needs its own [unregisterSignerRelays].
 */
interface SignerRelayRouting {
    fun registerSignerRelays(relays: Set<NormalizedRelayUrl>)

    fun unregisterSignerRelays(relays: Set<NormalizedRelayUrl>)

    /** Routes nothing specially: for tests and hosts without Tor. */
    object None : SignerRelayRouting {
        override fun registerSignerRelays(relays: Set<NormalizedRelayUrl>) = Unit

        override fun unregisterSignerRelays(relays: Set<NormalizedRelayUrl>) = Unit
    }
}

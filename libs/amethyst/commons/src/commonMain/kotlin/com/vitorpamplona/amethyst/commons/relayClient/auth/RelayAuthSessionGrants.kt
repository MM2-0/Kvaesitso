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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The relays this account said "log in" to during **this run of the app**, without asking to
 * remember the answer permanently.
 *
 * A NIP-42 challenge is not a one-off event: relays re-challenge on every reconnect, and a client
 * that drops its socket on network changes, doze, or an app switch reconnects constantly. Answering
 * the dialog only for the single in-flight challenge therefore meant the same relay asked the same
 * question again minutes later — the prompt fatigue that makes users reach for "always allow" on a
 * relay they only wanted to try once. Holding the grant in memory keeps the answer alive for exactly
 * as long as the user is plausibly still doing the thing they answered for.
 *
 * Deliberately **not** persisted: it is dropped when the process dies (this object lives on
 * [com.vitorpamplona.amethyst.commons.model.Account], which is built per process) and when the account is
 * logged out, so the next cold start asks again. That is the whole difference from
 * [com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision.ALLOW], which the "remember"
 * switch writes to disk.
 *
 * Scoped per account because the grant authorizes revealing *one* identity: the prompt names whose
 * npub is at stake, so account B is never covered by an answer given for account A.
 */
class RelayAuthSessionGrants {
    private val granted = MutableStateFlow<Set<String>>(emptySet())

    /** Observable so the settings screen can list — and revoke — what is currently granted. */
    val grants: StateFlow<Set<String>> = granted.asStateFlow()

    /** Non-suspending: this is read on the hot NIP-42 decision path. */
    fun isGranted(relayUrl: String): Boolean = relayUrl in granted.value

    fun grant(relayUrl: String) = granted.update { it + relayUrl }

    fun revoke(relayUrl: String) = granted.update { it - relayUrl }

    fun clear() = granted.update { emptySet() }
}

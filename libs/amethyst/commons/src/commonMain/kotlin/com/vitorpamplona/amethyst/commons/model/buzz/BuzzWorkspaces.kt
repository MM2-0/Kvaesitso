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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The set of `block/buzz` workspaces the user has **joined** — one relay (tenant) each.
 *
 * Distinct from [BuzzRelayDialect], which merely marks relays *observed* to speak the Buzz
 * dialect: a workspace here is one the user actively joined (redeemed an invite for), so the
 * app must connect + NIP-42-authenticate + run the member-channel discovery against it, even
 * on a cold start before any Buzz event has arrived to trigger dialect detection. Buzz
 * membership is granted server-side by the HTTP invite claim — there is no NIP-51/kind-10009
 * join event to key off — so this joined set is the client's own bookkeeping of which relays
 * to sync as workspaces.
 *
 * **One instance per account** (`Account.buzzWorkspaces`) — deliberately NOT a process-wide
 * singleton like [BuzzRelayDialect]. Joining is a per-user act: the invite was redeemed by one
 * key and the relay grants membership to that key alone. While this was device-global it also
 * fed `AuthCoordinator.isFirstParty`, so one account joining a workspace made *every* logged-in
 * account first-party there — the bystander-account AUTH leak the per-account gate exists to
 * prevent. The dialect mark stays global: which protocol a relay speaks is a property of the
 * relay, not of who is asking.
 *
 * Persisted across launches by the platform (`BuzzWorkspacePreferences` on Android mirrors each
 * account's set to that account's own store and restores it at startup). Joining also marks the
 * relay as a Buzz dialect.
 */
class BuzzWorkspaces {
    private val joined = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /** The joined workspace relays; discovery subscriptions and the workspaces hub collect this. */
    val flow: StateFlow<Set<NormalizedRelayUrl>> = joined

    fun isJoined(relay: NormalizedRelayUrl): Boolean = relay in joined.value

    /** Records [relay] as a joined workspace (and a Buzz dialect). Returns true when it was new. */
    fun join(relay: NormalizedRelayUrl): Boolean {
        BuzzRelayDialect.mark(relay)
        while (true) {
            val current = joined.value
            if (relay in current) return false
            if (joined.compareAndSet(current, current + relay)) return true
        }
    }

    /** Removes [relay] from the joined set (leaving a workspace). */
    fun leave(relay: NormalizedRelayUrl) {
        while (true) {
            val current = joined.value
            if (relay !in current) return
            if (joined.compareAndSet(current, current - relay)) return
        }
    }

    /**
     * Replaces the whole joined set with [relays] — used to restore from disk at startup. Each is
     * also marked a Buzz dialect so its events materialize as workspace channels immediately.
     */
    fun restore(relays: Set<NormalizedRelayUrl>) {
        relays.forEach { BuzzRelayDialect.mark(it) }
        joined.value = relays
    }
}

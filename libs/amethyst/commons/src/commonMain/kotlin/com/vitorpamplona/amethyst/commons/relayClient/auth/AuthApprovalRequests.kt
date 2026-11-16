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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the set of tier-2 NIP-42 AUTH challenges awaiting a decision and the
 * logic that settles them. Platform-agnostic (lives in `commons`) so the state
 * and, crucially, the cold-boot race fix are unit-testable without any desktop
 * wiring (relay client, LocalCache, Compose).
 *
 * A [PendingAuthApproval] is produced by [AuthApprovalPolicy.classify] when a
 * relay is neither pre-approved nor blocked; the suspended signer awaits its
 * [PendingAuthApproval.decision]. This class owns the live set the banner
 * renders and the three ways an entry leaves it:
 *
 *  - [resolve] — the user picked `[Once] [Always] [Never]`.
 *  - [autoApproveNowTrusted] — the relay became tier-1 after the challenge was
 *    surfaced (the cold-boot race, see that method).
 *  - [cancelAll] — logout / account switch tears everything down.
 */
class AuthApprovalRequests {
    private val _pending = MutableStateFlow<PersistentMap<NormalizedRelayUrl, PendingAuthApproval>>(persistentMapOf())

    /** The live set of pending tier-2 challenges the AUTH banner renders. */
    val pending: StateFlow<PersistentMap<NormalizedRelayUrl, PendingAuthApproval>> = _pending.asStateFlow()

    /** Track a newly surfaced tier-2 challenge. Last write per relay wins. */
    fun add(approval: PendingAuthApproval) {
        _pending.update { it.putting(approval.relayUrl, approval) }
    }

    /**
     * Settle a pending challenge with the user's pick. Removes the entry
     * BEFORE completing the deferred so the suspended signer wakes exactly
     * once. Returns false if there was nothing pending for [relayUrl].
     */
    fun resolve(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ): Boolean {
        val approval = _pending.value[relayUrl] ?: return false
        _pending.update { it.removing(relayUrl) }
        return approval.decision.complete(scope)
    }

    /**
     * Auto-approve any pending challenge whose relay is now tier-1 (present in
     * [trusted]).
     *
     * Fixes the cold-boot race: on startup an AUTH-required DM-inbox relay
     * (kind:10050) often sends its challenge before the account's own kind:10050
     * list has been fetched, so [AuthApprovalPolicy.classify] — which reads the
     * trusted set exactly once — sees an empty set and surfaces a tier-2 prompt
     * for a relay that should have auto-signed. Nothing re-evaluates that
     * pending decision when the list finally loads, leaving a spurious banner.
     *
     * When the DM-inbox set updates, call this: every pending relay now in
     * [trusted] is settled with [AuthApprovalScope.ONCE] — sign this session but
     * do NOT persist, since it is trusted by identity (it is the user's own
     * inbox), not by an explicit user grant.
     */
    fun autoApproveNowTrusted(trusted: Set<NormalizedRelayUrl>) {
        // Snapshot the relays to settle so we don't mutate while iterating the
        // live map. ONCE = sign this session, never persist (trusted by
        // identity, not by an explicit user grant).
        _pending.value.keys
            .filter { it in trusted }
            .forEach { relayUrl -> resolve(relayUrl, AuthApprovalScope.ONCE) }
    }

    /**
     * Cancel every pending challenge, completing each with
     * [AuthApprovalScope.BLOCKED] so suspended signers unblock (and drop the
     * AUTH) rather than hang. Used on logout / account switch.
     */
    fun cancelAll() {
        val snapshot = _pending.value
        _pending.value = persistentMapOf()
        snapshot.values.forEach { it.decision.complete(AuthApprovalScope.BLOCKED) }
    }
}

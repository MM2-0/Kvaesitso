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

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthInputs
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthResolver
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict

/**
 * Decides whether Amethyst should authenticate with a given relay (NIP-42), for one account.
 *
 * Precedence (see [RelayAuthResolver]): blocked-relay list → per-relay override → this session's
 * in-memory grants ([sessionGrants]) → global [globalPolicy] → prompt-if-attributable-else-deny.
 * Under [RelayAuthPolicy.CUSTOM] the
 * [customToggles] gate each category, using [isFollowed] to split the counterparties carried in the
 * [RelayAuthContext] into followed vs. stranger.
 *
 * Venues come in two shapes and both count as "a room I joined" for the
 * [RelayAuthCustomToggles.myRelaysAndVenues] toggle: [isTrustedVenue] recognizes a venue *id* named
 * by a purpose (a NIP-28 chat, a NIP-72 community, a NIP-53 stream, a NIP-29 group id, a Concord
 * community id), while [isVenueHostRelay] recognizes the relay that *hosts* one — the only signal
 * available for a room whose traffic never names it in a way the deriver can see, or whose challenge
 * arrives before its subscription does. [isTrustedVenue] is asked about the (relay, venue) pair
 * rather than the id alone because a NIP-29 group id means nothing without its host relay.
 */
class RelayAuthPermissionLedger(
    val store: RelayAuthPermissionStore,
    val globalPolicy: () -> RelayAuthPolicy,
    /**
     * Relays this account already approved during this run of the app. Answering the prompt without
     * the "remember" switch records the grant here, so the same relay's next reconnect is answered
     * silently instead of raising the same dialog again.
     *
     * Required, with no default, because it is shared state: one account's grants have to be the
     * same object on every AUTH path (the foreground screen and the background notification
     * consumer both decide off this ledger — see [com.vitorpamplona.amethyst.commons.model.Account]). A
     * default would let a ledger built without one quietly get a private set instead, so answers
     * given on one path would not be seen on the other and the dialog would come back anyway.
     */
    val sessionGrants: RelayAuthSessionGrants,
    val customToggles: () -> RelayAuthCustomToggles = { RelayAuthCustomToggles() },
    val isInMyRelayList: (String) -> Boolean = { false },
    val isBlocked: (String) -> Boolean = { false },
    val isFollowed: (String) -> Boolean = { false },
    val isTrustedVenue: (relayUrl: String, venueId: String) -> Boolean = { _, _ -> false },
    val isVenueHostRelay: (String) -> Boolean = { false },
) {
    /**
     * The authorization verdict for [ctx], taking the challenge's purpose into account.
     *
     * [isFirstParty] says whether this account has a reason of its own to be on the relay. It gates
     * the automatic grants only — a non-first-party challenge is never auto-allowed, but one we can
     * explain still reaches the user as a prompt.
     */
    suspend fun decide(
        ctx: RelayAuthContext,
        isFirstParty: Boolean = true,
    ): RelayAuthVerdict {
        fun isWrite(kind: AuthPurposeKind) = kind == AuthPurposeKind.SEND_DM || kind == AuthPurposeKind.NOTIFY_INBOX

        // The relay itself hosts a room this account joined — a NIP-29 relay group or a Concord
        // community. Computed apart from the purposes because those rooms are the relay's whole
        // reason to be here: their traffic is `#h`-scoped (NIP-29) or addressed to derived stream
        // keys (Concord), so an AUTH challenge that lands before the room's subscription is
        // assembled carries no venue to match — and the joined room would sit empty behind a
        // question the user already answered by joining it.
        val hostsMyVenue = isVenueHostRelay(ctx.relayUrl)

        val inputs =
            RelayAuthInputs(
                storedOverride = store.loadDecision(ctx.relayUrl),
                isBlocked = isBlocked(ctx.relayUrl),
                hasSessionGrant = sessionGrants.isGranted(ctx.relayUrl),
                policy = globalPolicy(),
                toggles = customToggles(),
                isInMyRelayList = isInMyRelayList(ctx.relayUrl),
                servesTrustedVenue =
                    hostsMyVenue ||
                        ctx.purposes.any { p ->
                            (p.kind == AuthPurposeKind.POST_VENUE || p.kind == AuthPurposeKind.READ_VENUE) &&
                                p.venues.any { isTrustedVenue(ctx.relayUrl, it) }
                        },
                // Reading a followed author's outbox.
                servesFollowedReadCounterparty =
                    ctx.purposes.any { p ->
                        p.kind == AuthPurposeKind.READ_OUTBOX && p.counterparties.any(isFollowed)
                    },
                // Messaging a followed user's inbox (DM / notification).
                servesFollowedWriteCounterparty =
                    ctx.purposes.any { p -> isWrite(p.kind) && p.counterparties.any(isFollowed) },
                // Messaging a non-followed user's inbox.
                servesStrangerWriteCounterparty =
                    ctx.purposes.any { p -> isWrite(p.kind) && p.counterparties.any { !isFollowed(it) } },
                // Hosting a joined room is itself an explanation, and the only one available when the
                // challenge arrives before any of that room's filters do. Without it, a user on
                // "decide per relay" (or with the venue toggle off) would get a silent denial for a
                // room they joined instead of the question. MY_INBOX and THREAD likewise name no
                // counterparty by design — the relay is holding back the user's *own* inbox or the
                // conversation on screen — and are fully explainable, so they too must reach ASK.
                hasAttributablePurpose =
                    hostsMyVenue ||
                        ctx.purposes.any {
                            it.kind == AuthPurposeKind.MY_OWN_RELAY ||
                                it.kind == AuthPurposeKind.OTHER ||
                                it.kind == AuthPurposeKind.MY_INBOX ||
                                it.kind == AuthPurposeKind.THREAD ||
                                it.counterparties.isNotEmpty() ||
                                it.venues.isNotEmpty()
                        },
                isFirstParty = isFirstParty,
            )
        return RelayAuthResolver.resolve(inputs)
    }

    /** Convenience for callers with no purpose context (e.g. a bare challenge). */
    suspend fun decide(relayUrl: String): RelayAuthVerdict = decide(RelayAuthContext(relayUrl))

    /**
     * Records why [ctx]'s relay was authenticated with, so the settings screen can show the
     * counterparties behind each grant. Only purposes that name counterparties are recorded.
     */
    suspend fun recordGrant(ctx: RelayAuthContext) {
        val additions =
            ctx.purposes
                .filter { it.counterparties.isNotEmpty() }
                .associate { it.kind to it.counterparties }
        if (additions.isNotEmpty()) store.recordUse(ctx.relayUrl, additions)
    }

    /**
     * Remembers a "log in" answer for [relayUrl] until the app is restarted, so the relay's next
     * reconnect doesn't ask again. Nothing is written to disk — see [RelayAuthSessionGrants].
     *
     * Refused, returning false, while [globalPolicy] is [RelayAuthPolicy.NEVER]: that is the
     * switch-it-all-off answer, and a session grant outranks the policy (see [RelayAuthResolver]),
     * so recording one here would quietly re-enable the very thing the user just turned off. The
     * settings screen clears existing grants when the policy is set to NEVER; this stops a *new*
     * one being written afterwards — which the undo on "forget this login" otherwise did, because
     * its snackbar carries an action label and so sits on screen indefinitely, long enough for the
     * policy to change underneath it.
     *
     * Only the policy needs this guard. A stored override arriving in the same window is
     * self-protecting: it is ranked *above* the grant, so an ALLOW or DENY written meanwhile
     * decides the relay either way. So is the block list, which outranks everything.
     */
    fun grantForSession(relayUrl: String): Boolean {
        if (globalPolicy() == RelayAuthPolicy.NEVER) return false
        sessionGrants.grant(relayUrl)
        return true
    }

    /** Forgets this session's grant for [relayUrl], so the next challenge is decided from scratch. */
    fun revokeSessionGrant(relayUrl: String) = sessionGrants.revoke(relayUrl)

    /**
     * Forgets this session's grants for every relay in [blockedRelayUrls] — the kind-10006 block
     * list, which outranks everything else on the decision path.
     *
     * Blocking already denies while it is in force, so this is about what happens *after* it is
     * lifted: without it, unblocking would resume authenticating off an answer given before the
     * block. The weaker "not now, and remember it" drops the grant too (see [setDecision]), so the stronger
     * signal has to as well.
     */
    fun revokeSessionGrantsFor(blockedRelayUrls: Collection<String>) = blockedRelayUrls.forEach(sessionGrants::revoke)

    /**
     * Stores a per-relay override for [relayUrl].
     *
     * Also drops any session grant: the stored decision is now the whole answer for this relay, so
     * leaving the transient one behind would let a later [clearDecision] ("follows your rules again")
     * silently keep authenticating off a grant the user can no longer see.
     *
     * The two writes are not atomic — [RelayAuthPermissionCache] only publishes an override to memory
     * *after* its disk write returns — so a challenge arriving between them must never see neither.
     * Which side to fail on depends on the decision:
     * - **DENY** revokes first. The window then asks or denies, never signs: a user who just pressed
     *   "not now" with the remember switch on must not get one more AUTH out of the grant they are
     *   replacing.
     * - **ALLOW** revokes last, so the grant still covers the window. Revoking first left the relay
     *   momentarily undecided, which re-prompted the user who had just pressed "always" — the very
     *   dialog this whole feature exists to stop.
     */
    suspend fun setDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) {
        if (decision == RelayAuthDecision.DENY) sessionGrants.revoke(relayUrl)
        store.storeDecision(relayUrl, decision)
        sessionGrants.revoke(relayUrl)
    }

    /** Removes the per-relay override for [relayUrl], reverting to the global policy. */
    suspend fun clearDecision(relayUrl: String) {
        sessionGrants.revoke(relayUrl)
        store.clearDecision(relayUrl)
    }

    /** All per-relay overrides — for the settings screen. */
    suspend fun allDecisions(): Map<String, RelayAuthDecision> = store.allDecisions()
}

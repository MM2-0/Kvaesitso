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
package com.vitorpamplona.amethyst.commons.relayauth

/**
 * The per-situation switches applied under [RelayAuthPolicy.CUSTOM]. Each independently authorizes
 * one category of relay; a situation with no matching toggle falls through to a prompt.
 *
 * @param myRelaysAndVenues your own relays, plus venues (public chats, NIP-72 communities, live
 *   streams, NIP-29 relay groups, Concord communities) you've joined, subscribed to, or favorited.
 * @param readFollows a relay serving the outbox of someone you follow (to download their posts).
 * @param messageFollows a relay serving the inbox of someone you follow (to send DMs, replies,
 *   notifications).
 * @param messageStrangers a relay serving the inbox of someone you *don't* follow. Off by default —
 *   sending to a stranger otherwise prompts.
 */
data class RelayAuthCustomToggles(
    val myRelaysAndVenues: Boolean = true,
    val readFollows: Boolean = true,
    val messageFollows: Boolean = true,
    val messageStrangers: Boolean = false,
)

/**
 * Everything the resolver needs to decide an auth challenge, gathered by the host (which owns
 * the blocked-relay list, the user's relay lists, and the follow graph). Kept as plain values
 * so the decision itself is pure and unit-testable without any account/relay wiring.
 *
 * @param storedOverride an explicit per-relay decision the user set previously, or null.
 * @param hasSessionGrant the user already answered "log in" for this relay during this run of the
 *   app, without asking to remember it permanently. Held in memory only, so it dies with the process.
 * @param isBlocked the relay is on the user's blocked-relay list (kind 10006).
 * @param policy the top-level [RelayAuthPolicy].
 * @param toggles the [RelayAuthCustomToggles] applied when [policy] is [RelayAuthPolicy.CUSTOM].
 * @param isInMyRelayList the relay is in the user's own relay list.
 * @param servesTrustedVenue this relay hosts a venue the user has joined, subscribed to, or
 *   favorited — a public chat, a NIP-72 community, a live stream, a NIP-29 relay group, or a Concord
 *   community. True either because a purpose names one of those venues or because the relay itself is
 *   a joined room's host (a group/community relay serves nothing else).
 * @param servesFollowedReadCounterparty a followed user's outbox is served here (reading them).
 * @param servesFollowedWriteCounterparty a followed user's inbox is served here (messaging them).
 * @param servesStrangerWriteCounterparty a non-followed user's inbox is served here (messaging them).
 * @param hasAttributablePurpose we know *why* this relay wants auth (so a prompt can explain it).
 *   When false, an unresolved challenge is denied silently rather than prompting.
 * @param isFirstParty this account has a reason of its *own* to be on this relay — it is publishing
 *   there, a subscription there reads its own inbox/outbox, or the relay is in its own relay list.
 *   False means the only reason we are here belongs to somebody else (another logged-in account's
 *   traffic, or a followed author whose outbox happens to live here). Gates the *automatic* grants
 *   only, and only for the categories it can gate without emptying them (see [RelayAuthResolver]):
 *   a non-first-party challenge is never auto-allowed there, but it still reaches the user as a
 *   prompt rather than a silent denial.
 */
data class RelayAuthInputs(
    val storedOverride: RelayAuthDecision?,
    val isBlocked: Boolean,
    val hasSessionGrant: Boolean = false,
    val policy: RelayAuthPolicy,
    val toggles: RelayAuthCustomToggles,
    val isInMyRelayList: Boolean,
    val servesTrustedVenue: Boolean,
    val servesFollowedReadCounterparty: Boolean,
    val servesFollowedWriteCounterparty: Boolean,
    val servesStrangerWriteCounterparty: Boolean,
    val hasAttributablePurpose: Boolean,
    val isFirstParty: Boolean = true,
)

/**
 * Pure NIP-42 auth decision. Precedence, highest first:
 *
 * 1. Blocked-relay list → [RelayAuthVerdict.DENY] (never reveal identity to a blocked relay).
 * 2. Explicit per-relay override → honor it.
 * 3. [RelayAuthInputs.hasSessionGrant] → [RelayAuthVerdict.ALLOW]. Ranked *below* the stored override
 *    so a later "never" answer — the only way a DENY can be written for a relay already granted this
 *    session — takes effect immediately instead of losing to the in-memory grant.
 * 4. Top-level [RelayAuthPolicy]:
 *    - [RelayAuthPolicy.NEVER] → DENY
 *    - [RelayAuthPolicy.ALWAYS] → ALLOW
 *    - [RelayAuthPolicy.CUSTOM] → ALLOW if any *enabled* [RelayAuthCustomToggles] category matches
 *      this relay (own relays/venues, reading follows, messaging follows, messaging strangers);
 *      else fall through
 * 5. Fall-through → [RelayAuthVerdict.ASK] when the purpose is known, otherwise DENY.
 *
 * Most [RelayAuthPolicy.CUSTOM] grants additionally require [RelayAuthInputs.isFirstParty]: under
 * "decide per relay" an account never reveals its identity *without being asked* on a relay it has no
 * reason of its own to be on, which is what keeps a bystander account off a relay only another account
 * uses. It deliberately does not suppress the question — a non-first-party challenge we can explain
 * falls through to ASK, so the user decides rather than getting a silent denial they never see.
 *
 * [RelayAuthCustomToggles.readFollows] is the one category exempt from that gate, because the gate is
 * unsatisfiable there rather than merely strict: reading a followed author means talking to *their*
 * outbox relay, which is by definition not one we publish to, subscribe to for our own inbox, or list.
 * See [customAllows].
 *
 * [RelayAuthPolicy.ALWAYS] is NOT gated this way: it means what it says, every relay that asks. Users
 * who want the narrower "only the relays I actually use" behaviour choose CUSTOM.
 */
object RelayAuthResolver {
    fun resolve(inputs: RelayAuthInputs): RelayAuthVerdict {
        if (inputs.isBlocked) return RelayAuthVerdict.DENY

        inputs.storedOverride?.let {
            return when (it) {
                RelayAuthDecision.ALLOW -> RelayAuthVerdict.ALLOW
                RelayAuthDecision.DENY -> RelayAuthVerdict.DENY
            }
        }

        // The user answered this exact question, for this exact relay, earlier in this session. Not
        // gated on isFirstParty: an explicit answer outranks every inference we would otherwise make
        // about whether the account belongs here.
        if (inputs.hasSessionGrant) return RelayAuthVerdict.ALLOW

        return when (inputs.policy) {
            RelayAuthPolicy.NEVER -> RelayAuthVerdict.DENY
            // Unconditional, by design: "Always log in" means every relay that asks. Narrowing it to the
            // relays this account uses is what CUSTOM ("decide per relay") is for — gating ALWAYS as well
            // left the user no way to express "just authenticate everywhere" and, on a fresh install with
            // a large follow list, produced a prompt for each of the 250+ third-party outbox relays.
            RelayAuthPolicy.ALWAYS -> RelayAuthVerdict.ALLOW
            RelayAuthPolicy.CUSTOM ->
                if (customAllows(inputs)) RelayAuthVerdict.ALLOW else fallThrough(inputs)
        }
    }

    /**
     * Whether an enabled [RelayAuthCustomToggles] category covers this relay.
     *
     * [RelayAuthCustomToggles.readFollows] is checked *before* the [RelayAuthInputs.isFirstParty]
     * gate because that gate is unsatisfiable for it, not merely strict. "I'm reading someone I
     * follow" describes their outbox relay: not one we publish to, not one serving our own
     * inbox/outbox, not one on our list — so `isFirstParty` is false by construction and gating the
     * category made it unreachable. Every follow's outbox relay prompted even with the toggle on, and
     * the only challenges it ever granted were ones `myRelaysAndVenues` already covered.
     *
     * Exempting it is safe in the way the gate is meant to be: the follow graph consulted is *this*
     * account's, so no other account's traffic can conjure a match. What it can match is another
     * logged-in account reading an author we follow too — and the cost of that is an AUTH on a relay
     * we would be reading that same author from anyway, which is what the toggle asks for.
     *
     * Every other category keeps the gate, where it costs them nothing: our own relay list and our
     * joined rooms' hosts are first-party by definition, and a pending event of ours makes its
     * destination first-party too. That is precisely what stops a bystander account being
     * auto-authenticated — and billed — on a paid inbox relay because *another* account's outgoing
     * DM happens to name someone we follow.
     */
    private fun customAllows(inputs: RelayAuthInputs): Boolean {
        val t = inputs.toggles
        if (t.readFollows && inputs.servesFollowedReadCounterparty) return true
        if (!inputs.isFirstParty) return false

        return (t.myRelaysAndVenues && (inputs.isInMyRelayList || inputs.servesTrustedVenue)) ||
            (t.messageFollows && inputs.servesFollowedWriteCounterparty) ||
            (t.messageStrangers && inputs.servesStrangerWriteCounterparty)
    }

    private fun fallThrough(inputs: RelayAuthInputs): RelayAuthVerdict = if (inputs.hasAttributablePurpose) RelayAuthVerdict.ASK else RelayAuthVerdict.DENY
}

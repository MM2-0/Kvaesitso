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
 * Why Amethyst is about to authenticate (NIP-42) with a relay. Carried to the decision
 * point so we can (a) tell the user *why* an auth is requested and (b) apply follow-based
 * trust against the counterparties a relay serves.
 *
 * Counterparty pubkeys are stored as hex strings to keep this module free of protocol types;
 * display names/avatars are resolved elsewhere at render time.
 */
enum class AuthPurposeKind {
    /** Delivering a NIP-17 private message to a recipient's DM inbox (kind 10050). */
    SEND_DM,

    /** Delivering a public reply/mention/reaction to a recipient's NIP-65 inbox (read relays). */
    NOTIFY_INBOX,

    /** Reading an author's posts from their NIP-65 outbox (write relays). */
    READ_OUTBOX,

    /** Posting into a venue — a NIP-28 public chat or a NIP-72 community — hosted on this relay. */
    POST_VENUE,

    /** Reading a venue's content (public chat / community) from this relay. */
    READ_VENUE,

    /**
     * Reading what other people addressed to *us* — notifications, nutzaps, incoming DMs. These
     * subscriptions are `#p` = me with no `authors`, so they match no tag-shape rule; they are only
     * recognizable from the SubPurpose the subscription declared.
     */
    MY_INBOX,

    /**
     * Reading a conversation the user has open — the thread itself, or the reactions/zaps/reposts on
     * its notes. Fetched by `#e` against note ids, which is indistinguishable by shape from reading a
     * NIP-28 channel, so this too is only recognizable from the declared SubPurpose.
     */
    THREAD,

    /** The relay is in the user's own relay list. */
    MY_OWN_RELAY,

    /** We're actively using this relay but couldn't attribute a specific purpose (safety net so we
     *  prompt instead of silently failing). */
    OTHER,
}

/**
 * A single reason a relay connection needs auth. [counterparties] holds the people it concerns
 * (pubkeys, for DM/notify/outbox purposes); [venues] holds venue identifiers (channel event-ids or
 * community `a`-addresses, for [AuthPurposeKind.POST_VENUE]/[AuthPurposeKind.READ_VENUE]).
 */
data class AuthPurpose(
    val kind: AuthPurposeKind,
    val counterparties: Set<String> = emptySet(),
    val venues: Set<String> = emptySet(),
    /**
     * Event ids of the conversation being read, for [AuthPurposeKind.THREAD]. Kept separate from
     * [venues] because a thread is not a room: these are note ids, and the only use for them is to
     * resolve *whose* conversation it is at render time, so the prompt can say "your conversation
     * with Alice" instead of "this conversation" about something the user may not have on screen.
     */
    val notes: Set<String> = emptySet(),
)

/** The relay plus every live reason we currently have to auth with it. */
data class RelayAuthContext(
    val relayUrl: String,
    val purposes: List<AuthPurpose> = emptyList(),
)

/**
 * The runtime verdict for an auth challenge. Distinct from [RelayAuthDecision], which is the
 * two-value ([RelayAuthDecision.ALLOW]/[RelayAuthDecision.DENY]) *persisted* per-relay override:
 * [ASK] is only ever a live decision, never stored.
 */
enum class RelayAuthVerdict {
    /** Sign and send the NIP-42 auth event. */
    ALLOW,

    /** Do not auth; do not reveal identity. */
    DENY,

    /** Prompt the user, explaining the purpose, before deciding. */
    ASK,
}

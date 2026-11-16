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
package com.vitorpamplona.amethyst.commons.relayClient.user

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag

/**
 * Narrow, read-only view of a logged-in account that the user-finder
 * subscription layer needs to route metadata / relay-list / report /
 * contact-card REQs for *other* users.
 *
 * This is deliberately NOT part of [IAccount][com.vitorpamplona.amethyst.commons.model.IAccount]:
 * `IAccount` is a behavioral capability interface for the *acting* user
 * (sending DMs, gift wraps, MLS groups). The relay hints needed to *discover
 * other users' metadata* are a separate concern, so they live on their own
 * narrow interface (ISP).
 *
 * All accessors are **snapshot getters** read fresh on every filter rebuild —
 * matching the prior direct `account.xxx.flow.value` reads. Relay-list changes
 * therefore take effect on the next subscription invalidation without any
 * captured-snapshot staleness.
 *
 * Platforms implement this on their concrete account (Android `Account`,
 * Desktop `DesktopIAccount`). Fields with no backing on a platform degrade
 * safely: Desktop has no NIP-85 trust-provider subsystem wired, so
 * [trustProvider] returns null and [declaredFollowsByOutboxRelay] returns an
 * empty map — contact-card and report discovery become best-effort there.
 */
interface UserFinderAccount {
    /** This account's own pubkey (hex). */
    val userFinderPubkeyHex: HexKey

    /** Index/discovery relays, with the platform default fallback already applied. */
    fun indexRelays(): Set<NormalizedRelayUrl>

    /** Home/write relays used for outbox discovery (nip65 + private storage + local). */
    fun outboxHomeRelays(): Set<NormalizedRelayUrl>

    /** Search relays (trusted + own search list), for the user-finder's search-tier fallback. */
    fun searchRelays(): Set<NormalizedRelayUrl>

    /**
     * Just this account's own NIP-51 search relay list — WITHOUT the trusted-relay
     * union that [searchRelays] adds. This is the narrow set the per-note event
     * finder fans "missing event" REQs to, matching the pre-extraction
     * `account.searchRelayList` read (reusing [searchRelays] there would have
     * unintentionally widened the fan-out to trusted relays).
     */
    fun searchOnlyRelays(): Set<NormalizedRelayUrl>

    /**
     * Follow + all-mine + search relays, used by the per-note event-finder to
     * place "missing event" / "missing addressable" REQs (reactions, zaps,
     * reposts, replies) when a note references content no relay has yet placed.
     * Snapshot getter, same contract as the others.
     */
    fun followPlusAllMineWithSearchRelays(): Set<NormalizedRelayUrl>

    /** Shared-outbox / proxy relays used as the broad common fallback. */
    fun commonRelays(): Set<NormalizedRelayUrl>

    /** Home relays used specifically for NIP-51 contact-card (kind 30382) discovery. */
    fun cardHomeRelays(): Set<NormalizedRelayUrl>

    /**
     * NIP-85 trusted-assertions rank provider whose cards are fetched per profile, or null when
     * unsupported (e.g. Desktop). Fetched even when the local trust network index holds them: a
     * card newer than its last sync updates it until the next one.
     */
    fun trustProvider(): ServiceProviderTag?

    /**
     * NIP-85 follower-count rank provider, or null when unsupported (e.g. Desktop).
     * Read by the contact-card sub-assembler alongside [trustProvider].
     */
    fun followerCountProvider(): ServiceProviderTag?

    /**
     * Declared follows keyed by the relay they were declared on, used to trust
     * report authors. Empty when the platform has no follow-graph-per-relay data.
     */
    fun declaredFollowsByOutboxRelay(): Map<NormalizedRelayUrl, Set<HexKey>>
}

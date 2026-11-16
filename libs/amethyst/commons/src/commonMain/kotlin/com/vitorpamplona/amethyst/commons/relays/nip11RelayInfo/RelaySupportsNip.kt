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
package com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation

/**
 * Whether [relayInfo] affirmatively signals that its relay does NOT run NIP-29 groups: the doc
 * resolved with an explicit `supported_nips` list that lacks "29" and no `self` key (the field
 * NIP-29 relays publish so clients can verify their relay-signed group metadata — see
 * [isRelaySignedRelayGroup]). A doc with a null `supported_nips` proves nothing (still loading,
 * or the fetch failed), so it never triggers the warning.
 *
 * Takes the resolved document rather than a relay URL, which is what makes it shared: the cache
 * lookup that produces one is the front end's business, the rule applied to it is not.
 */
fun looksLikeNonNip29Relay(relayInfo: Nip11RelayInformation): Boolean = relayInfo.supported_nips?.none { it == "29" } == true && relayInfo.self == null

/**
 * Whether [channel]'s relay-signed metadata is genuinely from its host relay, per NIP-29:
 * "these are addressable events signed by the relay keypair directly … as stated by the NIP-11
 * `self` pubkey", and "relays shouldn't accept these events if they're signed by anyone else".
 *
 * So the authoritative check is `39000.author == relay.self`. When the relay publishes a `self`
 * key we enforce that strictly — this rejects a stray user-published 39000 even on a real NIP-29
 * relay. When the relay does NOT advertise `self` at all (we can't verify cryptographically), we
 * fall back to the weaker "advertises NIP-29" signal so a compliant relay that merely omits `self`
 * still works. A relay with neither fails. Reads only the resolved doc ([relayInfo]); callers
 * driving a live surface should warm it first and re-evaluate as it resolves.
 */
fun isRelaySignedRelayGroup(
    channel: RelayGroupChannel,
    relayInfo: Nip11RelayInformation,
): Boolean {
    val self = relayInfo.self
    return if (self != null) {
        channel.event?.pubKey == self
    } else {
        relayInfo.supported_nips?.any { it == "29" } == true
    }
}

/** [isRelaySignedRelayGroup] against whatever NIP-11 document the shell has cached for the host relay. */
fun isRelaySignedRelayGroup(channel: RelayGroupChannel): Boolean = LocalCache.appHost.relayInfo(channel.groupId.relayUrl)?.let { isRelaySignedRelayGroup(channel, it) } == true

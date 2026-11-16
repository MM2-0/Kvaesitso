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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Whether a given account has a *first-party* reason to authenticate (NIP-42) with a relay on the
 * shared [com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient]. Pure so the per-account
 * signing gate can be tested without a live client, signer, or Compose.
 *
 * The socket is shared by every logged-in account, so "this relay wants auth" says nothing about
 * *which* account should answer. An account should reveal its identity to a relay only when:
 *  - it is publishing its own event there ([pendingEvents] authored by it — e.g. delivering a DM to
 *    the recipient's inbox relay), or
 *  - the relay is one it configured itself ([myRelays] — its NIP-65 / DM / search / … lists, which
 *    is where its own inbox/outbox reads are routed anyway), or
 *  - the relay hosts a room the account explicitly joined ([myVenueRelays]): a NIP-29 relay group
 *    from its kind-10009 list, or a Concord community from its kind-13302 list. Neither kind of room
 *    can qualify by the two rules above. A private/closed NIP-29 group's content (kind-9 chat,
 *    kind-11 threads, …) is `#h`-scoped and served only to authenticated members, so the read never
 *    names the user; a Concord plane is authored by — and addressed to — a derived stream key, so
 *    even the wraps the account *publishes* there carry someone else's pubkey. And neither host relay
 *    is in the account's own NIP-65/DM/… lists, so [myRelays] doesn't see them either. Without this,
 *    a joined private group is refused with `auth-required` and renders empty — the group's own
 *    metadata (39000) is public and still loads, so the group appears but shows no messages.
 *
 * Crucially, an active subscription merely *naming* the account (a `#p` tag or `authors` entry) is
 * NOT a first-party reason: the app packs several accounts' pubkeys into one merged filter and fans
 * it out to the union of everyone's relays, so account B's pubkey routinely rides a subscription to
 * account A's paid relay. Trusting that dragged bystander accounts (e.g. into inbox.nostr.wine's
 * AUTH, and its bill). Genuine own-inbox reads still qualify via [myRelays] — the relay serving them
 * is by definition in the account's own list.
 */
object RelayAuthFirstParty {
    fun hasReason(
        me: HexKey,
        relayUrl: NormalizedRelayUrl,
        pendingEvents: List<Event>,
        myRelays: Set<NormalizedRelayUrl>,
        myVenueRelays: Set<NormalizedRelayUrl> = emptySet(),
    ): Boolean {
        if (pendingEvents.any { it.pubKey == me }) return true
        if (relayUrl in myRelays) return true
        return relayUrl in myVenueRelays
    }
}

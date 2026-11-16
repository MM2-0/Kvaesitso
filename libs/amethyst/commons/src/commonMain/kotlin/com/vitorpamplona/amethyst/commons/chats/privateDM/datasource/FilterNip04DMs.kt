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
package com.vitorpamplona.amethyst.commons.chats.privateDM.datasource

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.dialableReadRelays
import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Where a conversation's NIP-04 DMs flow, resolved via the outbox model and **scoped per relay** so
 * every filter only names the keys that actually own the relay it is sent to.
 *
 * Each map is `relay -> the counterpart keys to name in that relay's filter`:
 *  - [toMeRelays] queries messages **to me** (`authors=[those keys], #p=[me]`). A relay appears here
 *    when it is my inbox (then the key set is the whole group) and/or a counterpart's outbox (then
 *    the key set is just the counterparts who publish there).
 *  - [fromMeRelays] queries messages **from me** (`authors=[me], #p=[those keys]`). A relay appears
 *    here when it is my outbox (then the key set is the whole group) and/or a counterpart's inbox
 *    (then the key set is just the counterparts who read there).
 *
 * Scoping the key set per relay is what keeps us from sending, e.g., `authors=[bob]` to a relay that
 * is only charlie's — a filter that relay has no reason to serve.
 */
class Nip04DmRelayRouting(
    val toMeRelays: Map<NormalizedRelayUrl, Set<HexKey>>,
    val fromMeRelays: Map<NormalizedRelayUrl, Set<HexKey>>,
) {
    val all: Set<NormalizedRelayUrl> get() = toMeRelays.keys + fromMeRelays.keys
}

private fun addAll(
    map: MutableMap<NormalizedRelayUrl, MutableSet<HexKey>>,
    relays: Collection<NormalizedRelayUrl>,
    keys: Collection<HexKey>,
) = relays.forEach { map.getOrPut(it) { mutableSetOf() }.addAll(keys) }

fun nip04DmRelayRouting(
    group: Set<HexKey>?,
    account: Account?,
): Nip04DmRelayRouting? {
    if (group.isNullOrEmpty() || account == null) return null

    val userOutboxRelays = account.homeRelays.flow.value
    val userInboxRelays = account.dmRelays.flow.value

    // relay -> counterpart keys whose messages-to-me we ask that relay for (authors set, #p=[me]).
    val toMe = mutableMapOf<NormalizedRelayUrl, MutableSet<HexKey>>()
    // relay -> counterpart keys whose copy of my messages we ask that relay for (#p set, authors=[me]).
    val fromMe = mutableMapOf<NormalizedRelayUrl, MutableSet<HexKey>>()

    // My own relays carry the whole conversation: my inbox holds everyone's messages to me, my outbox
    // holds all of mine. Both filters name the full group on these relays.
    addAll(toMe, userInboxRelays, group)
    addAll(fromMe, userOutboxRelays, group)

    // Each counterpart's own relays only get a filter naming that counterpart: their outbox (where
    // they publish their messages to me) and their inbox (where they keep my messages to them).
    group.forEach {
        val authorHomeRelayEventAddress = AdvertisedRelayListEvent.createAddressTag(it)
        val authorHomeRelayEvent = (LocalCache.getAddressableNoteIfExists(authorHomeRelayEventAddress)?.event as? AdvertisedRelayListEvent)

        val outbox =
            authorHomeRelayEvent?.dialableWriteRelays()
                ?: LocalCache.getUserIfExists(it)?.allUsedRelaysOrNull()
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: emptyList()

        val inbox =
            authorHomeRelayEvent?.dialableReadRelays()
                ?: LocalCache.getUserIfExists(it)?.allUsedRelaysOrNull()
                ?: LocalCache.relayHints.hintsForKey(it).ifEmpty { null }
                ?: emptyList()

        addAll(toMe, outbox, listOf(it))
        addAll(fromMe, inbox, listOf(it))
    }

    return Nip04DmRelayRouting(toMe, fromMe)
}

private fun toMeFilter(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    account: Account,
    since: Long?,
    until: Long?,
    limit: Int?,
) = RelayBasedFilter(
    relay = relay,
    filter =
        ExplainedFilter(
            purpose = SubPurpose.DIRECT_MESSAGES,
            kinds = listOf(EncryptedDmEvent.KIND),
            authors = authors.toList(),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
            since = since,
            until = until,
            limit = limit,
        ),
)

private fun fromMeFilter(
    relay: NormalizedRelayUrl,
    pTags: Set<HexKey>,
    account: Account,
    since: Long?,
    until: Long?,
    limit: Int?,
) = RelayBasedFilter(
    relay = relay,
    filter =
        ExplainedFilter(
            purpose = SubPurpose.DIRECT_MESSAGES,
            kinds = listOf(EncryptedDmEvent.KIND),
            authors = listOf(account.userProfile().pubkeyHex),
            tags = mapOf("p" to pTags.toList()),
            since = since,
            until = until,
            limit = limit,
        ),
)

/** Live-tail filters: everything since [windowStart], open-ended at the top (new messages keep arriving). */
fun filterNip04DMs(
    group: Set<HexKey>?,
    account: Account?,
    windowStart: Long,
): List<RelayBasedFilter>? {
    if (group.isNullOrEmpty() || account == null) return null
    val relays = nip04DmRelayRouting(group, account) ?: return null
    return relays.toMeRelays.map { (relay, authors) -> toMeFilter(relay, authors, account, since = windowStart, until = null, limit = null) } +
        relays.fromMeRelays.map { (relay, pTags) -> fromMeFilter(relay, pTags, account, since = windowStart, until = null, limit = null) }
}

/**
 * History filters: a bounded backward page per relay. Each relay is asked for [limit] events older
 * than [untilFor]`(relay)` (no `since`), so it can be paged down to empty independently. The author /
 * `#p` key set per relay comes straight from [relays], so each relay only sees the keys it owns.
 */
fun filterNip04DMsHistory(
    account: Account,
    relays: Nip04DmRelayRouting,
    limit: Int,
    untilFor: (NormalizedRelayUrl) -> Long?,
): List<RelayBasedFilter> =
    relays.toMeRelays.map { (relay, authors) -> toMeFilter(relay, authors, account, since = null, until = untilFor(relay), limit = limit) } +
        relays.fromMeRelays.map { (relay, pTags) -> fromMeFilter(relay, pTags, account, since = null, until = untilFor(relay), limit = limit) }

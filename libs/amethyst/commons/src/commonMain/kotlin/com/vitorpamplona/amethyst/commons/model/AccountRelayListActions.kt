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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/** The account's own relay lists that a relay seen elsewhere can be added to in one tap. */
enum class MyRelayList {
    OUTBOX,
    INBOX,
    DM_INBOX,
    SEARCH,
    RELAY_FEEDS,
    INDEXER,
    BROADCAST,
    PRIVATE_HOME,
    PROXY,
    TRUSTED,
    ;

    /**
     * False for the NIP-65 outbox and inbox when [relay] is on the local network: those lists are
     * where other people go to read and reach this user, and nobody else can open a local relay.
     *
     * This only gates the one-tap "add to my list" action. A local relay the user already has in
     * their own list (typed into the settings screen, or published by another client) is theirs:
     * it is read, shown and republished like any other entry.
     */
    fun accepts(relay: NormalizedRelayUrl): Boolean = !(isNip65() && relay.isLocalHost())

    fun isNip65(): Boolean = this == OUTBOX || this == INBOX
}

/**
 * The relays in [list], read exactly as its settings screen reads them: never the defaults some
 * lists fall back to, so adding one relay never saves the defaults along with it.
 */
fun Account.relaysIn(list: MyRelayList): List<NormalizedRelayUrl> =
    when (list) {
        MyRelayList.OUTBOX -> nip65RelayList.ownWriteRelays()
        MyRelayList.INBOX -> nip65RelayList.ownReadRelays()
        MyRelayList.DM_INBOX -> dmRelayList.getDMRelayList()?.allRelays() ?: emptyList()
        MyRelayList.SEARCH -> searchRelayList.flowNoDefaults.value.toList()
        MyRelayList.RELAY_FEEDS -> favoriteRelayList.flow.value.toList()
        MyRelayList.INDEXER -> indexerRelayList.flowNoDefaults.value.toList()
        MyRelayList.BROADCAST -> broadcastRelayList.flow.value.toList()
        MyRelayList.PRIVATE_HOME -> privateStorageRelayList.flow.value.toList()
        MyRelayList.PROXY -> proxyRelayList.flow.value.toList()
        MyRelayList.TRUSTED -> trustedRelayList.flow.value.toList()
    }

/** Every list of [MyRelayList] that already holds [relay]. */
fun Account.listsContaining(relay: NormalizedRelayUrl): Set<MyRelayList> = MyRelayList.entries.filterTo(mutableSetOf()) { relay in relaysIn(it) }

/**
 * Appends [relay] to [list] and publishes the list through the same save its settings screen uses,
 * so the side effects (republishing account events to new outboxes, key packages to new key
 * package relays) match. Does nothing when the relay is already there or [list] does not
 * [MyRelayList.accepts] it.
 */
suspend fun Account.addRelayTo(
    list: MyRelayList,
    relay: NormalizedRelayUrl,
) {
    if (!list.accepts(relay)) return

    when (list) {
        MyRelayList.OUTBOX -> {
            addToNip65(relay, AdvertisedRelayType.WRITE)
            return
        }

        MyRelayList.INBOX -> {
            addToNip65(relay, AdvertisedRelayType.READ)
            return
        }

        else -> {}
    }

    val current = relaysIn(list)
    if (relay in current) return
    val updated = current + relay

    when (list) {
        MyRelayList.DM_INBOX -> saveDMRelayList(updated)
        MyRelayList.SEARCH -> saveSearchRelayList(updated)
        MyRelayList.RELAY_FEEDS -> saveFavoriteRelayList(updated)
        MyRelayList.INDEXER -> saveIndexerRelayList(updated)
        MyRelayList.BROADCAST -> saveBroadcastRelayList(updated)
        MyRelayList.PRIVATE_HOME -> savePrivateOutboxRelayList(updated)
        MyRelayList.PROXY -> saveProxyRelayList(updated)
        MyRelayList.TRUSTED -> saveTrustedRelayList(updated)
        MyRelayList.OUTBOX, MyRelayList.INBOX -> {}
    }
}

private suspend fun Account.addToNip65(
    relay: NormalizedRelayUrl,
    type: AdvertisedRelayType,
) {
    val current = nip65RelayList.getNIP65RelayList()?.relays() ?: emptyList()
    val updated = nip65WithRelay(current, relay, type) ?: return
    sendNip65RelayList(updated)
}

/**
 * The NIP-65 entries for a [writes] and a [reads] set, as the settings screen saves them: one tag
 * per relay, BOTH when it is in both sets. Fed the sets the screen loaded, it rebuilds the list it
 * loaded, local relays included.
 */
fun nip65ListOf(
    writes: Set<NormalizedRelayUrl>,
    reads: Set<NormalizedRelayUrl>,
): List<AdvertisedRelayInfo> =
    writes.union(reads).map {
        val type =
            if (it in writes && it in reads) {
                AdvertisedRelayType.BOTH
            } else if (it in writes) {
                AdvertisedRelayType.WRITE
            } else {
                AdvertisedRelayType.READ
            }
        AdvertisedRelayInfo(it, type)
    }

/**
 * [all] with [relay] advertised for [type], or null when it already is or is a local relay.
 * NIP-65 keeps one `r` tag per relay with a read/write marker, so a relay already advertised for
 * the other direction becomes BOTH (and any duplicate tags for it collapse into that one) instead
 * of gaining a tag. A local relay is never ADDED this way (see [MyRelayList.accepts]), but one
 * already in the user's own list stays: republishing must never silently lose an entry.
 */
fun nip65WithRelay(
    all: List<AdvertisedRelayInfo>,
    relay: NormalizedRelayUrl,
    type: AdvertisedRelayType,
): List<AdvertisedRelayInfo>? {
    if (relay.isLocalHost()) return null

    val existing = all.filter { it.relayUrl == relay }

    val alreadyThere = existing.any { if (type == AdvertisedRelayType.WRITE) it.type.isWrite() else it.type.isRead() }
    if (alreadyThere) return null

    if (existing.isEmpty()) return all + AdvertisedRelayInfo(relay, type)

    val first = existing.first()
    return all.mapNotNull {
        when {
            it.relayUrl != relay -> it
            it === first -> AdvertisedRelayInfo(relay, AdvertisedRelayType.BOTH)
            else -> null
        }
    }
}

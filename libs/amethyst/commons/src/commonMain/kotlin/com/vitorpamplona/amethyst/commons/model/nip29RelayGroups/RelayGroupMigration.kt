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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Another relay that people this group trusts now list for the same group id — a sign the group
 * moved there, or forked (NIP-29 §Detecting migrations and forks). [admins] are the group's
 * (cached) admins pointing at [relay]; [friends] the user's follows doing so. [stillListsCurrent]
 * holds everyone in either set who ALSO still lists the relay we use, which reads more like a
 * replica/fork than a move.
 */
@Immutable
data class RelayGroupRelocation(
    val relay: NormalizedRelayUrl,
    val admins: Set<HexKey>,
    val friends: Set<HexKey>,
    val stillListsCurrent: Set<HexKey>,
) {
    /** How many distinct people point at [relay]. */
    val supporters: Int get() = (admins + friends).size

    /** Everyone pointing there dropped our relay: most likely a migration rather than a fork. */
    val looksLikeMove: Boolean get() = (admins + friends).all { it !in stillListsCurrent }
}

/**
 * NIP-29 migration/fork detection. A group's identity is its id plus the relay that enforces it,
 * so when the group's admins (or the user's friends) update their kind-10009 list to put the same
 * id on another relay, the client should tell the user and offer to follow it there.
 *
 * Only public `group` tags are readable: other people's private items are encrypted to them.
 */
object RelayGroupMigrationDetector {
    /**
     * Scans [lists] (kind-10009 events of [admins] and [friends]; others are ignored) for entries of
     * [current]'s group id that point at a different relay. Returns one [RelayGroupRelocation] per
     * alternative relay, admin-backed ones first, then by number of supporters. [self] is skipped —
     * our own list already tells us where we think the group is.
     */
    fun detect(
        current: GroupId,
        lists: Iterable<SimpleGroupListEvent>,
        admins: Set<HexKey>,
        friends: Set<HexKey>,
        self: HexKey? = null,
    ): List<RelayGroupRelocation> {
        val adminsByRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
        val friendsByRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
        val stillCurrent = HashSet<HexKey>()

        // Newest list per author only: an older version doesn't reflect where they are now.
        val newest = HashMap<HexKey, SimpleGroupListEvent>()
        lists.forEach { list ->
            if (list.pubKey == self) return@forEach
            if (list.pubKey !in admins && list.pubKey !in friends) return@forEach
            val previous = newest[list.pubKey]
            if (previous == null || list.createdAt > previous.createdAt) newest[list.pubKey] = list
        }

        newest.values.forEach { list ->
            val author = list.pubKey
            list.publicGroups().forEach { tag ->
                if (tag.groupId != current.id) return@forEach
                val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) ?: return@forEach
                if (relay == current.relayUrl) {
                    stillCurrent.add(author)
                } else if (author in admins) {
                    adminsByRelay.getOrPut(relay) { HashSet() }.add(author)
                } else {
                    friendsByRelay.getOrPut(relay) { HashSet() }.add(author)
                }
            }
        }

        return (adminsByRelay.keys + friendsByRelay.keys)
            .map { relay ->
                val relayAdmins = adminsByRelay[relay] ?: emptySet()
                val relayFriends = friendsByRelay[relay] ?: emptySet()
                RelayGroupRelocation(relay, relayAdmins, relayFriends, (relayAdmins + relayFriends).filterTo(HashSet()) { it in stillCurrent })
            }.sortedWith(compareByDescending<RelayGroupRelocation> { it.admins.size }.thenByDescending { it.supporters })
    }
}

/**
 * Last-known admin pubkeys per NIP-29 group, keyed by [GroupId.toKey]. NIP-29 asks clients to cache
 * these so the migration check can still read the admins' kind-10009 lists when the host relay (the
 * only source of the kind-39001 admin list) is down. A process-wide singleton mirrored to disk by
 * [com.vitorpamplona.amethyst.commons.model.preferences.RelayGroupAdminCacheStore].
 */
object RelayGroupAdminCache {
    /**
     * Most groups kept. The cache is device-global (shared by every account on the device) and is
     * filled for any group whose migration bar is shown, so it is bounded by recency of change instead
     * of by one account's kind-10009: past the cap, the entry changed least recently is dropped.
     */
    const val MAX_GROUPS = 256

    // Insertion order = recency of change (oldest first), so the cap trims from the front.
    private val admins = MutableStateFlow<Map<String, Set<HexKey>>>(emptyMap())

    val flow: StateFlow<Map<String, Set<HexKey>>> = admins

    fun adminsOf(groupId: GroupId): Set<HexKey> = admins.value[groupId.toKey()] ?: emptySet()

    /** Records the current admin set of [groupId] (no-op when unchanged or empty). */
    fun remember(
        groupId: GroupId,
        pubkeys: Set<HexKey>,
    ) {
        if (pubkeys.isEmpty()) return
        val key = groupId.toKey()
        while (true) {
            val current = admins.value
            if (current[key] == pubkeys) return
            val next = LinkedHashMap(current)
            next.remove(key)
            next[key] = pubkeys
            if (admins.compareAndSet(current, next.trimToCap())) return
        }
    }

    /**
     * Merges the map read from disk at startup UNDER the in-memory one: the disk read is async, so a
     * [remember] that landed before it completed is newer and wins. Returns the merged map.
     */
    fun restore(fromDisk: Map<String, Set<HexKey>>): Map<String, Set<HexKey>> {
        while (true) {
            val current = admins.value
            val merged = LinkedHashMap<String, Set<HexKey>>(fromDisk.size + current.size)
            merged.putAll(fromDisk)
            for ((key, value) in current) {
                merged.remove(key)
                merged[key] = value
            }
            val next = merged.trimToCap()
            if (admins.compareAndSet(current, next)) return next
        }
    }

    private fun LinkedHashMap<String, Set<HexKey>>.trimToCap(): LinkedHashMap<String, Set<HexKey>> {
        if (size > MAX_GROUPS) {
            val oldestFirst = entries.iterator()
            repeat(size - MAX_GROUPS) {
                oldestFirst.next()
                oldestFirst.remove()
            }
        }
        return this
    }

    /** Test-only: clears the cache so unit tests don't leak state into each other. */
    fun clearForTesting() {
        admins.value = emptyMap()
    }
}

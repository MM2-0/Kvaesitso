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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One account's cordn rooms, across every coordinator it talks to.
 *
 * ## Keyed by (coordinator, gid), never by gid alone
 *
 * A `gid` is unique only within one coordinator (`spec/00.md` §4) — two of them
 * can both serve `gid = "abc"` as unrelated groups with different members and
 * different ratchet trees. A map keyed by `gid` would let one answer for the
 * other, and from the user's side that reads as a room whose history changes
 * depending on which coordinator last synced. The same rule keys
 * `CordnCoordinatorRegistry` and the on-disk stores; this is the third place
 * it shows up, and it is the same rule each time.
 */
@Stable
class CordnGroupList(
    /** Whose rooms these are; handed to every room for its unread count. */
    private val accountPubKey: HexKey = "",
) {
    /** A room's identity: the coordinator that serves it, plus its `gid`. */
    data class RoomKey(
        val coordinatorPubKey: HexKey,
        val gid: String,
    )

    private val rooms = LinkedHashMap<RoomKey, CordnGroupChatroom>()

    private val _all = MutableStateFlow<List<CordnGroupChatroom>>(emptyList())

    /** Every room, for the inbox and the group list. */
    val all: StateFlow<List<CordnGroupChatroom>> = _all.asStateFlow()

    private val _revision = MutableStateFlow(0L)

    /**
     * `update` rather than `value++`: deliveries arrive off the sync loop while
     * a send can be filing one from the UI, and a read-modify-write would drop
     * bumps under exactly the interleaving the inbox needs to notice.
     */
    private fun bumpRevision() = _revision.update { it + 1 }

    /**
     * Bumps on every change the inbox cares about: a room added or dropped,
     * **and** every message filed into one.
     *
     * [all] cannot serve that second purpose, which is the trap this exists to
     * close. It re-emits only when the room *set* changes -- a message for a
     * room that already exists returns early from [getOrCreate] and never
     * reassigns it -- so a feed rebuilt off `all` alone rebuilds when a room is
     * joined and never again. The rows then keep whatever order that first
     * build gave them no matter what arrives, which is invisible until the
     * inbox is sorted by recency.
     *
     * The Concord control plane exposes its own `revision` for the same reason;
     * both are meant to be `sample()`d, since a re-sync files a burst.
     */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun getOrCreate(
        coordinatorPubKey: HexKey,
        gid: String,
    ): CordnGroupChatroom {
        val key = RoomKey(coordinatorPubKey, gid)
        rooms[key]?.let { return it }
        val room =
            CordnGroupChatroom(
                gid = gid,
                coordinatorPubKey = coordinatorPubKey,
                accountPubKey = accountPubKey,
                // The room is the only place that sees every way its newest
                // message can change, so it is the room that reports one.
                onNewestChanged = { bumpRevision() },
            )
        rooms[key] = room
        _all.value = rooms.values.toList()
        bumpRevision()
        return room
    }

    fun get(
        coordinatorPubKey: HexKey,
        gid: String,
    ): CordnGroupChatroom? = rooms[RoomKey(coordinatorPubKey, gid)]

    /**
     * Files [delivered] into its room, creating it if needed.
     *
     * Returns false when the room already held it — a re-sync re-delivers, so
     * a caller that counts unread messages must not count this one twice.
     */
    fun add(
        coordinatorPubKey: HexKey,
        gid: String,
        delivered: CordnDeliveredMessage,
    ): Boolean = getOrCreate(coordinatorPubKey, gid).add(delivered)

    /** Drops a room. The caller decides whether the stored state goes too. */
    fun forget(
        coordinatorPubKey: HexKey,
        gid: String,
    ) {
        if (rooms.remove(RoomKey(coordinatorPubKey, gid)) != null) {
            _all.value = rooms.values.toList()
            bumpRevision()
        }
    }

    /** Drops every room served by [coordinatorPubKey]. For a purge. */
    fun forgetCoordinator(coordinatorPubKey: HexKey) {
        rooms.keys.filter { it.coordinatorPubKey == coordinatorPubKey }.forEach { rooms.remove(it) }
        _all.value = rooms.values.toList()
        bumpRevision()
    }

    fun clear() {
        rooms.clear()
        _all.value = emptyList()
        bumpRevision()
    }
}

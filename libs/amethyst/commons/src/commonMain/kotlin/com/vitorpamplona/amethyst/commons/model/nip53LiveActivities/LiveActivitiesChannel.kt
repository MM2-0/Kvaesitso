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
package com.vitorpamplona.amethyst.commons.model.nip53LiveActivities

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.toNIP19OrFallback
import com.vitorpamplona.amethyst.commons.model.toNostrUriOrFallback
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache

@Stable
class LiveActivitiesChannel(
    val address: Address,
) : Channel() {
    var creator: User? = null
    var info: LiveActivitiesEvent? = null

    // Important to keep this long-term reference because LocalCache uses WeakReferences.
    var infoNote: Note? = null

    /**
     * Audio-room presence index (NIP-53 kind-10312) keyed by author
     * pubkey. Presence is replaceable (one per author), so keying on
     * the author auto-collapses heartbeat versions instead of growing
     * unbounded the way `notes` does. Empty for streaming channels
     * (kind-30311) — only kind-30312 rooms publish presence.
     *
     * Presence lives ONLY here, not in the base `notes` map: the
     * mixed-kind `notes` is dominated by chat in active rooms and
     * iterating it just to find presence is wasteful. Feeds that need
     * "is anyone live on stage in this room?" iterate this index
     * directly. See
     * [com.vitorpamplona.amethyst.commons.nests.dal.NestsFeedFilter]
     * and [com.vitorpamplona.amethyst.commons.home.dal.HomeLiveFilter].
     */
    val presenceNotes = LargeCache<HexKey, Note>()

    fun addPresenceNote(note: Note) {
        val author = note.author?.pubkeyHex ?: return
        val previous = presenceNotes.get(author)
        if (previous?.idHex == note.idHex) return
        presenceNotes.put(author, note)
        flowSet?.notes?.invalidateData()
    }

    /**
     * Drop an author's presence entry. Called by `LocalCache` when a
     * replaceable kind-10312 from this author lands in a *different*
     * room — without this eviction, the old room would keep surfacing
     * as "live" via stale presence until it drops out of the
     * freshness window.
     */
    fun removePresenceNote(author: HexKey) {
        if (!presenceNotes.containsKey(author)) return
        presenceNotes.remove(author)
        flowSet?.notes?.invalidateData()
    }

    /**
     * Drop presence entries older than [cutoff]. Without this, every
     * author who ever heartbeats in this room would leave an entry
     * here forever, even though the freshness check in
     * `NestsFeedFilter` already treats anything past
     * `PRESENCE_FRESHNESS_WINDOW_SECONDS` (10 min) as dead. Run on the
     * same schedule as [pruneOldMessages] with a generous cutoff (2×
     * the freshness window) so a presence still inside any feed's
     * window can never be pruned.
     */
    fun pruneStalePresence(cutoff: Long): Int {
        val toRemove = mutableListOf<HexKey>()
        presenceNotes.forEach { author, note ->
            val createdAt = note.event?.createdAt
            if (createdAt == null || createdAt < cutoff) toRemove.add(author)
        }
        toRemove.forEach { presenceNotes.remove(it) }
        if (toRemove.isNotEmpty()) flowSet?.notes?.invalidateData()
        return toRemove.size
    }

    fun address() = address

    override fun relays() = info?.allRelayUrls()?.toSet()?.ifEmpty { null } ?: super.relays()

    fun relayHintUrl() = relays().firstOrNull()

    fun relayHintUrls() = relays().take(3)

    fun updateChannelInfo(
        creator: User,
        channelInfo: LiveActivitiesEvent,
        channelInfoNote: Note,
    ) {
        this.info = channelInfo
        this.creator = creator
        this.infoNote = channelInfoNote
        super.updateChannelInfo()
    }

    override fun toBestDisplayName(): String = info?.title() ?: creatorName() ?: toNAddr().toShortDisplay()

    fun creatorName(): String? = creator?.toBestDisplayName()

    fun summary(): String? = info?.summary()

    fun profilePicture(): String? = info?.image()?.ifBlank { null }

    fun anyNameStartsWith(prefix: String): Boolean =
        info?.title()?.contains(prefix, true) == true ||
            info?.summary()?.contains(prefix, true) == true

    fun toNAddr() = address.toNIP19OrFallback(relayHintUrls(), info)

    fun toATag() = ATag(address, relayHintUrl())

    fun toNostrUri() = address.toNostrUriOrFallback(relayHintUrls(), info)
}

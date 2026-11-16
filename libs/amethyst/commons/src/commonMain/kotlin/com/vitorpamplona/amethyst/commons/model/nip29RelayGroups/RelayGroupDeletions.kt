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

import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The set of NIP-29 relay-group channels this device has **deleted** (kind-9008), keyed by
 * [GroupId.toKey] (`id@relay`, so a group is scoped to its host relay — group ids are only
 * unique per relay).
 *
 * A delete is terminal and destroys the group for everyone: the relay drops it and stops serving
 * its kind-39000 metadata. But the client already holds that metadata in `LocalCache`, and a Buzz
 * relay may keep re-announcing a stale kind-44100 member-added notification that would re-surface
 * the channel in the community's browse list — after a restart too, since it's re-fetched from the
 * relay. So the deletion has to be remembered client-side and the channel filtered out everywhere
 * the list is built.
 *
 * Like [BuzzChannelStars], there is no personal Nostr event for "I deleted this from my view", so
 * this is a process-wide singleton mirrored to a device-global store by the platform
 * ([com.vitorpamplona.amethyst] `RelayGroupDeletionPreferences`) and restored at startup. Deleting
 * is authoritative and terminal, so an entry is only ever added, never removed.
 */
object RelayGroupDeletions {
    private val deleted = MutableStateFlow<Set<String>>(emptySet())

    /** The deleted group keys ([GroupId.toKey]); the community view collects this to hide them. */
    val flow: StateFlow<Set<String>> = deleted

    fun isDeleted(groupKey: String): Boolean = groupKey in deleted.value

    fun isDeleted(groupId: GroupId): Boolean = isDeleted(groupId.toKey())

    /** Record [groupId] as deleted (idempotent). */
    fun markDeleted(groupId: GroupId) = markDeleted(groupId.toKey())

    /** Record [groupKey] ([GroupId.toKey]) as deleted (idempotent). */
    fun markDeleted(groupKey: String) {
        while (true) {
            val current = deleted.value
            if (groupKey in current) return
            if (deleted.compareAndSet(current, current + groupKey)) return
        }
    }

    /** Replaces the whole set — used to restore from disk at startup. */
    fun restore(keys: Set<String>) {
        deleted.value = keys
    }

    /** Test-only: clears the set so unit tests don't leak state into each other. */
    fun clearForTesting() {
        deleted.value = emptySet()
    }
}

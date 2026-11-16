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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds

/** Profiles (kind:0) for rendering, from the store and — bounded — from relays. */
object ProfileLoader {
    /** Most profiles one call fetches from relays; the rest render from the store or by npub. */
    const val MAX_PROFILE_FETCH = 100

    /**
     * Profiles for [pubKeys] as a [RenderContext]: from the store, and — when
     * [fetchMissing] — one fetch of the first [MAX_PROFILE_FETCH] missing kind:0s (in
     * [pubKeys] order, so the people listed first win) from the index + bootstrap relays.
     * A kind:3 tags thousands of people; their names are not worth thousands of fetches.
     */
    suspend fun renderContext(
        access: OneShotRelayAccess,
        pubKeys: Collection<HexKey>,
        fetchMissing: Boolean,
        timeoutMs: Long,
    ): RenderContext {
        val wanted = pubKeys.filterTo(LinkedHashSet()) { it.isValid() }
        if (wanted.isEmpty()) return RenderContext.EMPTY

        val profiles = HashMap<HexKey, UserMetadata>()
        val newest = HashMap<HexKey, Long>()

        fun collect(events: Collection<Event>) {
            events.forEach { event ->
                if (event !is MetadataEvent || event.pubKey !in wanted) return@forEach
                if ((newest[event.pubKey] ?: Long.MIN_VALUE) >= event.createdAt) return@forEach
                val metadata = event.contactMetaData() ?: return@forEach
                newest[event.pubKey] = event.createdAt
                profiles[event.pubKey] = metadata
            }
        }

        wanted.chunked(MAX_FILTER_VALUES).forEach { collect(access.query(Filter(authors = it, kinds = listOf(MetadataEvent.KIND)))) }

        val missing = (wanted - profiles.keys).take(MAX_PROFILE_FETCH)
        if (fetchMissing && missing.isNotEmpty()) {
            val filter = Filter(authors = missing, kinds = listOf(MetadataEvent.KIND), limit = missing.size)
            collect(access.fetch(access.indexRelays() + access.bootstrapRelays(), filter, timeoutMs).map { it.second })
        }
        return RenderContext(profiles)
    }

    /** Every pubkey a rendered view of [events] can name: authors and tagged users. */
    fun peopleIn(events: Collection<Event>): Set<HexKey> =
        buildSet {
            events.forEach {
                add(it.pubKey)
                addAll(it.taggedUserIds())
            }
        }
}

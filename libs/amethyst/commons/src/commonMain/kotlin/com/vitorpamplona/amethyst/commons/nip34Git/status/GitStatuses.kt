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
package com.vitorpamplona.amethyst.commons.nip34Git.status

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * The state of a NIP-34 issue, patch or PR, from the statuses the cache threads onto it (its
 * replies). Only one its author or a confirmed repository member signed counts, by the roles when
 * it was signed ([GitAuthority.isMemberAt]); anyone else's is a proposal.
 */
object GitStatuses {
    /**
     * Latest first by `created_at`; at equal timestamps the lower id wins, NIP-01's order. (gitworkshop
     * keeps whichever status it saw first, which no other client can reproduce; its notifications
     * use this same order.)
     */
    private val newest = compareBy<GitStatusEvent> { it.createdAt }.thenByDescending { it.id }

    /** The status [item] shows, or null for an item that is open by default. */
    fun latest(
        item: Note,
        authority: GitAuthority,
    ): GitStatusEvent? {
        val itemEvent = item.event ?: return null
        return latestAuthorised(item.replies.mapNotNull { it.event as? GitStatusEvent }, itemEvent, authority)
    }

    /** The latest of [statuses] about [item] that counts. */
    fun latestAuthorised(
        statuses: Collection<GitStatusEvent>,
        item: Event,
        authority: GitAuthority,
    ): GitStatusEvent? =
        statuses
            .filter { it.rootEventId() == item.id && authority.isMemberAt(it.pubKey, item, it.createdAt) }
            .maxWithOrNull(newest)

    /** The status each of [itemsById] shows, for statuses held outside the cache (tests, tools). */
    fun latestByItem(
        statuses: Collection<GitStatusEvent>,
        itemsById: Map<HexKey, Event>,
        authority: GitAuthority,
    ): Map<HexKey, GitStatusEvent> =
        statuses
            .groupBy { it.rootEventId() }
            .mapNotNull { (id, forItem) ->
                val item = itemsById[id ?: return@mapNotNull null] ?: return@mapNotNull null
                latestAuthorised(forItem, item, authority)?.let { item.id to it }
            }.toMap()

    /** Closed (1632) or applied / merged / resolved (1631). Open and draft items are not. */
    fun isClosedOrResolved(status: GitStatusEvent?): Boolean = status is GitStatusClosedEvent || status is GitStatusAppliedEvent

    fun isClosedOrResolved(
        item: Note,
        authority: GitAuthority,
    ): Boolean = isClosedOrResolved(latest(item, authority))
}

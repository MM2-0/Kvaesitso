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
package com.vitorpamplona.amethyst.commons.model.drss

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent

/** A list or folder found under a drss folder: where it sits ([parent], [depth]) and what it is. */
@Immutable
data class DrssTreeEntry(
    val address: Address,
    val parent: Address?,
    val depth: Int,
)

/**
 * Reads drss.io's folder trees (kind 30101 folders holding lists and folders) from what the cache
 * holds. A child not loaded yet is still listed, so a screen can load it; a folder that names one
 * of its ancestors is walked once.
 */
object DrssTree {
    private const val MAX_DEPTH = 8

    fun eventOf(
        address: Address,
        cache: EventCache = LocalCache,
    ): Event? = cache.getAddressableNoteIfExists(address)?.event

    /** [root] and everything under it, depth-first in tag order. */
    fun walk(
        root: Address,
        cache: EventCache = LocalCache,
    ): List<DrssTreeEntry> {
        val out = mutableListOf<DrssTreeEntry>()
        val seen = mutableSetOf<Address>()

        fun visit(
            address: Address,
            parent: Address?,
            depth: Int,
        ) {
            if (depth > MAX_DEPTH || !seen.add(address)) return
            out.add(DrssTreeEntry(address, parent, depth))
            val children =
                when (val event = eventOf(address, cache)) {
                    is DrssListFolderEvent -> event.children()
                    is DrssProvenanceFolderEvent -> event.children()
                    else -> return
                }
            children.forEach { visit(Address(it.kind, it.pubKeyHex, it.dTag), address, depth + 1) }
        }
        visit(root, null, 0)
        return out
    }

    /** The folders that hold [child] among [folders]. */
    fun parentsOf(
        child: Address,
        folders: List<DrssListFolderEvent>,
    ): List<DrssListFolderEvent> {
        val id = child.toValue()
        return folders.filter { folder -> folder.childAddressIds().contains(id) }
    }

    /**
     * Who a list or folder follows: the members of the follow sets and starter packs and the feeds
     * of the feed lists under it, read from what the cache holds. Private members of the user's own
     * sets are not read here.
     */
    fun authorsUnder(
        root: Address,
        cache: EventCache = LocalCache,
    ): Set<HexKey> {
        val out = mutableSetOf<HexKey>()
        walk(root, cache).forEach { entry ->
            when (val event = eventOf(entry.address, cache)) {
                is FollowSetEvent -> event.publicMembers().forEach { if (it is UserTag) out.add(it.pubKey) }
                is StarterPackEvent -> out.addAll(event.followIdSet())
                is DrssFeedListEvent -> out.addAll(event.feedKeys())
                else -> {}
            }
        }
        return out
    }

    /** The title a list or folder gives itself, or null. */
    fun titleOf(event: Event?): String? =
        when (event) {
            is DrssListFolderEvent -> event.title()
            is DrssFeedListEvent -> event.title()
            is DrssProvenanceFolderEvent -> event.title()
            is FollowSetEvent -> event.titleOrName()
            is StarterPackEvent -> event.title()
            is ArticleCurationSetEvent -> event.title()
            is BookmarkSetEvent -> event.titleOrName()
            is DrssEventListEvent -> event.title()
            else -> null
        }?.ifBlank { null }
}

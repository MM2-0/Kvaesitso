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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasMoreHashtagsThan
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class FilterByListParams(
    val isHiddenList: Boolean,
    val followLists: IFeedTopNavFilter?,
    val hiddenLists: LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun isNotHidden(userHex: String) = !(hiddenLists.hiddenUsers.contains(userHex) || hiddenLists.spammers.contains(userHex))

    fun isNotInMutedThread(noteEvent: Event): Boolean {
        if (hiddenLists.mutedThreads.isEmpty()) return true
        return !hiddenLists.mutedThreads.contains(noteEvent.threadRootIdOrSelf())
    }

    fun isNotInTheFuture(noteEvent: Event) = noteEvent.createdAt <= now

    fun hasExcessiveHashtags(noteEvent: Event) = hiddenLists.maxHashtagLimit > 0 && noteEvent.hasMoreHashtagsThan(hiddenLists.maxHashtagLimit)

    fun isAuthorInFollows(author: HexKey): Boolean {
        if (followLists == null) return false

        return followLists.matchAuthor(author)
    }

    fun isGlobal() = followLists is GlobalTopNavFilter

    private fun applyTopFilter(
        comingFrom: List<NormalizedRelayUrl>,
        noteEvent: Event,
    ): Boolean {
        if (followLists == null) return false

        return when (followLists) {
            is RelayTopNavFilter -> comingFrom.contains(followLists.relayUrl)
            else -> followLists.match(noteEvent)
        }
    }

    private fun applyTopFilter(
        comingFrom: List<NormalizedRelayUrl>,
        address: Address,
    ): Boolean {
        if (followLists == null) return false

        return when (followLists) {
            is RelayTopNavFilter -> comingFrom.contains(followLists.relayUrl)
            else -> followLists.matchAuthor(address.pubKeyHex)
        }
    }

    fun match(
        noteEvent: Event,
        comingFrom: List<NormalizedRelayUrl>,
    ) = (applyTopFilter(comingFrom, noteEvent)) &&
        (isHiddenList || isNotHidden(noteEvent.pubKey)) &&
        isNotInMutedThread(noteEvent) &&
        isNotInTheFuture(noteEvent) &&
        !hasExcessiveHashtags(noteEvent)

    fun match(
        address: Address?,
        comingFrom: List<NormalizedRelayUrl>,
    ) = address != null &&
        (applyTopFilter(comingFrom, address)) &&
        (isHiddenList || isNotHidden(address.pubKeyHex))

    companion object {
        fun showHiddenKey(
            selectedListName: String,
            userHex: String,
        ) = selectedListName == FollowSetEvent.blockListFor(userHex) || selectedListName == MuteListEvent.blockListFor(userHex)

        fun create(
            followLists: IFeedTopNavFilter?,
            hiddenUsers: LiveHiddenUsers,
        ): FilterByListParams =
            FilterByListParams(
                isHiddenList = followLists is MutedAuthorsByOutboxTopNavFilter || followLists is MutedAuthorsByProxyTopNavFilter,
                followLists = followLists,
                hiddenLists = hiddenUsers,
            )
    }
}

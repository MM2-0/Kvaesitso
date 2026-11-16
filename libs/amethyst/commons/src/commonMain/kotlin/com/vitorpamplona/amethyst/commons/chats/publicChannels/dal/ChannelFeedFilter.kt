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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.ChangesFlowFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.chats.isMinichatReply

class ChannelFeedFilter(
    val channel: Channel,
    val account: Account,
) : AdditiveFeedFilter<Note>(),
    ChangesFlowFilter<Note> {
    override fun feedKey() = channel

    override fun changesFlow() = channel.changesFlow()

    // A minichat reply — a kind-1111 comment, or a Buzz 40002 thread reply (reply-marked, non-broadcast)
    // — lives in the thread opened from its root message, not as a flat sibling in the main timeline.
    // An inline reply (kind-9/42, or a Buzz broadcast=1 reply) is a normal message and stays.
    private fun isTimelineMessage(note: Note): Boolean = !isMinichatReply(note.event) && account.isAcceptable(note)

    override fun feed(): List<Note> = sort(channel.notes.filterIntoSet { _, it -> isTimelineMessage(it) })

    override fun applyFilter(newItems: Set<Note>): Set<Note> =
        newItems
            .filter { channel.notes.containsKey(it.idHex) && isTimelineMessage(it) }
            .toSet()

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}

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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_ALL_TIMELINE_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_TIMELINE_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupJoinedChatTailFilter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupOpenChatTailFilter
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every relay-group timeline REQ carries the Buzz stream kinds unconditionally (they
 * simply match nothing on a vanilla relay); gating them created a history-cursor bug.
 */
class BuzzTimelineKindsTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val vanillaRelay = RelayUrlNormalizer.normalizeOrNull("wss://groups.example.com/")!!

    @Test
    fun theUnifiedTimelineKindSetContainsBothNip29AndBuzz() {
        assertTrue(RELAY_GROUP_ALL_TIMELINE_KINDS.containsAll(RELAY_GROUP_TIMELINE_KINDS))
        assertTrue(RELAY_GROUP_ALL_TIMELINE_KINDS.containsAll(BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS))
    }

    @Test
    fun everyTimelineReqCarriesBuzzKindsUnconditionally() {
        // The Buzz kinds are requested on EVERY relay-group timeline REQ (open tail,
        // joined fleet tail), independent of any dialect mark. Gating them created a
        // history-cursor-skip bug — this pins the unconditional contract.
        val openTail = buildRelayGroupOpenChatTailFilter(GroupId("g1", vanillaRelay), sinceEpoch = 0L)
        assertTrue(openTail.filter.kinds!!.contains(StreamMessageV2Event.KIND))

        val fleet =
            listOf(GroupId("g1", buzzRelay), GroupId("g2", vanillaRelay)).map {
                buildRelayGroupJoinedChatTailFilter(it, sinceEpoch = 0L)
            }
        fleet.forEach { assertTrue(it.filter.kinds!!.contains(StreamMessageV2Event.KIND)) }
    }
}

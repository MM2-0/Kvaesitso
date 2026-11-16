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
package com.vitorpamplona.amethyst.model.buzz

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification
import com.vitorpamplona.amethyst.commons.model.buzz.buzzChannelTypes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The channel-type map the invite projection classifies against.
 *
 * It exists because reading the type off the [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel]
 * is only correct *after* `LocalCache.consume(GroupMetadataEvent)` has copied the event into it — and
 * consume wakes the cache observers one step earlier, so the recompute that the arriving directory
 * triggers reads an empty channel, answers UNKNOWN, and never runs again. Deriving the type from the
 * metadata event that caused the emission is what makes that answer stable.
 */
class BuzzChannelTypesTest {
    private val relayKey = "b".repeat(64)

    private fun metadata(
        groupId: String,
        channelType: String?,
    ): GroupMetadataEvent {
        val tags = mutableListOf(arrayOf("d", groupId), arrayOf("name", groupId.uppercase()))
        if (channelType != null) tags.add(arrayOf("t", channelType))
        return GroupMetadataEvent(
            id = groupId.padEnd(64, '0'),
            pubKey = relayKey,
            createdAt = 1_700_000_000L,
            tags = tags.toTypedArray(),
            content = "",
            sig = "0".repeat(128),
        )
    }

    private fun noteOf(event: Event): Note = AddressableNote((event as GroupMetadataEvent).address()).apply { this.event = event }

    @Test
    fun `a stream channel is a named channel and a dm channel is a dm`() {
        val types =
            buzzChannelTypes(
                listOf(
                    noteOf(metadata("chan-eng", "stream")),
                    noteOf(metadata("chan-dm", "dm")),
                ),
            )

        assertEquals(ChannelClassification.NAMED, types["chan-eng"])
        assertEquals(ChannelClassification.DM, types["chan-dm"])
    }

    @Test
    fun `a channel with no buzz type at all is still a named channel`() {
        // A vanilla NIP-29 relay has no `channel_type`, and a group there is a group — never a DM.
        val types = buzzChannelTypes(listOf(noteOf(metadata("chan-plain", null))))

        assertEquals(ChannelClassification.NAMED, types["chan-plain"])
    }

    @Test
    fun `a note whose metadata has not arrived contributes nothing`() {
        // The placeholder case the projection has to withhold on, rather than guess NAMED and flash a
        // "somebody added you" card in front of every Buzz DM while its directory is in flight.
        val placeholder = AddressableNote(metadata("chan-unloaded", "stream").address())

        val types = buzzChannelTypes(listOf(placeholder))

        assertNull(types["chan-unloaded"])
        assertEquals(0, types.size)
    }

    @Test
    fun `the newest note for a group wins`() {
        // Two versions of the same addressable can be in flight; the last one applied is the one the
        // cache kept, and the map must not go back to an older answer.
        val types =
            buzzChannelTypes(
                listOf(
                    noteOf(metadata("chan-switch", "dm")),
                    noteOf(metadata("chan-switch", "stream")),
                ),
            )

        assertEquals(ChannelClassification.NAMED, types["chan-switch"])
    }
}

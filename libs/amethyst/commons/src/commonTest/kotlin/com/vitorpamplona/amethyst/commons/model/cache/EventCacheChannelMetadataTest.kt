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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** NIP-28: clients SHOULD ignore kind 41s from pubkeys other than the kind 40 pubkey. */
class EventCacheChannelMetadataTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val mallory = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))

    private suspend fun create() = alice.sign(ChannelCreateEvent.build("Real", null, null, null, createdAt = 1_000))

    private suspend fun update(
        signer: NostrSignerInternal,
        channel: ChannelCreateEvent,
        name: String,
        createdAt: Long,
    ) = signer.sign(ChannelMetadataEvent.build(name, null, null, null, EventHintBundle(channel, null, null), createdAt = createdAt))

    @Test
    fun updateFromAnotherAuthorIsIgnored() =
        runTest {
            val cache = EventCache()
            val channel = create()
            cache.justConsume(channel, null, true)
            cache.justConsume(update(mallory, channel, "Hijacked", 2_000), null, true)

            val state = cache.getPublicChatChannelIfExists(channel.id)
            assertEquals("Real", state?.info?.name)
            assertEquals(alice.pubKey, state?.creator?.pubkeyHex)
        }

    @Test
    fun updateFromAnotherAuthorStaysOutOfTheTimeline() =
        runTest {
            val cache = EventCache()
            val channel = create()
            val spam = update(mallory, channel, "Hijacked", 2_000)
            cache.justConsume(channel, null, true)
            cache.justConsume(spam, null, true)

            assertFalse(cache.getPublicChatChannelIfExists(channel.id)!!.notes.containsKey(spam.id))
        }

    @Test
    fun earlyUpdateFromAnotherAuthorIsDroppedWhenTheChannelArrives() =
        runTest {
            val cache = EventCache()
            val channel = create()
            val spam = update(mallory, channel, "Hijacked", 2_000)
            cache.justConsume(spam, null, true)
            cache.justConsume(channel, null, true)

            assertFalse(cache.getPublicChatChannelIfExists(channel.id)!!.notes.containsKey(spam.id))
        }

    @Test
    fun updateFromTheCreatorIsApplied() =
        runTest {
            val cache = EventCache()
            val channel = create()
            cache.justConsume(channel, null, true)
            cache.justConsume(update(alice, channel, "Renamed", 2_000), null, true)

            assertEquals("Renamed", cache.getPublicChatChannelIfExists(channel.id)?.info?.name)
        }

    @Test
    fun updatesArrivingBeforeTheChannelOnlyCountForTheCreator() =
        runTest {
            val cache = EventCache()
            val channel = create()
            cache.justConsume(update(alice, channel, "Renamed", 2_000), null, true)
            cache.justConsume(update(mallory, channel, "Hijacked", 3_000), null, true)
            cache.justConsume(channel, null, true)

            val state = cache.getPublicChatChannelIfExists(channel.id)
            assertEquals("Renamed", state?.info?.name)
            assertEquals(alice.pubKey, state?.creator?.pubkeyHex)
        }
}

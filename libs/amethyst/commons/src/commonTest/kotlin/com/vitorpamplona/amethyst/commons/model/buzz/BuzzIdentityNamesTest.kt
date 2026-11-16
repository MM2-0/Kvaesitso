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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuzzIdentityNamesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val context = UserContext { AddressableNote(it) }
    private val users = HashMap<HexKey, User>()

    @AfterTest
    fun reset() = BuzzIdentityNames.clearForTesting()

    private fun user(
        pubkey: HexKey,
        name: String,
        vararg tags: Array<String>,
    ) = User(pubkey, context).also {
        it.metadata().flow.value = UserInfo(UserMetadata().apply { this.name = name }, arrayOf(*tags).toImmutableListOfLists(), emptyList(), 1, pubkey)
        users[pubkey] = it
    }

    private fun channelWith(vararg members: HexKey) =
        RelayGroupChannel(GroupId("6a39da2f-33c0-44f6-a050-c4da0138644a", relay)).also { channel ->
            channel.updateMembers(GroupMembersEvent("00", "ff".repeat(32), 10, members.map { arrayOf("p", it) }.toTypedArray(), "", "00"))
        }

    private fun label(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        viewer: HexKey,
    ) = BuzzIdentityNames.labelFor(channel, pubkey, viewer, users = { users[it] }, hasAgentProfile = { false })?.name

    @Test
    fun anAgentSharingAMembersNameIsNamedAfterItsOwner() {
        val alice = KeyPair()
        val aliceKey = alice.pubKey.toHexKey()
        val me = "1".repeat(64)
        val human = "c".repeat(64)
        val agent = "a".repeat(64)
        user(me, "Me")
        user(aliceKey, "Alice")
        user(human, "Honey")
        user(agent, "Honey", OwnerAttestation.sign(agent, "", alice.privKey!!).toTag())

        val channel = channelWith(me, human, agent)
        // The person keeps the plain name; the agent borrows its owner's.
        assertEquals("Honey", label(channel, human, me))
        assertEquals("Alice’s Honey", label(channel, agent, me))
    }

    @Test
    fun namesOnlyGrowOnARealCollision() {
        val me = "1".repeat(64)
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        user(me, "Me")
        user(a, "Sam")
        user(b, "Sam")

        // Alone in the channel, Sam is just Sam.
        assertEquals("Sam", label(channelWith(me, a), a, me))
        // With a namesake, both get a key suffix.
        val both = channelWith(me, a, b)
        assertTrue(label(both, a, me)!!.startsWith("Sam · "))
        assertTrue(label(both, a, me) != label(both, b, me))
    }
}

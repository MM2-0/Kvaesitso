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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The display-name order: NIP-85 nickname, then the account's NIP-02 follow-list petname (the
 * user's own local name for the contact, so it beats the profile's self-chosen name), then the
 * profile's name, then the short npub.
 */
class UserAssertionsDisplayNameTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val target = "aa".repeat(32)

    private class MapCache : ICacheProvider {
        private val context = UserContext { addr -> AddressableNote(addr) }
        val users = HashMap<HexKey, User>()
        val addressables = HashMap<Address, AddressableNote>()

        override val relayHints = HintIndexer()

        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = users[pubkey]

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = addressables.getOrPut(address) { AddressableNote(address) }

        override fun getEventStream(): ICacheEventStream = error("unused")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User = users.getOrPut(pubkey) { User(pubkey, context) }

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    private fun setProfileName(
        user: User,
        name: String,
    ) {
        user.metadata().flow.value = UserInfo(UserMetadata().apply { this.name = name }, EmptyTagList, emptyList(), 1)
    }

    private fun loadFollowList(
        cache: MapCache,
        vararg tags: Array<String>,
    ) {
        val event = ContactListEvent("11".repeat(32), signer.pubKey, 10, arrayOf(*tags), "", "22".repeat(64))
        cache.getOrCreateAddressableNote(ContactListEvent.createAddress(signer.pubKey)).loadEvent(event, cache.getOrCreateUser(signer.pubKey), emptyList())
    }

    @Test
    fun followListPetnameBeatsTheProfileName() =
        runTest {
            val cache = MapCache()
            val state = UserAssertionsState(signer, cache, UserAssertionDecryptionCache(signer), EmojiPackState(signer, cache, backgroundScope))
            val user = cache.getOrCreateUser(target)
            setProfileName(user, "Profile Name")

            assertEquals("Profile Name", state.displayNameFlow(user).first())

            loadFollowList(cache, arrayOf("p", target, "", "bestie"))
            assertEquals("bestie", state.displayNameFlow(user).first())
            // The synchronous initial value reads the index the flow just built.
            assertEquals("bestie", state.cachedDisplayName(user))
        }

    @Test
    fun withoutAPetnameTheProfileNameShows() =
        runTest {
            val cache = MapCache()
            val state = UserAssertionsState(signer, cache, UserAssertionDecryptionCache(signer), EmojiPackState(signer, cache, backgroundScope))
            val user = cache.getOrCreateUser(target)
            setProfileName(user, "Profile Name")
            loadFollowList(cache, arrayOf("p", target))

            assertEquals("Profile Name", state.displayNameFlow(user).first())
            assertEquals("Profile Name", state.cachedDisplayName(user))
        }
}

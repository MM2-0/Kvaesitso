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
package com.vitorpamplona.amethyst.commons.chats.rooms.dal

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountChatPreferences
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.chats.PinnedChatroomNote
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NIP-17 gift wraps can't be fetched per counterpart, so a pinned conversation whose newest message
 * is older than the inbox's download window has no message loaded at all. It must still show up on
 * Messages, pinned on top, until a real message replaces its placeholder row.
 */
class PinnedChatroomsFeedTest {
    private val me: HexKey = "a".repeat(64)
    private val stranger: HexKey = "b".repeat(64)
    private val quiet: HexKey = "c".repeat(64)
    private val hidden: HexKey = "d".repeat(64)
    private val sig = "0".repeat(128)

    private val strangerRoom = ChatroomKey(setOf(stranger))
    private val quietRoom = ChatroomKey(setOf(quiet))
    private val hiddenRoom = ChatroomKey(setOf(hidden))

    private val chatroomList = ChatroomList(me)
    private val pinnedRooms = MutableStateFlow<Set<ChatroomKey>>(emptySet())

    // AccountSettings can't be built off-device (it reads the system locale), and the DM filters
    // only read the enabled chat feeds and the pinned rooms off it.
    private val enabledFeeds = MutableStateFlow(setOf(ChatFeedType.NIP17))

    private val settings =
        mockk<AccountSettings>().also {
            every { it.enabledChatFeeds } returns enabledFeeds
            every { it.syncedSettings.chats } returns AccountChatPreferences(pinnedRooms)
        }

    private val toggles by lazy { ChatFeedToggles(settings, chatroomList, draftRumor = { null }, findCachedNotes = { emptyList() }, reindexDraft = {}) }

    // Draft wrap -> its rumor, standing in for the drafts decryption cache.
    private val draftRumors = HashMap<DraftWrapEvent, ChatMessageEvent>()

    private val draftCache =
        mockk<DraftEventCache>().also {
            every { it.preCachedDraft(any()) } answers { draftRumors[firstArg()] }
        }

    private fun user(hex: HexKey) = mockk<User>(relaxed = true).also { every { it.pubkeyHex } returns hex }

    private val account =
        mockk<Account>().also {
            every { it.userProfile() } returns user(me)
            every { it.followingKeySet() } returns emptySet()
            every { it.chatroomList } returns chatroomList
            every { it.settings } returns settings
            every { it.chatFeedToggles } returns toggles
            every { it.draftsDecryptionCache } returns draftCache
            every { it.isAllHidden(any()) } answers { firstArg<Set<HexKey>>().all { key -> key == hidden } }
            // The real Known rule (pins included), with no Web of Trust network.
            every { it.isKnownChatroom(any(), any(), any()) } answers { callOriginal() }
            every { it.currentTrustVerdicts() } returns TrustVerdicts.inactive(me)
        }

    private fun pin(vararg rooms: ChatroomKey) {
        pinnedRooms.value = rooms.toSet()
    }

    private fun messageFrom(
        author: HexKey,
        createdAt: Long,
        to: HexKey = me,
    ): Note {
        val event = ChatMessageEvent(createdAt.toString().padStart(64, '1'), author, createdAt, arrayOf(arrayOf("p", to)), "hi", sig)
        return Note(event.id).also {
            it.loadEvent(event, user(author), emptyList())
            chatroomList.add(event, it)
        }
    }

    // An unsent draft is indexed into its room as the wrap's Note, so it can be the room's newest entry.
    private fun draftTo(
        to: HexKey,
        createdAt: Long,
    ): Note {
        val rumor = ChatMessageEvent(createdAt.toString().padStart(64, '2'), me, createdAt, arrayOf(arrayOf("p", to)), "draft", sig)
        val wrap = DraftWrapEvent(createdAt.toString().padStart(64, '3'), me, createdAt, arrayOf(arrayOf("d", "draft-$to")), "enc", sig)
        draftRumors[wrap] = rumor
        return Note(wrap.id).also {
            it.loadEvent(wrap, user(me), emptyList())
            chatroomList.add(rumor, it)
        }
    }

    @Test
    fun pinnedRoomWithoutMessagesGetsAPlaceholderRow() {
        pin(quietRoom)

        val feed = ChatroomListKnownFeedFilter(account).feed()

        assertEquals(1, feed.size)
        assertEquals(quietRoom, (feed.single() as PinnedChatroomNote).room)
    }

    @Test
    fun unpinnedRoomWithoutMessagesHasNoRow() {
        pin()
        assertTrue(ChatroomListKnownFeedFilter(account).feed().isEmpty())
    }

    @Test
    fun placeholderIsStableAcrossRebuilds() {
        pin(quietRoom)
        val filter = ChatroomListKnownFeedFilter(account)
        assertSame(filter.feed().single(), filter.feed().single())
    }

    @Test
    fun hiddenPinnedRoomGetsNoPlaceholder() {
        pin(hiddenRoom)
        assertTrue(ChatroomListKnownFeedFilter(account).feed().isEmpty())
    }

    @Test
    fun pinnedPlaceholderSortsAboveUnpinnedRooms() {
        // A stranger's room I answered, so it's Known on its own merits — but not pinned.
        val mine = messageFrom(me, 1_000, to = stranger)
        pin(quietRoom)

        val feed = ChatroomListKnownFeedFilter(account).feed()

        assertEquals(2, feed.size)
        assertTrue(feed[0] is PinnedChatroomNote)
        assertSame(mine, feed[1])
    }

    // The row draws a draft by its rumor, pin badge included, so the sort must see the same room.
    @Test
    fun pinnedRoomWhoseNewestEntryIsADraftSortsOnTop() {
        messageFrom(quiet, 1_000)
        val draft = draftTo(quiet, 2_000)
        val newer = messageFrom(me, 3_000, to = stranger)
        pin(quietRoom)

        assertEquals(listOf(draft, newer), ChatroomListKnownFeedFilter(account).feed())
    }

    // The rumor cache is an LRU: an evicted draft must not flip its row back to unpinned.
    @Test
    fun pinnedDraftRowStaysOnTopAfterItsRumorIsEvicted() {
        messageFrom(quiet, 1_000)
        val draft = draftTo(quiet, 2_000)
        val newer = messageFrom(me, 3_000, to = stranger)
        pin(quietRoom)
        draftRumors.clear()

        assertEquals(listOf(draft, newer), ChatroomListKnownFeedFilter(account).feed())
    }

    @Test
    fun arrivingMessageReplacesTheDraftRow() {
        messageFrom(quiet, 1_000)
        draftTo(quiet, 2_000)
        val filter = ChatroomListKnownFeedFilter(account)
        val old = filter.feed()

        val msg = messageFrom(quiet, 3_000)
        assertEquals(listOf(msg), filter.updateListWith(old, filter.applyFilter(setOf(msg))))
    }

    @Test
    fun arrivingMessageReplacesThePlaceholder() {
        pin(quietRoom)
        val filter = ChatroomListKnownFeedFilter(account)
        val old = filter.feed()

        val msg = messageFrom(quiet, 2_000)
        val updated = filter.updateListWith(old, filter.applyFilter(setOf(msg)))

        assertEquals(listOf(msg), updated)
    }

    @Test
    fun pinnedStrangerRoomIsKnownNotNew() {
        val msg = messageFrom(stranger, 3_000)

        pin()
        assertEquals(listOf(msg), ChatroomListNewFeedFilter(account).feed())
        assertTrue(ChatroomListKnownFeedFilter(account).feed().isEmpty())

        pin(strangerRoom)
        assertTrue(ChatroomListNewFeedFilter(account).feed().isEmpty())
        assertEquals(listOf(msg), ChatroomListKnownFeedFilter(account).feed())
    }

    // With NIP-17 off the rooms were emptied on purpose: no "No recent messages loaded" row for them.
    @Test
    fun noPlaceholdersWhileNip17IsOff() =
        runTest {
            pin(quietRoom)
            toggles.apply(setOf(ChatFeedType.NIP04))
            assertTrue("placeholder shown with NIP-17 off", ChatroomListKnownFeedFilter(account).feed().none { it is PinnedChatroomNote })
        }
}

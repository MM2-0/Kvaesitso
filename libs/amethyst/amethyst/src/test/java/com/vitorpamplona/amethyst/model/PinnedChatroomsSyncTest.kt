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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.AccountChatPreferencesInternal
import com.vitorpamplona.amethyst.commons.model.toChatroomKeys
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinned chatrooms ride inside the NIP-78 AppSpecificData settings blob (see
 * [AccountChatPreferencesInternal]) so they survive reinstalls and sync across
 * devices. These lock in the wire shape — each room is its member pubkeys
 * sorted ascending — and that the JSON round-trips back to the same
 * [ChatroomKey] set, including for blobs written before the field existed.
 */
class PinnedChatroomsSyncTest {
    private val roomA = ChatroomKey(setOf("bbb", "aaa"))
    private val roomB = ChatroomKey(setOf("ccc"))

    @Test
    fun convertsInternalListsToChatroomKeys() {
        val internal = AccountChatPreferencesInternal(pinnedRooms = listOf(listOf("aaa", "bbb"), listOf("ccc")))
        assertEquals(setOf(roomA, roomB), internal.toChatroomKeys())
    }

    @Test
    fun wireShapeIsSortedMemberLists() {
        // Mirrors AccountSyncedSettings.toInternal(): users.sorted() per room.
        val internal = AccountChatPreferencesInternal(setOf(roomA, roomB).map { it.users.sorted() })
        assertEquals(listOf(listOf("aaa", "bbb"), listOf("ccc")), internal.pinnedRooms)
    }

    @Test
    fun jsonRoundTripPreservesRooms() {
        val internal = AccountChatPreferencesInternal(setOf(roomA, roomB).map { it.users.sorted() })
        val decoded = JsonMapper.fromJson<AccountChatPreferencesInternal>(JsonMapper.toJson(internal))
        assertEquals(setOf(roomA, roomB), decoded.toChatroomKeys())
    }

    @Test
    fun blobWithoutPinnedRoomsFieldDefaultsToEmpty() {
        // Settings events published before this field existed must still parse.
        val decoded = JsonMapper.fromJson<AccountChatPreferencesInternal>("{}")
        assertEquals(emptySet<ChatroomKey>(), decoded.toChatroomKeys())
    }
}

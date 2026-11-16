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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The identity a conversation is published under.
 *
 * A launcher shortcut is not a notification — it persists, it is what the system matches a
 * notification against to grant it the Conversations treatment, and
 * [ConversationShortcuts.removeForAccount] finds an account's shortcuts by pattern-matching
 * these strings on logout. So the id has to be stable for the same chat, distinct for
 * different ones, and readably scoped to its account.
 */
class ConversationShortcutIdTest {
    private val npub = "npub1" + "q".repeat(58)
    private val otherNpub = "npub1" + "p".repeat(58)
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    /**
     * The one that would actually break. A [ChatroomKey] is a *set*, so two arrivals in the
     * same group DM can iterate its members in different orders. Joining them unsorted would
     * mint a second id for a chat that already has a shortcut — a duplicate entry in the
     * launcher, and a notification whose `setShortcutId` points at whichever copy lost.
     */
    @Test
    fun aRoomHasOneIdRegardlessOfMemberOrder() {
        val oneWay = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice, bob)), npub)
        val theOther = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(bob, alice)), npub)

        assertEquals(oneWay, theOther)
    }

    @Test
    fun theSameChatUnderTwoAccountsIsTwoConversations() {
        val mine = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice)), npub)
        val theirs = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice)), otherNpub)

        assertNotEquals(
            "one launcher entry shared by two logins would open the wrong inbox",
            mine,
            theirs,
        )
    }

    @Test
    fun differentChatsUnderOneAccountStayApart() {
        val withAlice = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice)), npub)
        val withBob = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(bob)), npub)
        val withBoth = NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice, bob)), npub)

        assertEquals(3, setOf(withAlice, withBob, withBoth).size)
    }

    /** A DM, a Marmot group and a relay group named by the same hex are three rooms, not one. */
    @Test
    fun eachKindOfRoomHasItsOwnNamespace() {
        val ids =
            setOf(
                NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice)), npub),
                NotificationRoutes.marmotShortcutId(alice, npub),
                NotificationRoutes.relayGroupShortcutId(alice, npub),
            )

        assertEquals(3, ids.size)
    }

    /**
     * [ConversationShortcuts.removeForAccount] selects on `":$npub:"`, so every id has to carry
     * the account delimited that way or a logout would leave that account's contacts in the
     * launcher.
     */
    @Test
    fun everyIdIsFindableByTheAccountScopeLogoutSweepsOn() {
        val scope = ":$npub:"

        listOf(
            NotificationRoutes.chatroomShortcutId(ChatroomKey(setOf(alice, bob)), npub),
            NotificationRoutes.marmotShortcutId(alice, npub),
            NotificationRoutes.relayGroupShortcutId("naddr1abc", npub),
        ).forEach {
            assertTrue("'$it' is not sweepable on logout", it.contains(scope))
        }
    }
}

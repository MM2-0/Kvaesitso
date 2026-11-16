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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationClickTargetTest {
    private val account = "a".repeat(64)
    private val noteId = "b".repeat(64)

    private fun spec(
        target: NotificationTarget? = null,
        account: String? = null,
    ) = NotificationSpec(title = "t", body = "b", kind = NotifKind.MENTION, target = target, account = account)

    @Test
    fun aClickCarriesTheSpecsTargetAndAccount() {
        val click = NotificationClick.of(spec(NotificationTarget.Note(noteId), account))
        assertEquals(NotificationTarget.Note(noteId), click.target)
        assertEquals(account, click.account)
        assertEquals(NotificationClick(NotificationTarget.FrontPage, account), NotificationClick.of(spec(NotificationTarget.FrontPage, account)))
    }

    @Test
    fun aClickOnANotificationThisRunDidNotPostCarriesNothing() {
        assertEquals(NotificationClick(null, null), NotificationClick.of(null))
    }

    @Test
    fun theNoteIdIsReadFromANoteTarget() {
        assertEquals(noteId, spec(NotificationTarget.Note(noteId)).deepLinkNoteId)
        assertNull(spec(NotificationTarget.FrontPage).deepLinkNoteId)
        assertNull(spec(NotificationTarget.Link("nostr:naddr1x")).deepLinkNoteId)
        assertNull(spec().deepLinkNoteId)
    }

    @Test
    fun aPostedNotificationOpensOnce() {
        val posted = PostedNotifications<String>()
        val s = spec(NotificationTarget.FrontPage, account)
        posted.put("id", s)
        assertEquals(s, posted.take("id"))
        assertNull(posted.take("id"))
        assertNull(posted.take("never-posted"))
    }

    @Test
    fun theTraysLastMessageOpensOnceAndANewerOneReplacesIt() {
        // As NucleusNotificationDispatcher keeps it: the tray's listener also fires on a plain icon click.
        val last = PostedNotifications<Unit>(capacity = 1)
        last.put(Unit, spec(account = "old"))
        last.put(Unit, spec(account = "new"))
        assertEquals("new", NotificationClick.of(last.take(Unit)).account)
        // A later click on the tray icon opens nothing.
        assertEquals(NotificationClick(null, null), NotificationClick.of(last.take(Unit)))
    }

    @Test
    fun theOldestPostedNotificationsAreForgottenFirst() {
        val posted = PostedNotifications<Int>(capacity = 2)
        posted.put(1, spec(account = "1"))
        posted.put(2, spec(account = "2"))
        posted.put(3, spec(account = "3"))
        assertNull(posted.take(1))
        assertEquals("2", posted.take(2)?.account)
        assertEquals("3", posted.take(3)?.account)
    }
}

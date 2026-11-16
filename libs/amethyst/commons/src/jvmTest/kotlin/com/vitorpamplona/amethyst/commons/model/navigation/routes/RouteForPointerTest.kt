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
package com.vitorpamplona.amethyst.commons.model.navigation.routes

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.THREAD_VIEW_KINDS
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeForPointer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A notification tap on a cold process finds nothing in LocalCache, so the only thing left
 * to route on is what the `nevent` itself states. Ordinary notes are fully described by
 * their kind and can open their thread right away; anything that needs a tag out of the
 * body must keep waiting on the redirect.
 */
class RouteForPointerTest {
    private val id = "a".repeat(64)

    @Test
    fun plainNoteKindsOpenTheirThread() {
        assertEquals(Route.Note(id), routeForPointer(TextNoteEvent.KIND, id))
        assertEquals(Route.Note(id), routeForPointer(CommentEvent.KIND, id))
        assertEquals(Route.Note(id), routeForPointer(PictureEvent.KIND, id))
    }

    @Test
    fun aPointerWithoutAKindCannotDecide() {
        assertNull(routeForPointer(null, id))
    }

    @Test
    fun kindsThatNeedTheBodyKeepWaiting() {
        // channel id lives in an `e` tag
        assertNull(routeForPointer(ChannelMessageEvent.KIND, id))
        // chatroom key lives in the `p` tags of the rumor
        assertNull(routeForPointer(ChatMessageEvent.KIND, id))
        // the wrap has to be opened before it can say anything
        assertNull(routeForPointer(GiftWrapEvent.KIND, id))
        // addressables are cited by `a` tag, not by id
        assertNull(routeForPointer(LongFormContentEvent.KIND, id))
        // a kind:0 IS the person — Route.Profile, not a note
        assertNull(routeForPointer(MetadataEvent.KIND, id))
    }

    /**
     * The invariant the shortcut rests on, checked against the thing it claims to mirror rather
     * than against a second hand-written list: for every kind it answers for, the answer must be
     * the one [routeFor] gives once the body finally arrives. Walking `THREAD_VIEW_KINDS` itself
     * means a kind added to it is covered by construction.
     *
     * This is what catches an addressable kind slipping in. `routeForInner` routes those by
     * `addressTag()`, so the shortcut's `Route.Note(id)` would point at a version id that no
     * relay will serve — a silent dead end, and exactly what the NIP-71 video pair did.
     */
    @Test
    fun everyShortcutKindAgreesWithTheRouteItsBodyWouldHaveTaken() {
        val account = mockk<Account>(relaxed = true)

        THREAD_VIEW_KINDS.forEach { kind ->
            val event: Event =
                EventFactory.create(id, "b".repeat(64), 1, kind, emptyArray(), "", "c".repeat(128))

            assertEquals(
                routeFor(event, account),
                routeForPointer(kind, id),
                "kind $kind (${event::class.simpleName}) disagrees with routeFor",
            )
            assertEquals(Route.Note(id), routeForPointer(kind, id), "kind $kind must resolve to the thread view")
        }
    }

    /**
     * The regression itself. 21/22 are regular video and take the shortcut; 34235/34236 extend
     * `AddressableVideoEvent`, are cited by `naddr`, and must wait for the body.
     */
    @Test
    fun addressableVideoKindsDoNotTakeTheShortcut() {
        assertNull(routeForPointer(AddressableNormalVideoEvent.KIND, id))
        assertNull(routeForPointer(AddressableShortVideoEvent.KIND, id))
    }
}

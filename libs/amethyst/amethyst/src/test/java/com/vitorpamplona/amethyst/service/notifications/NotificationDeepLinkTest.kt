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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.uri.chatroomRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.model.navigation.uri.isChatroomRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.isPrivateNoteRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.privateNoteRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.ui.navigation.findParameterValue
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The deep links a tapped notification hands to `MainActivity.uriToRoute`, checked at the
 * string level — these are what decide whether a tap lands on a real screen or on the
 * "looking for event" redirect.
 */
class NotificationDeepLinkTest {
    private val npub = "npub1" + "q".repeat(58)
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @Test
    fun chatroomUriCarriesBothParticipantsAndTheAccount() {
        val uri = NotificationRoutes.chatroomUri(ChatroomKey(setOf(alice)), npub)

        assertTrue(isChatroomRoute(uri))
        assertEquals(alice, uri.findQueryParameterValue("id"))
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun chatroomUriKeepsEveryMemberOfAGroupDm() {
        val uri = NotificationRoutes.chatroomUri(ChatroomKey(setOf(alice, bob)), npub)

        assertEquals(setOf(alice, bob), uri.findQueryParameterValue("id")?.split(',')?.toSet())
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    /**
     * The regression: `marmot:<hex>?account=…` is an *opaque* URI, so `java.net.URI` keeps the
     * query inside the scheme-specific part and reports no query at all. Reading the account
     * that way returned null on every Marmot notification, and the tap opened the group under
     * whichever account happened to be current instead of switching to the addressee first.
     */
    @Test
    fun opaqueMarmotUriStillYieldsItsAccount() {
        val uri = NotificationRoutes.marmotUri("d".repeat(64), npub)

        assertNull(URI(uri).findParameterValue("account"))
        assertEquals(npub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun hierarchicalNotificationUrisAreReadTheSameWay() {
        val uri = NotificationRoutes.notificationsUri(npub, "c".repeat(64))

        assertEquals(npub, uri.findQueryParameterValue("account"))
        assertEquals("c".repeat(64), uri.findQueryParameterValue("scrollTo"))
    }

    @Test
    fun aUriWithoutAQueryHasNoParameters() {
        assertNull("nevent1qqsabcdef".findQueryParameterValue("account"))
        assertNull("".findQueryParameterValue("account"))
    }

    @Test
    fun anEmptyParameterValueReadsAsAbsent() {
        assertNull("chatroom?id=&account=$npub".findQueryParameterValue("id"))
    }

    /**
     * A rumor's `nevent` names the gift wrap that delivered it, never the rumor — that is the
     * citation rule `RumorHostCitationTest` pins, and it is why a private note cannot be
     * deep-linked the ordinary way: the wrap is unfetchable from the account's read relays and
     * its note stops emitting before it is ever decrypted. So `noteUri` has to switch shapes on
     * its own; every renderer calls it and none of them knows whether its note arrived sealed.
     */
    @Test
    fun aPrivateNoteIsAddressedByItsRumorIdNotItsEnvelope() {
        val signer = NostrSignerSync(KeyPair())
        val rumor = signer.sign(TextNoteEvent.build("psst"))
        val wrap = GiftWrapEvent.create(rumor, signer.pubKey)

        val note = Note(rumor.id)
        note.event = rumor
        note.recordRumorHost(wrap)

        val uri = NotificationRoutes.noteUri(note, npub)

        assertTrue(isPrivateNoteRoute(uri))
        assertEquals(rumor.id, uri.findQueryParameterValue("id"))
        assertEquals(npub, uri.findQueryParameterValue("account"))
        assertFalse("the deep link must not name the wrap", uri.contains(wrap.id))
        assertFalse("and must not be an nevent of it", uri.startsWith("nevent1"))
    }

    @Test
    fun aPublicNoteKeepsItsNeventDeepLink() {
        val signer = NostrSignerSync(KeyPair())
        val event = signer.sign(TextNoteEvent.build("hello world"))

        val note = Note(event.id)
        note.event = event

        val uri = NotificationRoutes.noteUri(note, npub)

        assertEquals(NEvent.create(event.id, null, event.kind, null) + "?account=" + npub, uri)
    }

    /** The route a private link produces must be flagged, or its screen would REQ the rumor id. */
    @Test
    fun thePrivateRouteIsMarkedPrivate() {
        val uri = NotificationRoutes.privateNoteUri(alice, npub)

        assertEquals(Route.EventRedirect(alice, isPrivate = true), privateNoteRoute(uri))
    }

    @Test
    fun aPrivateLinkWithoutAnIdIsNotARoute() {
        assertNull(privateNoteRoute("privatenote?id=&account=$npub"))
    }

    /**
     * The destination, not just the link: a DM opens the conversation it belongs to. A kind-14
     * is a [com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable], so both ways into it agree —
     * the room link a notification now carries, and `routeFor` on the event itself, which is how
     * the wrap-shaped links still sitting in trays from an older build resolve once the envelope
     * is opened. Neither is a thread: the thread view is where a *private note* goes, which is a
     * NIP-17-wrapped kind 1 — the same envelope, but a note posted privately rather than a
     * message in a room.
     */
    @Test
    fun aDmOpensItsConversationByEitherRoute() {
        val me = NostrSignerSync(KeyPair())
        val them = NostrSignerSync(KeyPair())
        val account = mockk<Account>(relaxed = true)
        every { account.userProfile().pubkeyHex } returns me.pubKey

        val room = ChatroomKey(setOf(them.pubKey))
        val expected = Route.Room(room)

        assertEquals(expected, uriToRoute(NotificationRoutes.chatroomUri(room, npub), account))

        val dm = them.sign(ChatMessageEvent.build("hi", listOf(PTag(me.pubKey, null))))
        assertEquals(expected, routeFor(dm, account))
    }

    /**
     * A room link resolves without touching the account.
     *
     * It must not: `uriToRoute` runs against whichever account is current, *before* the
     * `?account=` switch, so registering the room here would file a DM for account B under
     * account A. And `nostr:` is exported and browsable, so a web page can post one of these —
     * which is also why the ids are checked rather than taken as given. The screen registers the
     * room itself when it opens, on the account that is current by then.
     */
    @Test
    fun aRoomLinkResolvesWithoutTouchingTheAccount() {
        val account = mockk<Account>(relaxed = true)
        val uri = NotificationRoutes.chatroomUri(ChatroomKey(setOf(alice)), npub)

        assertEquals(Route.Room(ChatroomKey(setOf(alice))), uriToRoute(uri, account))

        verify(exactly = 0) { account.chatroomList }
    }

    @Test
    fun aRoomLinkThatIsNotMadeOfPubkeysIsNotARoute() {
        assertNull(chatroomRoute("chatroom?id=not-a-pubkey&account=$npub"))
        assertNull(chatroomRoute("chatroom?id=${alice.dropLast(1)}&account=$npub"))
        assertNull(chatroomRoute("chatroom?id=${alice.uppercase()}&account=$npub"))
        // one bad member poisons the whole key — a room is the exact set or nothing
        assertNull(chatroomRoute("chatroom?id=$alice,nope&account=$npub"))
        assertNull(chatroomRoute("chatroom?id=&account=$npub"))
    }
}

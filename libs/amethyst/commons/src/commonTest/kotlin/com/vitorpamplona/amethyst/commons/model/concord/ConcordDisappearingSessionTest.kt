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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORD-08 Disappearing Messages at the commons layer: what the chat builders tag (§2), the
 * session's refusal and sweep (§3), and the timer notice (§4).
 */
class ConcordDisappearingSessionTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val day = 86_400L

    private fun entryFor(community: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            root = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            controlPk = community.controlPkHex,
            controlRoot = community.controlRoot.toHexKey(),
            relays = listOf("wss://r.example"),
            name = "Nostrichs",
        )

    /** A community whose timer is [timerSecs], folded into a session that captures what it emits. */
    private suspend fun session(
        timerSecs: Long?,
        captured: MutableList<Event> = mutableListOf(),
    ): Pair<NewConcordCommunity, ConcordCommunitySession> {
        val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
        val session = ConcordCommunitySession(entryFor(community), owner.pubKey) { _, _, rumor, _ -> captured += rumor }
        community.genesisWraps.forEach { session.ingest(it) }
        if (timerSecs != null) {
            val standing = session.state.value!!.metadata!!
            val edit = ConcordModeration.setMessageExpiration(owner, community.controlPlane, community.communityId, standing, timerSecs, session.controlEditions(), 2L, owner = community.ownerPubKey)
            session.ingest(edit)
        }
        return community to session
    }

    private fun opened(
        wrap: Event,
        plane: GroupKey,
    ): Event = ConcordStreamEnvelope.open(wrap, plane).rumor

    private fun wrapExpiration(wrap: Event): String? = wrap.tags.firstOrNull { it[0] == "expiration" }?.get(1)

    @Test
    fun theFoldedTimerIsWhatTheSessionSendsWith() =
        runTest {
            assertNull(session(null).second.messageExpirationSecs(), "no timer until staff set one")
            assertEquals(30 * day, session(30 * day).second.messageExpirationSecs())
        }

    @Test
    fun everyDurableChatRumorAndItsWrapCarryTheSameExpiration() =
        runTest {
            val (community, session) = session(day)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val timer = session.messageExpirationSecs()
            val at = 1_000_000L
            val parent = ChannelChat.message(owner.pubKey, general, plane.epoch, "parent", at - 10)
            val imeta = listOf(IMetaTagBuilder("https://blossom.example/x").build())

            val durable =
                listOf(
                    ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "hi", at, timerSecs = timer),
                    ConcordActions.buildChannelImageMessage(owner, plane.key, general, plane.epoch, "pic", imeta, at, timerSecs = timer),
                    ConcordActions.buildChannelInlineReply(owner, plane.key, general, plane.epoch, parent, "quote", at, timerSecs = timer),
                    ConcordActions.buildChannelReply(owner, plane.key, general, plane.epoch, parent, "thread", at, timerSecs = timer),
                    ConcordActions.buildChannelImageReply(owner, plane.key, general, plane.epoch, parent, "thread pic", imeta, at, timerSecs = timer),
                    ConcordActions.buildChannelReaction(owner, plane.key, general, plane.epoch, parent, "+", at, timerSecs = timer),
                )
            for (wrap in durable) {
                val rumor = opened(wrap, plane.key)
                assertEquals(at + day, ConcordDisappearing.expirationOf(rumor), "kind ${rumor.kind} signs the deadline")
                assertEquals((at + day).toString(), wrapExpiration(wrap), "kind ${rumor.kind}'s wrap repeats it")
                assertEquals("p", wrap.tags[0][0], "the random p stays first")
            }

            // Exempt: deletes, timer notices, typing — neither inside nor outside.
            val exempt =
                listOf(
                    ConcordActions.buildChannelDelete(owner, plane.key, general, plane.epoch, listOf(parent), at),
                    ConcordActions.buildChannelTimerNotice(owner, plane.key, general, plane.epoch, day, at),
                    ConcordActions.buildChannelTyping(owner, plane.key, general, plane.epoch, at),
                )
            for (wrap in exempt) {
                assertNull(ConcordDisappearing.expirationOf(opened(wrap, plane.key)))
                assertNull(wrapExpiration(wrap))
            }

            // Timer off: nothing anywhere.
            val off = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "forever", at, timerSecs = null)
            assertNull(ConcordDisappearing.expirationOf(opened(off, plane.key)))
            assertEquals(listOf("p"), off.tags.map { it[0] })
        }

    @Test
    fun anEditKeepsTheOriginalMessagesDeadlineNotNowPlusTimer() =
        runTest {
            val (community, session) = session(day)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val sentAt = 1_000_000L
            val original = ChannelChat.message(owner.pubKey, general, plane.epoch, "hi", sentAt, ConcordDisappearing.withExpiration(emptyArray(), sentAt + 7 * day))
            val editedAt = sentAt + 3 * day

            // Default: the target's own deadline, verbatim, inside and outside.
            val edit = ConcordActions.buildChannelEdit(owner, plane.key, general, plane.epoch, original, "hi!", editedAt)
            assertEquals(sentAt + 7 * day, ConcordDisappearing.expirationOf(opened(edit, plane.key)))
            assertEquals((sentAt + 7 * day).toString(), wrapExpiration(edit))

            // A message sent without a timer stays timer-free when edited, even though a timer is on now.
            val forever = ChannelChat.message(owner.pubKey, general, plane.epoch, "forever", sentAt)
            val editForever = ConcordActions.buildChannelEdit(owner, plane.key, general, plane.epoch, forever, "still forever", editedAt)
            assertNull(ConcordDisappearing.expirationOf(opened(editForever, plane.key)))
            assertNull(wrapExpiration(editForever))

            // A smuggled expiration in extraTags never overrides the original's.
            val smuggled = ConcordActions.buildChannelEdit(owner, plane.key, general, plane.epoch, forever, "x", editedAt, arrayOf(arrayOf("expiration", "5")))
            assertNull(ConcordDisappearing.expirationOf(opened(smuggled, plane.key)))
        }

    @Test
    fun anAlreadyExpiredRumorIsRefusedAndItsWrapPurged() =
        runTest {
            val captured = mutableListOf<Event>()
            val (community, session) = session(day, captured)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val longAgo = TimeUtils.now() - 2 * day
            val stale = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "stale", longAgo, timerSecs = day)

            session.ingest(stale)
            assertTrue(captured.none { it.content == "stale" }, "never stored (CORD-08 §3)")
            // The one-shot readers refuse it too (what `amy concord read` prints).
            assertTrue(ConcordActions.channelMessages(listOf(stale), plane.key, general, plane.epoch).isEmpty())
            assertNull(ConcordActions.openChannelRumor(stale, plane.key, general, plane.epoch))

            // Queued for an immediate sweep, which takes the wrap out of the buffer.
            assertTrue(session.nextExpiry.value!! <= TimeUtils.now())
            val swept = session.sweepExpired()
            assertEquals(listOf(stale.id), swept.map { it.wrapId })
            assertFalse(session.isBuffered(general, stale.id))
            assertNull(session.nextExpiry.value)
        }

    @Test
    fun theSweepDropsALiveRumorFromTheBufferWhenItExpires() =
        runTest {
            val captured = mutableListOf<Event>()
            val (community, session) = session(day, captured)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val now = TimeUtils.now()
            val live = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "for a day", now, timerSecs = day)
            val forever = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "forever", now)

            session.ingest(live)
            session.ingest(forever)
            val rumor = captured.single { it.content == "for a day" }
            assertEquals(now + day, session.nextExpiry.value)

            // Not yet due: nothing moves.
            assertTrue(session.sweepExpired(now).isEmpty())
            assertTrue(session.isBuffered(general, live.id))

            // Due: the wrap leaves the buffer (no re-projection can bring it back); the untagged one stays.
            val swept = session.sweepExpired(now + day)
            assertEquals(listOf(rumor.id), swept.map { it.rumorId })
            assertEquals(listOf(live.id), swept.map { it.wrapId })
            assertFalse(session.isBuffered(general, live.id))
            assertTrue(session.isBuffered(general, forever.id))
            assertNull(session.nextExpiry.value)
        }

    @Test
    fun theSweepPopsOnlyWhatIsDueInDeadlineOrderAndCarriesAttachmentUrls() =
        runTest {
            val (community, session) = session(day)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val now = TimeUtils.now()
            val image = listOf(ChannelChat.encryptedImageImeta("https://blossom.example/blob", "image/png", null, null, AESGCM(), null))
            val late = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "late", now, timerSecs = 3 * day)
            val soon = ConcordActions.buildChannelImageMessage(owner, plane.key, general, plane.epoch, "soon", image, now, timerSecs = day)
            val mid = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "mid", now, timerSecs = 2 * day)
            listOf(late, soon, mid).forEach { session.ingest(it) }
            assertEquals(now + day, session.nextExpiry.value, "the head of the deadline order")

            val first = session.sweepExpired(now + 2 * day)
            assertEquals(listOf(soon.id, mid.id), first.map { it.wrapId }, "due ones only, soonest first")
            // CORD-08 §3: the image's decryption key must go with the message.
            assertEquals(listOf("https://blossom.example/blob"), first.first().attachmentUrls)
            assertEquals(now + 3 * day, session.nextExpiry.value)
            assertEquals(listOf(late.id), session.sweepExpired(now + 3 * day).map { it.wrapId })
            assertNull(session.nextExpiry.value)
        }

    @Test
    fun aSweptWrapDeliveredAgainIsNotOpenedAgain() =
        runTest {
            val captured = mutableListOf<Event>()
            val (community, session) = session(day, captured)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!
            val stale = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "stale", TimeUtils.now() - 2 * day, timerSecs = day)
            session.ingest(stale)
            assertEquals(listOf(stale.id), session.sweepExpired().map { it.wrapId })

            // A relay serving it again: claimed, dropped unopened — no new deadline, no re-sweep loop.
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(stale))
            assertNull(session.nextExpiry.value)
            assertFalse(session.isBuffered(general, stale.id))
            assertTrue(captured.none { it.content == "stale" })
        }

    @Test
    fun aRefoundingCarriesTheTrackedDeadlinesIntoTheNewSession() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val registry = ConcordSessionRegistry()
            val entry = entryFor(community)
            registry.sync(listOf(entry), owner.pubKey)
            val old = registry.sessionFor(community.communityIdHex)!!
            community.genesisWraps.forEach { old.ingest(it) }
            val plane = old.currentChannelPlane(community.generalChannelIdHex)!!
            val now = TimeUtils.now()
            val live = ConcordActions.buildChannelMessage(owner, plane.key, community.generalChannelIdHex, plane.epoch, "bye", now, timerSecs = day)
            old.ingest(live)

            // The root rolls: the registry rebuilds the session, which never sees the old wrap again.
            val rolled =
                ConcordCommunityListEntry(
                    id = entry.id,
                    owner = entry.owner,
                    ownerSalt = entry.ownerSalt,
                    root = KeyPair().pubKey.toHexKey(),
                    rootEpoch = entry.rootEpoch + 1,
                    relays = entry.relays,
                    name = entry.name,
                )
            registry.sync(listOf(rolled), owner.pubKey)
            val fresh = registry.sessionFor(community.communityIdHex)!!
            assertTrue(fresh !== old)
            assertEquals(now + day, fresh.nextExpiry.value)
            assertEquals(listOf(live.id), fresh.sweepExpired(now + day).map { it.wrapId })
        }

    @Test
    fun theManagerSchedulesOnTheEarliestDeadlineAndSweepsPerCommunity() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val manager = ConcordSessionManager(MutableStateFlow(listOf(entryFor(community))), owner.pubKey, backgroundScope)
            testScheduler.runCurrent()
            community.genesisWraps.forEach { manager.ingest(it) }
            testScheduler.runCurrent()
            assertNull(manager.nextExpiry.value, "nothing expires: the sweep never wakes")

            val general = community.generalChannelIdHex
            val plane = manager.sessionFor(community.communityIdHex)!!.currentChannelPlane(general)!!
            val now = TimeUtils.now()
            val wrap = ConcordActions.buildChannelMessage(owner, plane.key, general, plane.epoch, "for a day", now, timerSecs = day)
            manager.ingest(wrap)
            testScheduler.runCurrent()
            assertEquals(now + day, manager.nextExpiry.value)

            assertTrue(manager.sweepExpired(now).isEmpty())
            val swept = manager.sweepExpired(now + day)
            assertEquals(listOf(wrap.id), swept[community.communityIdHex]!!.map { it.wrapId })
            testScheduler.runCurrent()
            assertNull(manager.nextExpiry.value)
        }

    @Test
    fun aTimerNoticeIsATypedChatRumorBelievedOnlyFromStaff() =
        runTest {
            val captured = mutableListOf<Event>()
            val (community, session) = session(null, captured)
            val general = community.generalChannelIdHex
            val plane = session.currentChannelPlane(general)!!

            session.ingest(ConcordActions.buildChannelTimerNotice(owner, plane.key, general, plane.epoch, 30 * day, TimeUtils.now()))
            val notice = assertIs<ConcordTimerNoticeEvent>(captured.single())
            assertEquals(30 * day, notice.timerSecs())

            val authority = session.state.value!!.authority
            assertTrue(ConcordDisappearing.isBelievedNotice(notice, authority), "the owner holds MANAGE_METADATA")

            val rando = NostrSignerInternal(KeyPair())
            val forged = ConcordDisappearing.timerNotice(rando.pubKey, general, plane.epoch, 0, TimeUtils.now())
            assertFalse(ConcordDisappearing.isBelievedNotice(forged, authority), "anyone can spell the tag")

            val malformed = ChannelChat.message(owner.pubKey, general, plane.epoch, "", TimeUtils.now(), arrayOf(arrayOf("timer", "soon")))
            assertFalse(ConcordDisappearing.isBelievedNotice(malformed, authority))
        }
}

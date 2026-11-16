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
package com.vitorpamplona.amethyst.commons.model.chats

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ChatFeedToggles] applies a Settings › Messages flip in one order: the gate first, then the rooms,
 * then [ChatFeedToggles.applied]. These drive [ChatFeedToggles.apply] directly; the turn-off hook runs
 * at the end of the rooms step, so it records what that step could see.
 */
class ChatFeedTogglesTest {
    private val me = "aa".repeat(32)
    private val other = "bb".repeat(32)
    private val someSig = "dd".repeat(64)

    private val settings = AccountSettings(KeyPair())
    private val rooms = ChatroomList(me)
    private val cached = mutableListOf<Note>()
    private val reindexed = mutableListOf<Note>()
    private val forgotten = mutableListOf<Set<Note>>()

    // The bounded rumor cache drafts are read from, and what decrypting each draft again would give.
    private val draftCache = mutableMapOf<DraftWrapEvent, Event>()
    private val draftPlaintext = mutableMapOf<DraftWrapEvent, Event>()

    // What the turn-off hook saw: the protocol, whether its gate was already closed, and whether
    // `applied` still listed it (it must: applied publishes last).
    private val hookSaw = mutableListOf<Triple<ChatFeedType, Boolean, Boolean>>()

    private val toggles: ChatFeedToggles

    init {
        toggles =
            ChatFeedToggles(
                settings = settings,
                chatroomList = rooms,
                draftRumor = { draftCache[it] },
                decryptDraft = { draft -> draftPlaintext[draft]?.also { draftCache[draft] = it } },
                findCachedNotes = { predicate -> cached.filter(predicate) },
                reindexDraft = { note -> reindexed += note },
                onDmProtocolOff = { type -> hookSaw += Triple(type, toggles.isEnabled(type), type in toggles.applied.value) },
                forgetGiftWraps = { notes -> forgotten += notes },
            )
    }

    private fun nip04Note(id: String): Note {
        val event = EncryptedDmEvent(id.padEnd(64, '0'), other, 100, arrayOf(arrayOf("p", me)), "x?iv=y", someSig)
        return Note(event.id).apply { this.event = event }
    }

    private fun nip17Note(id: String): Note {
        val event = ChatMessageEvent(id.padEnd(64, '0'), other, 200, arrayOf(arrayOf("p", me)), "hi", someSig)
        return Note(event.id).apply { this.event = event }
    }

    private fun ingest(note: Note) = rooms.add(note.event as ChatroomKeyable, note)

    private fun roomOf(note: Note) = rooms.rooms.get((note.event as ChatroomKeyable).chatroomKey(me))!!

    @Test
    fun turningOffClosesTheGateThenUnloadsThenPublishes() =
        runTest {
            val dm04 = nip04Note("a").also { ingest(it) }
            val dm17 = nip17Note("b").also { ingest(it) }

            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP04)

            // Gate already closed during the rooms step, applied not yet updated.
            assertEquals(listOf(Triple(ChatFeedType.NIP04, false, true)), hookSaw)
            assertEquals(setOf(dm17), roomOf(dm17).messages)
            assertFalse(toggles.isEnabled(dm04.event as ChatroomKeyable))
            assertTrue(toggles.isEnabled(dm17.event as ChatroomKeyable))
            assertFalse(ChatFeedType.NIP04 in toggles.applied.value)
        }

    @Test
    fun turningOnReindexesWhatTheCacheStillHolds() =
        runTest {
            val dm04 = nip04Note("a")
            val dm17 = nip17Note("b")
            cached += listOf(dm04, dm17)
            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP04)

            toggles.apply(ChatFeedType.ALL)

            // Messages go back in bulk, only for the protocol turned on; no drafts to route.
            assertTrue(dm04 in roomOf(dm04).messages)
            assertFalse(
                rooms.rooms
                    .get((dm17.event as ChatroomKeyable).chatroomKey(me))
                    ?.messages
                    .orEmpty()
                    .contains(dm17),
            )
            assertTrue(reindexed.isEmpty())
            assertTrue(ChatFeedType.NIP04 in toggles.applied.value)
        }

    @Test
    fun turningNip17OffForgetsTheWrapsOfWhatItUnloaded() =
        runTest {
            val dm04 = nip04Note("a").also { ingest(it) }
            val dm17 = nip17Note("b").also { ingest(it) }

            // NIP-04 has no wraps to forget; NIP-17 hands over exactly the rumors it took out of the rooms.
            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP04)
            assertTrue(forgotten.isEmpty())

            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP04 - ChatFeedType.NIP17)
            assertEquals(listOf(setOf(dm17)), forgotten)
            assertFalse(dm04 in forgotten.single())
        }

    @Test
    fun aDraftWhoseRumorWasEvictedStillLeavesWithItsProtocol() =
        runTest {
            val rumor = nip17Note("b").event as ChatMessageEvent
            val draft = DraftWrapEvent("c".padEnd(64, '0'), me, 300, arrayOf(arrayOf("d", "x")), "enc", someSig)
            val draftNote = Note(draft.id).apply { event = draft }
            rooms.add(rumor, draftNote)
            // Indexed while the rumor was cached; the cache has since let it go.
            draftPlaintext[draft] = rumor

            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP17)

            assertFalse(draftNote in rooms.rooms.get(rumor.chatroomKey(me))!!.messages)
        }

    @Test
    fun anUnchangedValueDoesNothing() =
        runTest {
            val dm04 = nip04Note("a").also { ingest(it) }
            cached += dm04

            toggles.apply(ChatFeedType.ALL)

            assertTrue(hookSaw.isEmpty())
            assertTrue(reindexed.isEmpty())
            assertEquals(setOf(dm04), roomOf(dm04).messages)
        }

    @Test
    fun nonDmTogglesNeverTouchTheRooms() =
        runTest {
            val dm04 = nip04Note("a").also { ingest(it) }

            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP28 - ChatFeedType.MARMOT)

            assertTrue(hookSaw.isEmpty())
            assertEquals(setOf(dm04), roomOf(dm04).messages)
            assertFalse(toggles.isEnabled(ChatFeedType.NIP28))
        }

    @Test
    fun admitOnlyAddsWhileTheProtocolIsOn() =
        runTest {
            val dm04 = nip04Note("a")
            val event = dm04.event as ChatroomKeyable

            assertTrue(toggles.admit(event) { ingest(dm04) })
            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP04)

            var ran = false
            assertFalse(toggles.admit(event) { ran = true })
            assertFalse(ran)
        }

    @Test
    fun startClearsAProtocolThatBeginsOff() =
        runTest {
            // Rooms are shared per pubkey; an earlier account object may have filled them.
            val dm04 = nip04Note("a").also { ingest(it) }
            settings.setChatFeedEnabled(ChatFeedType.NIP04, false)
            val fresh =
                ChatFeedToggles(
                    settings = settings,
                    chatroomList = rooms,
                    draftRumor = { null },
                    findCachedNotes = { emptyList() },
                    reindexDraft = {},
                )

            val job = fresh.start(backgroundScope)
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { while (roomOf(dm04).messages.isNotEmpty()) delay(10) }
            }
            job.cancel()

            assertTrue(roomOf(dm04).messages.isEmpty())
        }

    @Test
    fun callsGoAwayWithNip17OrTheCallsSetting() =
        runTest {
            assertTrue(toggles.isCallingActive())

            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP17)
            assertFalse(toggles.isCallingActive())

            toggles.apply(ChatFeedType.ALL)
            settings.callsEnabled.value = false
            assertFalse(toggles.isCallingActive())
        }

    // Turning NIP-17 off ends a call in progress, which signs a hangup. A remote or Amber signer that
    // refuses or fails throws: the toggles must keep applying the user's later flips, not die with it.
    @Test
    fun aFailingTurnOffHookDoesNotStopLaterFlips() =
        runTest {
            val failing =
                ChatFeedToggles(
                    settings = settings,
                    chatroomList = rooms,
                    draftRumor = { null },
                    findCachedNotes = { predicate -> cached.filter(predicate) },
                    reindexDraft = { note -> reindexed += note },
                    onDmProtocolOff = { throw IllegalStateException("signer refused the hangup") },
                )
            val scope = CoroutineScope(SupervisorJob())
            try {
                failing.start(scope)
                settings.enabledChatFeeds.value = ChatFeedType.ALL - ChatFeedType.NIP17
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) { while (ChatFeedType.NIP17 in failing.applied.value) delay(20) }
                }
                settings.enabledChatFeeds.value = ChatFeedType.ALL - ChatFeedType.NIP17 - ChatFeedType.NIP04
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) { while (ChatFeedType.NIP04 in failing.applied.value) delay(20) }
                }
                assertFalse(failing.isEnabled(ChatFeedType.NIP04))
            } finally {
                scope.cancel()
            }
        }

    // A quick off-and-on leaves `applied` where it started, but the unload reset the paging cursors.
    // Subscriptions watch `flips`, which must count both flips so their history REQs are rebuilt.
    @Test
    fun aQuickOffAndOnIsStillTwoFlips() =
        runTest {
            val before = toggles.flips.value[ChatFeedType.NIP17] ?: 0L
            toggles.apply(ChatFeedType.ALL - ChatFeedType.NIP17)
            toggles.apply(ChatFeedType.ALL)

            assertEquals(ChatFeedType.ALL, toggles.applied.value)
            assertEquals(before + 2, toggles.flips.value[ChatFeedType.NIP17])
            assertEquals(null, toggles.flips.value[ChatFeedType.NIP04], "an untouched type has no flips")
        }
}

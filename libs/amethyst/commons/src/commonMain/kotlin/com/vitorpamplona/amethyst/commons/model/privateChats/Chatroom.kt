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
package com.vitorpamplona.amethyst.commons.model.privateChats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel.Companion.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.ListChange
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NotesGatherer
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class Chatroom : NotesGatherer {
    var activeSenders: Set<User> = setOf()
    var messages: Set<Note> = setOf()
    var subject = MutableStateFlow<String?>(null)
    var subjectCreatedAt: Long? = null
    var ownerSentMessage: Boolean = false
    var newestMessage: Note? = null

    // Per-conversation NIP-04 history paging cursors, held here so reopening this room keeps its
    // progress and the cursors share the lifetime of the cached messages. The conversation history
    // loader binds its (single-active) orchestrator to this. Lazy — most rooms in the rooms list are
    // never opened for history paging, so they never allocate it.
    private val nip04HistoryHolder = lazy { RelayLoadingCursors() }
    val nip04History by nip04HistoryHolder

    /** Forgets this room's NIP-04 paging progress, without allocating cursors for a room that never paged. */
    fun resetNip04History() {
        if (nip04HistoryHolder.isInitialized()) nip04History.reset()
    }

    // Per-instance lock shared by previously @Synchronized methods.
    private val syncLock = KmpLock()

    private var changesFlow: WeakReference<MutableSharedFlow<ListChange<Note>>>? = null

    fun changesFlow(): MutableSharedFlow<ListChange<Note>> {
        val current = changesFlow?.get()
        if (current != null) return current
        val new = MutableSharedFlow<ListChange<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
        changesFlow = WeakReference(new)
        return new
    }

    override fun removeNote(note: Note) {
        removeMessageSync(note)
    }

    fun addMessageSync(msg: Note): Boolean =
        syncLock.withLock {
            if (msg !in messages) {
                messages = messages + msg
                msg.addGatherer(this)

                msg.author?.let { author ->
                    if (author !in activeSenders) {
                        activeSenders = activeSenders + author
                    }
                }

                val createdAt = msg.createdAt() ?: 0L
                if (createdAt > (newestMessage?.createdAt() ?: 0L)) {
                    newestMessage = msg
                }

                val newSubject = msg.event?.subject()

                if (newSubject != null && (msg.createdAt() ?: 0L) > (subjectCreatedAt ?: 0)) {
                    subject.tryEmit(newSubject)
                    subjectCreatedAt = msg.createdAt()
                }

                changesFlow?.get()?.tryEmit(ListChange.Addition(msg))

                return@withLock true
            }
            return@withLock false
        }

    /**
     * Adds [msgs] in one locked pass with one change event (e.g. a DM protocol turned back on and
     * re-indexed from the cache) — adding them one by one floods the 100-slot change buffer, so an open
     * conversation would miss most of them. @return the messages that were not already here.
     */
    fun addMessagesSync(msgs: Collection<Note>): Set<Note> =
        syncLock.withLock {
            val added = msgs.filterTo(HashSet()) { it !in messages }
            if (added.isEmpty()) return@withLock emptySet()

            messages = messages + added
            added.forEach { msg ->
                msg.addGatherer(this)
                msg.author?.let { if (it !in activeSenders) activeSenders = activeSenders + it }
                if ((msg.createdAt() ?: 0L) > (newestMessage?.createdAt() ?: 0L)) newestMessage = msg
                val newSubject = msg.event?.subject()
                if (newSubject != null && (msg.createdAt() ?: 0L) > (subjectCreatedAt ?: 0)) {
                    subject.tryEmit(newSubject)
                    subjectCreatedAt = msg.createdAt()
                }
            }

            changesFlow?.get()?.tryEmit(ListChange.SetAddition(added))
            added
        }

    fun removeMessageSync(msg: Note): Boolean =
        syncLock.withLock {
            if (msg in messages) {
                messages = messages - msg
                msg.removeGatherer(this)

                if (msg == newestMessage) {
                    newestMessage = messages.maxByOrNull { it.createdAt() ?: 0L }
                }

                // activeSenders is left alone on purpose: single removals are expirations, deletions and
                // hidden-user pruning, and a sender whose messages merely aged out still makes the room
                // Known. Only a whole protocol unload (removeMessagesIf) rebuilds it.

                if (msg.event?.subject() == subject.value) {
                    messages
                        .maxByOrNull {
                            val noteEvent = it.event
                            if (noteEvent?.subject() != null) {
                                noteEvent.createdAt
                            } else {
                                0
                            }
                        }?.let {
                            subject.tryEmit(it.event?.subject())
                            subjectCreatedAt = it.createdAt()
                        }
                }

                changesFlow?.get()?.tryEmit(ListChange.Deletion(msg))

                return@withLock true
            }
            return@withLock false
        }

    /**
     * Drops every message matching [predicate] in one locked pass with one change event (e.g. a whole
     * DM protocol turned off): removing them one by one copies the set per message and floods the
     * 100-slot change buffer, so an open conversation would miss most of the deletions. Unlike a
     * single removal, the derived state is rebuilt from what is left — newest message, senders and
     * subject — since a bulk removal can take every message a sender or subject came from.
     * @return the removed messages.
     */
    fun removeMessagesIf(predicate: (Note) -> Boolean): Set<Note> =
        syncLock.withLock {
            val toRemove = messages.filterTo(HashSet(), predicate)
            if (toRemove.isEmpty()) return@withLock emptySet()

            messages = messages - toRemove
            toRemove.forEach { it.removeGatherer(this) }

            newestMessage = messages.maxByOrNull { it.createdAt() ?: 0L }
            activeSenders = messages.mapNotNullTo(HashSet()) { it.author }

            if (toRemove.any { it.event?.subject() == subject.value }) {
                val newestWithSubject = messages.filter { it.event?.subject() != null }.maxByOrNull { it.createdAt() ?: 0L }
                subject.tryEmit(newestWithSubject?.event?.subject())
                subjectCreatedAt = newestWithSubject?.createdAt()
            }

            changesFlow?.get()?.tryEmit(ListChange.SetDeletion(toRemove))
            toRemove
        }

    fun senderIntersects(keySet: Set<HexKey>): Boolean = activeSenders.any { it.pubkeyHex in keySet }

    fun pruneMessagesToTheLatestOnly(): Set<Note> =
        // Same lock as addMessageSync/removeMessageSync: the snapshot, the
        // rewrite of `messages` and the change emission must not interleave
        // with a gift wrap being decrypted into this room.
        syncLock.withLock { pruneMessagesToTheLatestOnlyLocked() }

    private fun pruneMessagesToTheLatestOnlyLocked(): Set<Note> {
        val sorted = messages.sortedWith(DefaultFeedOrder)

        val toKeep =
            if ((sorted.firstOrNull()?.createdAt() ?: 0L) > DmHistoryTuning.recentBoundary()) {
                // Recent conversation, keep its newest N
                sorted.take(DmHistoryTuning.recentKeepCount).toSet()
            } else {
                // Old conversation, keep the last one.
                sorted.take(1).toSet()
            } + sorted.filter { it.flowSet?.isInUse() ?: false } + sorted.filter { it.event !is EncryptedDmEvent && it.event !is BaseDMGroupEvent }
        // Both DM protocols are pruned by the recency rule above: NIP-04 (EncryptedDmEvent) and NIP-17
        // (BaseDMGroupEvent rumors — ChatMessageEvent / file headers). Anything else that ever lands in a
        // room is kept. The caller realigns the per-relay download window for the dropped messages so
        // they can be paged again later (see LocalCache.pruneOldMessages + RelayLoadingCursors.rewindTo).

        val toRemove = messages.minus(toKeep)
        messages = toKeep

        changesFlow?.get()?.tryEmit(ListChange.SetDeletion<Note>(toRemove))

        return toRemove
    }
}

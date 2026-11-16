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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

class ThreadAssembler(
    private val cache: ICacheProvider,
) {
    private fun searchRoot(
        note: Note,
        testedNotes: MutableSet<Note> = mutableSetOf(),
    ): Note? {
        if (note.replyTo == null || note.replyTo?.isEmpty() == true) return note

        val noteEvent = note.event

        if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) return note

        // Reactions and zaps anchor their own thread: their replyTo points at the
        // post they reacted to, which lives in a different conversation. Stop here
        // instead of climbing across that bridge into the targeted post's thread.
        if (noteEvent.anchorsItsOwnThread()) return note

        testedNotes.add(note)

        val markedAsRoot =
            noteEvent
                ?.tags
                ?.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }
                ?.getOrNull(1)
        if (markedAsRoot != null) {
            // Check to see if there is an error in the tag and the root has replies
            val rootNote = cache.getNoteIfExists(markedAsRoot)
            if (rootNote?.replyTo?.isEmpty() == true) {
                return cache.checkGetOrCreateNote(markedAsRoot)
            }
        }

        val hasNoReplyTo =
            note.replyTo?.lastOrNull {
                it.replyTo?.isEmpty() == true
            }
        if (hasNoReplyTo != null) return hasNoReplyTo

        // recursive
        val roots =
            note.replyTo?.mapNotNull {
                if (it !in testedNotes) {
                    searchRoot(it, testedNotes)
                } else {
                    null
                }
            }

        if (roots != null && roots.isNotEmpty()) {
            return roots[0]
        }

        return null
    }

    @Stable
    class ThreadInfo(
        val root: Note,
        val allNotes: ImmutableSet<Note>,
    )

    fun findRoot(noteId: String): Note? {
        val note = cache.checkGetOrCreateNote(noteId) ?: return null

        return if (note.event != null) {
            val thread = OnlyLatestVersionSet()

            searchRoot(note, thread) ?: note
        } else {
            note
        }
    }

    fun findThreadFor(noteId: String): ThreadInfo? {
        val note = cache.checkGetOrCreateNote(noteId) ?: return null

        return if (note.event != null) {
            val thread = OnlyLatestVersionSet()

            if (note.event.anchorsItsOwnThread()) {
                // Reactions and zaps anchor their own conversation: the event is
                // the root and only its reply subtree is loaded — not the thread
                // of the post it targeted (that post is embedded in the event's
                // card). Replies and comments (kind 1 / 1111) keep loading the
                // full parent thread below.
                loadDown(note, thread)
            } else {
                val threadRoot = searchRoot(note, thread) ?: note

                loadUp(note, thread)

                loadDown(threadRoot, thread)
                // adds the replies of the note in case the search for Root
                // did not added them.
                note.replies.forEach { loadDown(it, thread) }
            }

            ThreadInfo(
                root = note,
                allNotes = thread.toImmutableSet(),
            )
        } else {
            ThreadInfo(
                root = note,
                allNotes = setOf(note).toImmutableSet(),
            )
        }
    }

    fun loadUp(
        note: Note,
        thread: MutableSet<Note>,
    ) {
        if (note !in thread) {
            thread.add(note)

            // A reaction/zap is a thread boundary: include the node itself, but do
            // not climb its replyTo — that edge bridges into the targeted post's
            // separate conversation (which must not be pulled into this thread).
            if (!note.event.anchorsItsOwnThread()) {
                note.replyTo?.forEach { loadUp(it, thread) }
            }
        }
    }

    fun loadDown(
        note: Note,
        thread: MutableSet<Note>,
    ) {
        if (note !in thread) {
            thread.add(note)

            note.replies.forEach { loadDown(it, thread) }
        }
    }
}

/**
 * Reactions and zaps keep the post they target in [Note.replyTo] so the card can
 * embed it, but that edge is a cross-reference, not a thread parent: it points
 * into a different conversation. For thread traversal they behave as roots — their
 * thread is the event plus its own reply subtree, never the thread of the post
 * they reacted to. Used by [ThreadAssembler] (root search, upward load) and
 * [ThreadLevelCalculator] (reply level), mirroring how reposts are treated.
 */
fun Event?.anchorsItsOwnThread(): Boolean =
    when (this) {
        is ReactionEvent, is ZapReceiptEvent, is NutzapEvent, is OnchainZapEvent, is Bolt12ZapEvent -> true
        else -> false
    }

class OnlyLatestVersionSet : MutableSet<Note> {
    val map = hashMapOf<Address, Long>()
    val set = hashSetOf<Note>()

    override fun add(element: Note): Boolean {
        val loadedCreatedAt = element.createdAt()
        val noteEvent = element.event

        return if (element is AddressableNote && loadedCreatedAt != null) {
            innerAdd(element.address, element, loadedCreatedAt)
        } else if (noteEvent is AddressableEvent && loadedCreatedAt != null) {
            innerAdd(noteEvent.address(), element, loadedCreatedAt)
        } else {
            set.add(element)
        }
    }

    private fun innerAdd(
        address: Address,
        element: Note,
        loadedCreatedAt: Long,
    ): Boolean {
        val existing = map.get(address)
        return if (existing == null) {
            map.put(address, loadedCreatedAt)
            set.add(element)
        } else {
            if (loadedCreatedAt > existing) {
                map.put(address, loadedCreatedAt)
                set.add(element)
            } else {
                false
            }
        }
    }

    override fun addAll(elements: Collection<Note>): Boolean = elements.fold(false) { changed, it -> add(it) || changed }

    override val size: Int
        get() = set.size

    override fun clear() {
        set.clear()
        map.clear()
    }

    override fun isEmpty(): Boolean = set.isEmpty()

    override fun containsAll(elements: Collection<Note>): Boolean = set.containsAll(elements)

    override fun contains(element: Note): Boolean = set.contains(element)

    override fun iterator(): MutableIterator<Note> = set.iterator()

    override fun retainAll(elements: Collection<Note>): Boolean = set.retainAll(elements)

    override fun removeAll(elements: Collection<Note>): Boolean = elements.fold(false) { changed, it -> remove(it) || changed }

    override fun remove(element: Note): Boolean {
        element.address()?.let {
            map.remove(it)
        }
        (element.event as? AddressableEvent)?.address()?.let {
            map.remove(it)
        }

        return set.remove(element)
    }
}

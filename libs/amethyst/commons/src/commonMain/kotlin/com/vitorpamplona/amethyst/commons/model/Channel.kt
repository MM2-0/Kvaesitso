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
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.WeakReference
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
abstract class Channel : NotesGatherer {
    companion object {
        val DefaultFeedOrder: Comparator<Note> =
            compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex }
    }

    val notes = LargeCache<HexKey, Note>()
    var lastNote: Note? = null

    private var relays = mapOf<NormalizedRelayUrl, Counter>()

    // Single per-instance lock that previously @Synchronized methods share.
    // Replaces JVM-only @Synchronized so this class compiles on iOS.
    private val syncLock = KmpLock()

    private var changesFlow: WeakReference<MutableSharedFlow<ListChange<Note>>>? = null

    fun changesFlow(): MutableSharedFlow<ListChange<Note>> {
        val current = changesFlow?.get()
        if (current != null) return current
        val new = MutableSharedFlow<ListChange<Note>>(0, 10, BufferOverflow.DROP_OLDEST)
        changesFlow = WeakReference(new)
        return new
    }

    open fun participatingAuthors(maxTimeLimit: Long) =
        notes.mapNotNull { key, value ->
            val createdAt = value.createdAt()
            if (createdAt != null && createdAt > maxTimeLimit) {
                value.author
            } else {
                null
            }
        }

    abstract fun toBestDisplayName(): String

    open fun relays(): Set<NormalizedRelayUrl> =
        relays.entries
            .sortedByDescending { it.value.number }
            .mapTo(LinkedHashSet(relays.size)) { it.key }

    fun updateChannelInfo() {
        flowSet?.metadata?.invalidateData()
    }

    fun addRelaySync(briefInfo: NormalizedRelayUrl) =
        syncLock.withLock {
            if (briefInfo !in relays) {
                relays = relays + Pair(briefInfo, Counter(1))
            }
        }

    fun addRelay(relay: NormalizedRelayUrl) {
        val counter = relays[relay]
        if (counter != null) {
            counter.number++
        } else {
            addRelaySync(relay)
        }
    }

    fun addNote(
        note: Note,
        relay: NormalizedRelayUrl? = null,
    ) {
        if (!notes.containsKey(note.idHex)) {
            notes.put(note.idHex, note)
            note.addGatherer(this)

            if ((note.createdAt() ?: 0L) > (lastNote?.createdAt() ?: 0L)) {
                lastNote = note
            }

            if (relay != null) {
                addRelay(relay)
            }

            changesFlow?.get()?.tryEmit(ListChange.Addition(note))

            flowSet?.notes?.invalidateData()
        }
    }

    /**
     * Refresh derived state after an already-[addNote]'d [note] finishes loading its event.
     *
     * Most channels attach a note whose event is already set, so [addNote] can pick [lastNote] and
     * order the feed right away. Concord is the exception: it attaches a message row to its channel
     * *before* the rumor is consumed (so the row already carries its gatherer when it flows through
     * the Messages filter), which means [addNote] ran while `createdAt()` was still null and could
     * neither set [lastNote] nor sort correctly. Once the event lands, call this so [lastNote] and
     * any notes-flow observers (row previews, unread counts, ordering) reflect the real message.
     */
    fun refreshAfterEventLoad(note: Note) {
        if (!notes.containsKey(note.idHex)) return
        if ((note.createdAt() ?: 0L) > (lastNote?.createdAt() ?: 0L)) {
            lastNote = note
        }
        flowSet?.notes?.invalidateData()
    }

    override fun removeNote(note: Note) {
        if (notes.containsKey(note.idHex)) {
            notes.remove(note.idHex)
            note.removeGatherer(this)

            if (note == lastNote) {
                lastNote = notes.values().sortedWith(DefaultFeedOrder).firstOrNull()
            }

            changesFlow?.get()?.tryEmit(ListChange.Deletion(note))

            flowSet?.notes?.invalidateData()
        }
    }

    fun pruneOldMessages(): Set<Note> {
        val important =
            notes
                .values()
                .sortedWith(DefaultFeedOrder)
                .take(500)
                .toSet()

        val toBeRemoved = notes.filter { key, it -> it !in important }

        toBeRemoved.forEach { notes.remove(it.idHex) }

        changesFlow?.get()?.tryEmit(ListChange.SetDeletion(toBeRemoved.toSet()))

        flowSet?.notes?.invalidateData()

        return toBeRemoved.toSet()
    }

    fun pruneHiddenMessages(account: IAccount): Set<Note> {
        val hidden =
            notes
                .filter { key, it ->
                    it.author?.let { author -> account.isHidden(author) } == true
                }.toSet()

        hidden.forEach { notes.remove(it.idHex) }

        changesFlow?.get()?.tryEmit(ListChange.SetDeletion(hidden))

        flowSet?.notes?.invalidateData()

        return hidden.toSet()
    }

    var flowSet: ChannelFlowSet? = null

    fun createOrDestroyFlowSync(create: Boolean) =
        syncLock.withLock {
            if (create) {
                if (flowSet == null) {
                    flowSet = ChannelFlowSet(this)
                }
            } else {
                if (flowSet != null && flowSet?.isInUse() == false) {
                    flowSet = null
                }
            }
        }

    fun flow(): ChannelFlowSet {
        if (flowSet == null) {
            createOrDestroyFlowSync(true)
        }
        return flowSet!!
    }

    fun clearFlow() {
        if (flowSet != null && flowSet?.isInUse() == false) {
            createOrDestroyFlowSync(false)
        }
    }
}

data class Counter(
    var number: Int = 0,
)

@Stable
class ChannelFlowSet(
    u: Channel,
) {
    // Observers line up here.
    val metadata = ChannelFlow(u)
    val notes = ChannelFlow(u)

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            notes.hasObservers()
}

class ChannelFlow(
    val channel: Channel,
) {
    val stateFlow = MutableStateFlow(ChannelState(channel))

    fun invalidateData() {
        stateFlow.tryEmit(ChannelState(channel))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

class ChannelState(
    val channel: Channel,
)

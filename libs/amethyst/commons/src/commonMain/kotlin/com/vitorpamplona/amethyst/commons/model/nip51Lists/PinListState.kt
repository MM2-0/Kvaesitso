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
package com.vitorpamplona.amethyst.commons.model.nip51Lists

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@Stable
class PinListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val pinList = cache.getOrCreateAddressableNote(PinListEvent.createPinAddress(signer.pubKey))

    fun getPinListFlow(): StateFlow<NoteState> = pinList.flow().metadata.stateFlow

    fun getPinList(): PinListEvent? = pinList.event as? PinListEvent

    fun pinnedEvents(note: Note): List<EventBookmark> {
        val noteEvent = note.event as? PinListEvent
        return noteEvent?.pinnedEvents() ?: emptyList()
    }

    @OptIn(FlowPreview::class)
    val pinnedNotes: StateFlow<List<EventBookmark>> =
        getPinListFlow()
            .map { noteState ->
                pinnedEvents(noteState.note)
            }.onStart {
                emit(pinnedEvents(pinList))
            }.debounce(100)
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    val pinnedEventIdSet: StateFlow<Set<String>> =
        pinnedNotes
            .map { pins ->
                pins.map { it.eventId }.toSet()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(FlowPreview::class)
    val pinnedNotesList: StateFlow<List<Note>> =
        pinnedNotes
            .map { pins ->
                pins.mapNotNull { cache.checkGetOrCreateNote(it.eventId) }.reversed()
            }.onStart {
                emit(
                    pinnedNotes.value.mapNotNull { cache.checkGetOrCreateNote(it.eventId) }.reversed(),
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    fun isPinned(note: Note): Boolean = pinnedEventIdSet.value.contains(note.idHex)

    suspend fun addPin(note: Note): PinListEvent {
        val currentList = getPinList()
        val pin = EventBookmark(note.idHex, note.relayHintUrl())

        return if (currentList == null) {
            PinListEvent.create(
                pin = pin,
                signer = signer,
            )
        } else {
            PinListEvent.add(
                earlierVersion = currentList,
                pin = pin,
                signer = signer,
            )
        }
    }

    suspend fun removePin(note: Note): PinListEvent? {
        val currentList = getPinList() ?: return null
        val pin = EventBookmark(note.idHex, note.relayHintUrl())

        return PinListEvent.remove(
            earlierVersion = currentList,
            pin = pin,
            signer = signer,
        )
    }

    suspend fun removeDeletedPins(deletedNotes: Set<Note>): PinListEvent? {
        val currentList = getPinList() ?: return null
        if (deletedNotes.isEmpty()) return null

        val deletedIds = deletedNotes.mapTo(HashSet()) { it.idHex }
        val newTags =
            currentList.tags
                .filter { tag ->
                    val bookmark = EventBookmark.parse(tag)
                    bookmark == null || bookmark.eventId !in deletedIds
                }.toTypedArray()

        if (newTags.size == currentList.tags.size) return null

        return PinListEvent.resign(tags = newTags, signer = signer)
    }
}

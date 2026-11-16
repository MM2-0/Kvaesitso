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
package com.vitorpamplona.amethyst.commons.model.emphChat

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

interface EphemeralChatRepository {
    fun ephemeralChatList(): EphemeralChatListEvent?

    fun updateEphemeralChatListTo(newEphemeralChatList: EphemeralChatListEvent?)
}

class EphemeralChatListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: EphemeralChatListDecryptionCache,
    val scope: CoroutineScope,
    val settings: EphemeralChatRepository,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val ephemeralChatListNote = cache.getOrCreateAddressableNote(getEphemeralChatListAddress())

    fun getEphemeralChatListAddress() = EphemeralChatListEvent.createAddress(signer.pubKey)

    fun getEphemeralChatListFlow(): StateFlow<NoteState> = ephemeralChatListNote.flow().metadata.stateFlow

    fun getEphemeralChatList(): EphemeralChatListEvent? = ephemeralChatListNote.event as? EphemeralChatListEvent

    suspend fun ephemeralChatListWithBackup(note: Note): Set<RoomId> {
        val event = note.event as? EphemeralChatListEvent ?: settings.ephemeralChatList()
        return event?.let { decryptionCache.roomSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveEphemeralChatList: StateFlow<Set<RoomId>> =
        getEphemeralChatListFlow()
            .transformLatest { noteState ->
                emit(ephemeralChatListWithBackup(noteState.note))
            }.onStart {
                emit(ephemeralChatListWithBackup(ephemeralChatListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun follow(channel: EphemeralChatChannel): EphemeralChatListEvent {
        val ephemeralChatList = getEphemeralChatList()

        return if (ephemeralChatList == null) {
            EphemeralChatListEvent.create(
                room = channel.roomId,
                isPrivate = true,
                signer = signer,
            )
        } else {
            EphemeralChatListEvent.add(
                earlierVersion = ephemeralChatList,
                room = channel.roomId,
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun unfollow(channel: EphemeralChatChannel): EphemeralChatListEvent? {
        val ephemeralChatList = getEphemeralChatList()
        return if (ephemeralChatList != null) {
            EphemeralChatListEvent.remove(
                earlierVersion = ephemeralChatList,
                room = channel.roomId,
                signer = signer,
            )
        } else {
            null
        }
    }

    init {
        settings.ephemeralChatList()?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved ephemeral chat list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "EphemeralChatList Collector Start")
            getEphemeralChatListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "EphemeralChatList List for ${signer.pubKey}" }
                (noteState.note.event as? EphemeralChatListEvent)?.let {
                    settings.updateEphemeralChatListTo(it)
                }
            }
        }
    }
}

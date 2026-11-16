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
package com.vitorpamplona.amethyst.commons.model.nip28PublicChats

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.tags.ChannelTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

interface PublicChatListRepository {
    fun publicChatList(): PublicChatListEvent?

    fun updatePublicChatListTo(newPublicChatList: PublicChatListEvent?)
}

class PublicChatListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: PublicChatListDecryptionCache,
    val scope: CoroutineScope,
    val settings: PublicChatListRepository,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val publicChatListNote = cache.getOrCreateAddressableNote(getPublicChatListAddress())

    fun getPublicChatListAddress() = PublicChatListEvent.createAddress(signer.pubKey)

    fun getPublicChatListFlow(): StateFlow<NoteState> = publicChatListNote.flow().metadata.stateFlow

    fun getPublicChatList(): PublicChatListEvent? = publicChatListNote.event as? PublicChatListEvent

    suspend fun publicChatListWithBackup(note: Note): Set<ChannelTag> {
        val event = note.event as? PublicChatListEvent ?: settings.publicChatList()
        return event?.let { decryptionCache.channelSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<ChannelTag>> =
        getPublicChatListFlow()
            .transformLatest { noteState ->
                emit(publicChatListWithBackup(noteState.note))
            }.onStart {
                emit(publicChatListWithBackup(publicChatListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<HexKey>> =
        flow
            .map {
                it.mapTo(mutableSetOf()) { it.eventId }
            }.onStart {
                emit(flow.value.mapTo(mutableSetOf()) { it.eventId })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val flowSetNote =
        flowSet
            .map {
                it.mapNotNull {
                    cache.checkGetOrCreateNote(it)
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun follow(channel: PublicChatChannel): PublicChatListEvent {
        val publicChatList = getPublicChatList()

        return if (publicChatList == null) {
            PublicChatListEvent.create(ChannelTag(channel.idHex, channel.relayHintUrl()), true, signer)
        } else {
            PublicChatListEvent.add(publicChatList, ChannelTag(channel.idHex, channel.relayHintUrl()), true, signer)
        }
    }

    suspend fun follow(channels: List<PublicChatChannel>): PublicChatListEvent {
        val publicChatList = getPublicChatList()

        val channelTags = channels.map { ChannelTag(it.idHex, it.relayHintUrl()) }
        return if (publicChatList == null) {
            PublicChatListEvent.create(channelTags, true, signer)
        } else {
            PublicChatListEvent.add(publicChatList, channelTags, true, signer)
        }
    }

    suspend fun unfollow(channel: PublicChatChannel): PublicChatListEvent? {
        val publicChatList = getPublicChatList()

        return if (publicChatList != null) {
            PublicChatListEvent.remove(publicChatList, ChannelTag(channel.idHex, channel.relayHintUrl()), signer)
        } else {
            null
        }
    }

    init {
        settings.publicChatList()?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved channel list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Channel List Collector Start")
            getPublicChatListFlow().collect {
                Log.d("AccountRegisterObservers") { "Channel List for ${signer.pubKey}" }
                (it.note.event as? PublicChatListEvent)?.let {
                    settings.updatePublicChatListTo(it)
                }
            }
        }
    }
}

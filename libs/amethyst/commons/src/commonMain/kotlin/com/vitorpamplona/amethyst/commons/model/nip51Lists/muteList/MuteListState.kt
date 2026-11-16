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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MuteListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: MuteListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val muteListNote = cache.getOrCreateAddressableNote(getMuteListAddress())

    fun getMuteListAddress() = MuteListEvent.createAddress(signer.pubKey)

    fun getMuteListFlow(): StateFlow<NoteState> = muteListNote.flow().metadata.stateFlow

    fun getMuteList(): MuteListEvent? = muteListNote.event as? MuteListEvent

    suspend fun muteListWithBackup(note: Note): List<MuteTag> {
        val event = note.event as? MuteListEvent ?: settings.backupMuteList
        return event?.let { decryptionCache.mutedUsersAndWords(it) } ?: emptyList()
    }

    val flow =
        getMuteListFlow()
            .map { muteListWithBackup(it.note) }
            .onStart { emit(muteListWithBackup(muteListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun hideUser(pubkeyHex: String): MuteListEvent {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.add(
                earlierVersion = muteList,
                mute = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
            )
        } else {
            MuteListEvent.create(
                mute = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun showUser(pubkeyHex: String): MuteListEvent? {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.remove(
                earlierVersion = muteList,
                mute = UserTag(pubkeyHex),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun hideWord(word: String): MuteListEvent {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.add(
                earlierVersion = muteList,
                mute = WordTag(word),
                isPrivate = true,
                signer = signer,
            )
        } else {
            MuteListEvent.create(
                mute = WordTag(word),
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun showWord(word: String): MuteListEvent? {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.remove(
                earlierVersion = muteList,
                mute = WordTag(word),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun hideHashtag(hashtag: String): MuteListEvent {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.add(
                earlierVersion = muteList,
                mute = HashtagTag(hashtag),
                isPrivate = true,
                signer = signer,
            )
        } else {
            MuteListEvent.create(
                mute = HashtagTag(hashtag),
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun showHashtag(hashtag: String): MuteListEvent? {
        val muteList = getMuteList()

        return if (muteList != null) {
            MuteListEvent.remove(
                earlierVersion = muteList,
                mute = HashtagTag(hashtag),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun hideThread(rootHex: HexKey): MuteListEvent {
        val muteList = getMuteList()
        return if (muteList != null) {
            MuteListEvent.add(
                earlierVersion = muteList,
                mute = EventTag(rootHex),
                isPrivate = true,
                signer = signer,
            )
        } else {
            MuteListEvent.create(
                mute = EventTag(rootHex),
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun showThread(rootHex: HexKey): MuteListEvent? {
        val muteList = getMuteList()
        return if (muteList != null) {
            MuteListEvent.remove(
                earlierVersion = muteList,
                mute = EventTag(rootHex),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun showUsers(pubkeys: List<String>): MuteListEvent? {
        if (pubkeys.isEmpty()) return null
        val muteList = getMuteList() ?: return null
        return MuteListEvent.removeAll(
            earlierVersion = muteList,
            mutes = pubkeys.map { UserTag(it) },
            signer = signer,
        )
    }

    suspend fun showWords(words: List<String>): MuteListEvent? {
        if (words.isEmpty()) return null
        val muteList = getMuteList() ?: return null
        return MuteListEvent.removeAll(
            earlierVersion = muteList,
            mutes = words.map { WordTag(it) },
            signer = signer,
        )
    }

    init {
        settings.backupMuteList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved mute list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Mute List Collector Start")
            getMuteListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Mute List for ${signer.pubKey}" }
                (it.note.event as? MuteListEvent)?.let {
                    settings.updateMuteList(it)
                }
            }
        }
    }
}

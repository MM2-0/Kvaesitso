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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.interestLists

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestLists.InterestListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
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

class InterestListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: InterestListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val hashtagListNote = cache.getOrCreateAddressableNote(getInterestListAddress())

    fun getInterestListAddress() = InterestListEvent.createAddress(signer.pubKey)

    fun getInterestListFlow(): StateFlow<NoteState> = hashtagListNote.flow().metadata.stateFlow

    fun getInterestList(): InterestListEvent? = hashtagListNote.event as? InterestListEvent

    suspend fun hashtagListWithBackup(note: Note): Set<String> {
        val event = note.event as? InterestListEvent ?: settings.backupInterestList
        return event?.let { decryptionCache.hashtags(it).mapTo(mutableSetOf()) { it.lowercase() } } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<String>> =
        getInterestListFlow()
            .transformLatest { noteState ->
                emit(hashtagListWithBackup(noteState.note))
            }.onStart {
                emit(hashtagListWithBackup(hashtagListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun follow(hashtags: List<String>): InterestListEvent {
        val hashtagList = getInterestList()

        return if (hashtagList == null) {
            InterestListEvent.create(hashtags, true, signer)
        } else {
            InterestListEvent.add(hashtagList, hashtags, true, signer)
        }
    }

    suspend fun follow(hashtag: String): InterestListEvent {
        val hashtagList = getInterestList()

        return if (hashtagList == null) {
            InterestListEvent.create(hashtag.lowercase(), true, signer)
        } else {
            InterestListEvent.add(hashtagList, hashtag.lowercase(), true, signer)
        }
    }

    suspend fun unfollow(hashtag: String): InterestListEvent? {
        val hashtagList = getInterestList()

        return if (hashtagList != null) {
            InterestListEvent.remove(hashtagList, hashtag, signer)
        } else {
            null
        }
    }

    init {
        settings.backupInterestList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved Hashtag list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Hashtag List Collector Start")
            getInterestListFlow().collect {
                Log.d("AccountRegisterObservers") { "Hashtag List for ${signer.pubKey}" }
                (it.note.event as? InterestListEvent)?.let {
                    settings.updateInterestListTo(it)
                }
            }
        }
    }
}

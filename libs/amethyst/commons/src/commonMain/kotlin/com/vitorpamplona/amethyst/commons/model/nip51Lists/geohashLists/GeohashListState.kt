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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists.GeohashListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
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

class GeohashListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: GeohashListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val geohashListNote = cache.getOrCreateAddressableNote(getGeohashListAddress())

    fun getGeohashListAddress() = GeohashListEvent.createAddress(signer.pubKey)

    fun getGeohashListFlow(): StateFlow<NoteState> = geohashListNote.flow().metadata.stateFlow

    fun getGeohashList(): GeohashListEvent? = geohashListNote.event as? GeohashListEvent

    suspend fun geohashListWithBackup(note: Note): Set<String> {
        val event = note.event as? GeohashListEvent ?: settings.backupGeohashList
        return event?.let { decryptionCache.geohashes(it).mapTo(mutableSetOf()) { it.lowercase() } } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<String>> =
        getGeohashListFlow()
            .transformLatest { noteState ->
                emit(geohashListWithBackup(noteState.note))
            }.onStart {
                emit(geohashListWithBackup(geohashListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun follow(geohashes: List<String>): GeohashListEvent {
        val geohashList = getGeohashList()

        return if (geohashList == null) {
            GeohashListEvent.create(geohashes, true, signer)
        } else {
            GeohashListEvent.add(geohashList, geohashes, true, signer)
        }
    }

    suspend fun follow(geohash: String): GeohashListEvent {
        val geohashList = getGeohashList()

        return if (geohashList == null) {
            GeohashListEvent.create(geohash, true, signer)
        } else {
            GeohashListEvent.add(geohashList, geohash, true, signer)
        }
    }

    suspend fun unfollow(geohash: String): GeohashListEvent? {
        val geohashList = getGeohashList()

        return if (geohashList != null) {
            GeohashListEvent.remove(geohashList, geohash, signer)
        } else {
            null
        }
    }

    init {
        settings.backupGeohashList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved Geohash list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Geohash List Collector Start")
            getGeohashListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "Geohash List for ${signer.pubKey}" }
                (noteState.note.event as? GeohashListEvent)?.let {
                    settings.updateGeohashListTo(it)
                }
            }
        }
    }
}

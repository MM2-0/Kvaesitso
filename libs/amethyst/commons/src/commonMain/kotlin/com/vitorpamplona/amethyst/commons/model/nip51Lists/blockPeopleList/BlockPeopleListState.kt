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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.blockPeopleList

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.FollowSetDecryptionCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class BlockPeopleListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FollowSetDecryptionCache,
    val scope: CoroutineScope,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val blockListNote = cache.getOrCreateAddressableNote(getBlockListAddress())

    fun getBlockListAddress() = FollowSetEvent.createBlockAddress(signer.pubKey)

    fun getBlockListFlow(): StateFlow<NoteState> = blockListNote.flow().metadata.stateFlow

    fun getBlockList(): FollowSetEvent? = blockListNote.event as? FollowSetEvent

    suspend fun blockListWithBackup(note: Note): List<MuteTag> {
        val event = note.event as? FollowSetEvent
        return event?.let { decryptionCache.usersAndWords(it) } ?: emptyList()
    }

    val flow =
        getBlockListFlow()
            .map { blockListWithBackup(it.note) }
            .onStart { emit(blockListWithBackup(blockListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun hideUser(pubkeyHex: String): FollowSetEvent {
        val blockList = getBlockList()

        return if (blockList != null) {
            FollowSetEvent.add(
                earlierVersion = blockList,
                person = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
            )
        } else {
            FollowSetEvent.create(
                title = FollowSetEvent.BLOCK_LIST_D_TAG,
                person = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
                dTag = FollowSetEvent.BLOCK_LIST_D_TAG,
            )
        }
    }

    suspend fun showUser(pubkeyHex: String): FollowSetEvent? {
        val blockList = getBlockList()

        return if (blockList != null) {
            FollowSetEvent.remove(
                earlierVersion = blockList,
                person = UserTag(pubkeyHex),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun showWord(word: String): FollowSetEvent? {
        val blockList = getBlockList()

        return if (blockList != null) {
            FollowSetEvent.remove(
                earlierVersion = blockList,
                person = WordTag(word),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun showUsers(pubkeys: List<String>): FollowSetEvent? {
        if (pubkeys.isEmpty()) return null
        val blockList = getBlockList() ?: return null
        return FollowSetEvent.removeAll(
            earlierVersion = blockList,
            persons = pubkeys.map { UserTag(it) },
            signer = signer,
        )
    }

    suspend fun showWords(words: List<String>): FollowSetEvent? {
        if (words.isEmpty()) return null
        val blockList = getBlockList() ?: return null
        return FollowSetEvent.removeAll(
            earlierVersion = blockList,
            persons = words.map { WordTag(it) },
            signer = signer,
        )
    }
}

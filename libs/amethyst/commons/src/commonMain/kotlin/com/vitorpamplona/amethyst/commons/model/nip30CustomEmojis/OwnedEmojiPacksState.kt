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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

class OwnedEmojiPacksState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingOwnedEmojiPackNotes() = cache.addressables.filter(EmojiPackEvent.KIND, user.pubkeyHex)

    val ownedEmojiPackVersions = MutableStateFlow(0)

    val ownedEmojiPackNotes =
        ownedEmojiPackVersions
            .map { existingOwnedEmojiPackNotes() }
            .onStart { emit(existingOwnedEmojiPackNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val ownedEmojiPackEventIds =
        ownedEmojiPackNotes
            .map { it.eventIdSet() }
            .onStart { emit(ownedEmojiPackNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestEmojiPacks: StateFlow<List<EmojiPackEvent>> =
        ownedEmojiPackNotes
            .transformLatest { emitAll(it.updateFlow<EmojiPackEvent>()) }
            .onStart { emit(ownedEmojiPackNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun EmojiPackEvent.toOwnedEmojiPack(): OwnedEmojiPack =
        OwnedEmojiPack(
            identifier = dTag(),
            title = titleOrName() ?: dTag(),
            description = description(),
            image = image(),
            publicEmojis = publicEmojis(),
            privateEmojis = privateEmojis(signer) ?: emptyList(),
        )

    suspend fun List<EmojiPackEvent>.toOwnedEmojiPackFeed() = map { it.toOwnedEmojiPack() }.sortedBy { it.title }

    val listFeedFlow =
        latestEmojiPacks
            .map { it.toOwnedEmojiPackFeed() }
            .onStart { emit(latestEmojiPacks.value.toOwnedEmojiPackFeed()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<OwnedEmojiPack>.getPack(packDTag: String) =
        this.firstOrNull {
            it.identifier == packDTag
        }

    fun getPack(dTag: String) = listFeedFlow.value.getPack(dTag)

    fun getOwnedEmojiPackNote(dTag: String): AddressableNote? = existingOwnedEmojiPackNotes().find { it.dTag() == dTag }

    fun getOwnedEmojiPackEvent(dTag: String): EmojiPackEvent? = getOwnedEmojiPackNote(dTag)?.event as? EmojiPackEvent

    fun getOwnedEmojiPackFlow(dTag: String) =
        listFeedFlow
            .map { it.getPack(dTag) }
            .onStart { emit(listFeedFlow.value.getPack(dTag)) }
            .flowOn(Dispatchers.IO)

    fun DeletionRequestEvent.hasAnyDeletedOwnedEmojiPacks() = deleteAddressesWithKind(EmojiPackEvent.KIND) || deletesAnyEventIn(ownedEmojiPackEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is EmojiPackEvent || (event is DeletionRequestEvent && event.hasAnyDeletedOwnedEmojiPacks())
            } else {
                false
            }
        }

    fun newNotes(newNotes: Set<Note>) {
        if (hasItemInNoteList(newNotes)) {
            forceRefresh()
        }
    }

    fun deletedNotes(deletedNotes: Set<Note>) {
        if (hasItemInNoteList(deletedNotes)) {
            forceRefresh()
        }
    }

    fun forceRefresh() {
        ownedEmojiPackVersions.update { it + 1 }
    }

    suspend fun createPack(
        title: String,
        description: String? = null,
        image: String? = null,
        account: Account,
    ) {
        account.sendMyPublicAndPrivateOutbox(EmojiPackActions.create(signer, title, description, image))
    }

    suspend fun updateMetadata(
        dTag: String,
        newTitle: String,
        newDescription: String?,
        newImage: String?,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return
        account.sendMyPublicAndPrivateOutbox(EmojiPackActions.updateMetadata(signer, packEvent, newTitle, newDescription, newImage))
    }

    /** Adds one emoji, replacing the one with the same shortcode in that bucket if there is one. */
    suspend fun addEmoji(
        dTag: String,
        emojiTag: EmojiUrlTag,
        isPrivate: Boolean,
        account: Account,
    ) {
        require(EmojiUrlTag.isValidShortcode(emojiTag.code)) { "Invalid emoji shortcode: ${emojiTag.code}" }
        addEmojis(dTag, listOf(emojiTag), isPrivate, EmojiPackActions.OnConflict.REPLACE, account)
    }

    /** Adds many emojis in one new version of the pack; null when the pack is not in the cache. */
    suspend fun addEmojis(
        dTag: String,
        emojis: List<EmojiUrlTag>,
        isPrivate: Boolean,
        onConflict: EmojiPackActions.OnConflict,
        account: Account,
    ): EmojiPackActions.AddResult? {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return null
        val result = EmojiPackActions.addEmojis(signer, packEvent, emojis, isPrivate, onConflict)
        result.event?.let { account.sendMyPublicAndPrivateOutbox(it) }
        return result
    }

    suspend fun removeEmoji(
        dTag: String,
        shortcode: String,
        isPrivate: Boolean,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return
        EmojiPackActions.removeEmojis(signer, packEvent, listOf(shortcode), isPrivate).event?.let {
            account.sendMyPublicAndPrivateOutbox(it)
        }
    }

    suspend fun deletePack(
        dTag: String,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return
        val deletionEventTemplate = DeletionRequestEvent.build(listOf(packEvent))
        val deletionEvent = signer.sign(deletionEventTemplate)
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }
}

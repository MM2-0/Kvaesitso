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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class EmojiPackState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
    reactionChoices: StateFlow<List<String>> = MutableStateFlow(emptyList()),
) {
    class EmojiMedia(
        val code: String,
        val link: String,
    )

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val emojiPackListNote = cache.getOrCreateAddressableNote(getEmojiListAddress())

    fun getEmojiListAddress() = EmojiListEvent.createAddress(signer.pubKey)

    fun getEmojiList(): EmojiListEvent? = emojiPackListNote.event as? EmojiListEvent

    fun getEmojiListFlow(): StateFlow<NoteState> = emojiPackListNote.flow().metadata.stateFlow

    /**
     * The addresses (`kind:pubkey:d`) of the packs in the emoji list, or empty while the list is not
     * on this device. One shared set, so a screen of pack cards answers "is this one added?" with
     * a lookup instead of a subscription and a tag scan per card.
     */
    val selectedPackIds: StateFlow<Set<String>> =
        getEmojiListFlow()
            .map { (it.note.event as? EmojiListEvent)?.emojiPackIds()?.toSet() ?: emptySet() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, getEmojiList()?.emojiPackIds()?.toSet() ?: emptySet())

    fun convertEmojiSelectionPack(selection: EmojiListEvent?): List<StateFlow<NoteState>>? =
        selection?.emojiPacks()?.map {
            cache
                .getOrCreateAddressableNote(it)
                .flow()
                .metadata.stateFlow
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<List<StateFlow<NoteState>>?> =
        getEmojiListFlow()
            .transformLatest {
                emit(convertEmojiSelectionPack(it.note.event as? EmojiListEvent))
            }.onStart {
                emit(convertEmojiSelectionPack(getEmojiList()))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    fun convertEmojiPack(pack: EmojiPackEvent): List<EmojiMedia> = pack.publicEmojis().toEmojiMedia()

    // Decrypts private (NIP-51) emojis when this signer authored the pack so they
    // surface alongside public ones in the `:` autocomplete. Foreign packs return
    // public-only because privateTags refuses to decrypt for non-authors.
    suspend fun convertEmojiPackWithPrivate(pack: EmojiPackEvent): List<EmojiMedia> = pack.allEmojis(signer).toEmojiMedia()

    private fun List<EmojiUrlTag>.toEmojiMedia() = map { EmojiMedia(it.code, it.url) }

    /** One selected pack, with its private emojis decrypted when this signer authored it. */
    @Immutable
    class EmojiPackGroup(
        val address: String,
        val title: String?,
        val image: String?,
        val emojis: List<EmojiMedia>,
    )

    // An emoji list can name the same pack twice; the picker keys its sections by address.
    suspend fun groupPacksWithPrivate(list: Array<NoteState>): List<EmojiPackGroup> =
        list.distinctBy { it.note.idHex }.mapNotNull { state ->
            val pack = state.note.event as? EmojiPackEvent ?: return@mapNotNull null
            val emojis = convertEmojiPackWithPrivate(pack)
            if (emojis.isEmpty()) return@mapNotNull null
            EmojiPackGroup(state.note.idHex, pack.titleOrName(), pack.image(), emojis)
        }

    /** The account's selected packs, in emoji-list order; packs not loaded yet are left out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val myPacks: StateFlow<List<EmojiPackGroup>> =
        flow
            .transformLatest { emojiList ->
                if (emojiList != null) {
                    emitAll(
                        combineTransform(emojiList) {
                            emit(groupPacksWithPrivate(it))
                        },
                    )
                } else {
                    emit(emptyList())
                }
            }.onStart {
                emit(
                    groupPacksWithPrivate(
                        convertEmojiSelectionPack(
                            getEmojiList(),
                        )?.map { it.value }?.toTypedArray() ?: emptyArray(),
                    ),
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /**
     * Every shortcode the selected packs define, each with the image it resolves to: the first
     * pack in the emoji list that uses a code wins, as [findEmojiTags] resolves it, since an event
     * can carry only one `emoji` tag per code.
     */
    val myEmojis: StateFlow<List<EmojiMedia>> =
        myPacks
            .map(::firstByCode)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /** An emoji picker favorite: a Unicode emoji, or a custom emoji of a selected pack. */
    @Immutable
    sealed class Favorite {
        @Immutable
        class Unicode(
            val emoji: String,
        ) : Favorite()

        @Immutable
        class Custom(
            val media: EmojiMedia,
        ) : Favorite()
    }

    /**
     * The account's reaction choices as emoji picker favorites. A custom emoji shows only while a
     * selected pack still carries it: its `:shortcode:` resolves to an `emoji` tag only against
     * those packs.
     */
    val favorites: StateFlow<List<Favorite>> =
        combine(reactionChoices, myEmojis, ::resolveFavorites)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /**
     * Resolves every `:shortcode:` in [message] against the account's selected
     * emoji packs, returning the NIP-30 `emoji` tags an event needs to carry for
     * the codes to render. Unknown codes are simply skipped.
     */
    fun findEmojiTags(message: String): List<EmojiUrlTag> {
        // From myPacks, not myEmojis: the derived flow can lag a pack the picker already shows.
        val myEmojiSet = firstByCode(myPacks.value)
        if (myEmojiSet.isEmpty()) return emptyList()
        val byCode = myEmojiSet.associateBy { it.code }
        return CustomEmoji
            .findAllEmojiCodes(message)
            .distinct()
            .mapNotNull { code ->
                byCode[code]?.let { EmojiUrlTag(it.code, it.link) }
            }
    }

    suspend fun addEmojiPack(emojiPack: Note): EmojiListEvent {
        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) throw IllegalArgumentException("Note is not an EmojiPackEvent; cannot add to emoji list.")

        val eventHint = emojiPack.toEventHint<EmojiPackEvent>() ?: throw IllegalArgumentException("Cannot build event hint for this emoji pack.")

        val usersEmojiList = getEmojiList()
        return if (usersEmojiList == null) {
            val template = EmojiListEvent.build(listOf(eventHint))
            signer.sign(template)
        } else {
            val template = EmojiListEvent.add(usersEmojiList, eventHint)
            signer.sign(template)
        }
    }

    suspend fun removeEmojiPack(emojiPack: Note): EmojiListEvent? {
        val usersEmojiList = getEmojiList() ?: throw IllegalArgumentException("No emoji pack selection exists to remove from.")

        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) return null

        val template = EmojiListEvent.remove(usersEmojiList, emojiPackEvent)
        return signer.sign(template)
    }

    companion object {
        /** The packs' emojis with one entry per shortcode, the first pack's winning. */
        fun firstByCode(packs: List<EmojiPackGroup>): List<EmojiMedia> = packs.flatMap { it.emojis }.distinctBy { it.code }

        /**
         * Maps reaction choices to favorites: `+` and `-` (like and dislike, not emoji) are
         * dropped, a custom emoji (`:shortcode:url`, or a bare `:shortcode:`) is kept only when
         * [myEmojis] holds it, and anything else is a Unicode emoji. Duplicates are dropped.
         */
        fun resolveFavorites(
            choices: List<String>,
            myEmojis: List<EmojiMedia>,
        ): List<Favorite> {
            val byLink = myEmojis.associateBy { it.link }
            val byCode = myEmojis.associateBy { it.code }
            val seen = mutableSetOf<String>()

            return choices.mapNotNull { choice ->
                if (choice.isBlank() || choice == "+" || choice == "-" || !seen.add(choice)) return@mapNotNull null

                val url = choice.customEmojiUrlOrNull()
                when {
                    url != null -> byLink[url]?.let { Favorite.Custom(it) }
                    choice.length > 2 && choice.startsWith(':') && choice.endsWith(':') -> byCode[choice.substring(1, choice.length - 1)]?.let { Favorite.Custom(it) }
                    else -> Favorite.Unicode(choice)
                }
            }
        }
    }
}

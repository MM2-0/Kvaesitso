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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.petnames.PetnameResolver
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlin.concurrent.Volatile

/**
 * The account's own kind:30382 contact cards — one card per target user, signed
 * by the account's main key. This is how the user nicknames other users: the
 * petname and summary always live in the card's NIP-44 encrypted content.
 *
 * The same kind is used by trust providers for WoT scores; those cards are
 * ignored here because everything is keyed on the card author being the
 * account itself ([signer]'s pubkey).
 */
@Stable
class UserAssertionsState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: UserAssertionDecryptionCache,
    val emojiPacks: EmojiPackState,
) {
    private val accountUser: User? by lazy { cache.getOrCreateUser(signer.pubKey) }

    // The account's own kind-3 follow list, whose `p` tags may carry NIP-02 petnames.
    private val followListNote: AddressableNote by lazy { cache.getOrCreateAddressableNote(ContactListEvent.createAddress(signer.pubKey)) }

    // pubkey -> petname for the latest follow list, rebuilt only when that list changes. An immutable
    // snapshot published through a volatile field: the flows rebuild it on an IO thread while
    // composition reads it through [cachedFollowListPetname], and a racing rebuild only costs a
    // redundant (identical) index, never a torn one.
    private class PetnameIndex(
        val event: ContactListEvent,
        val petnames: Map<HexKey, String>,
    )

    @Volatile
    private var petnameIndex: PetnameIndex? = null

    /** Looks [target] up in [followList]'s index, rebuilding it when stale. Parses tags: keep off the main thread. */
    private fun followListPetname(
        followList: ContactListEvent?,
        target: HexKey,
    ): String? {
        if (followList == null) return null
        val index =
            petnameIndex?.takeIf { it.event === followList }
                ?: PetnameIndex(followList, PetnameResolver.petnames(followList.tags)).also { petnameIndex = it }
        return index.petnames[target]
    }

    fun createCardAddress(target: HexKey): Address = UserAssertionEvent.createAddress(signer.pubKey, target)

    fun getCardNote(target: HexKey): AddressableNote = cache.getOrCreateAddressableNote(createCardAddress(target))

    fun getCard(target: HexKey): UserAssertionEvent? = getCardNote(target).event as? UserAssertionEvent

    /**
     * The account's own card about [target], as attached to the target user's
     * [UserCardsCache] when the event is consumed. `cards()` lazily allocates the
     * per-user cache (like `metadata()` does) so a card arriving later is seen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun myCardFlow(target: User): Flow<UserAssertionEvent?> =
        target
            .cards()
            .receivedCards
            .map { it[accountUser] }
            .distinctUntilChanged()
            .flatMapLatest { note ->
                note
                    ?.flow()
                    ?.metadata
                    ?.stateFlow
                    ?.map { it.note.event as? UserAssertionEvent }
                    ?: flowOf(null)
            }

    /**
     * The nickname (petname + private summary) the account gave [target],
     * decrypted from the card's content, along with the card's tags so
     * `:shortcode:` custom emojis resolve.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun nicknameFlow(target: User): Flow<Nickname?> =
        myCardFlow(target)
            .mapLatest { card -> card?.let { decryptionCache.nickname(it) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * Synchronously returns the nickname for [target] when its card is already
     * decrypted (or has none to decrypt). Cheap enough for initial values of UI
     * flows: a map read plus the decryption cache lookup, no crypto.
     */
    fun cachedNickname(target: User): Nickname? {
        val card =
            target
                .cardsOrNull()
                ?.receivedCards
                ?.value
                ?.get(accountUser)
                ?.event as? UserAssertionEvent ?: return null
        return decryptionCache.cachedNickname(card)
    }

    /**
     * The NIP-02 petname the account's own follow list (kind 3) gives [target], if any. It is the
     * fallback for display when the account has no NIP-85 nickname for them.
     */
    fun followListPetnameFlow(target: User): Flow<String?> =
        followListNote
            .flow()
            .metadata
            .stateFlow
            .map { followListPetname(it.note.event as? ContactListEvent, target.pubkeyHex) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * Synchronous counterpart of [followListPetnameFlow], for initial values in composition: it only
     * reads the last index the flows built (possibly for the previous version of the follow list —
     * the flow corrects it right after) and never parses the follow list on the calling thread.
     */
    fun cachedFollowListPetname(target: User): String? = petnameIndex?.petnames?.get(target.pubkeyHex)

    /**
     * The name the account knows [target] by, for rendering: the NIP-85 nickname when it has a
     * petname, otherwise the kind-3 (NIP-02) petname. Use [nicknameFlow] instead when editing
     * the nickname, which must not pick up the follow-list fallback.
     */
    fun displayNicknameFlow(target: User): Flow<Nickname?> =
        combine(nicknameFlow(target), followListPetnameFlow(target), ::withFollowListFallback)
            .distinctUntilChanged()

    /** Synchronous counterpart of [displayNicknameFlow], from already-decrypted data. */
    fun cachedDisplayNickname(target: User): Nickname? = withFollowListFallback(cachedNickname(target), cachedFollowListPetname(target))

    private fun withFollowListFallback(
        nickname: Nickname?,
        followListPetname: String?,
    ): Nickname? =
        if (nickname?.petName != null || followListPetname == null) {
            nickname
        } else {
            // The card's tags can't describe a name that didn't come from the card.
            Nickname(followListPetname, nickname?.summary, EmptyTagList)
        }

    /**
     * The name to render for [target]: the NIP-85 nickname the account gave them (NIP-81 policy),
     * then the account's NIP-02 follow-list petname — the user's own local name for the contact, so
     * it too wins over the profile's self-chosen name — then the profile's display name, falling
     * back to the short npub.
     */
    fun displayNameFlow(target: User): Flow<String> =
        combine(
            target.metadata().flow,
            displayNicknameFlow(target),
        ) { info, nickname ->
            nickname?.petName ?: info?.info?.bestName() ?: target.pubkeyDisplayHex()
        }.distinctUntilChanged()

    /** Synchronous first value for [displayNameFlow], from already-decrypted data. */
    fun cachedDisplayName(target: User): String = cachedDisplayNickname(target)?.petName ?: target.toBestDisplayName()

    suspend fun petName(target: HexKey): String? = getCard(target)?.let { decryptionCache.petName(it) }

    suspend fun summary(target: HexKey): String? = getCard(target)?.let { decryptionCache.summary(it) }

    /**
     * Builds the new signed card for [target] with the given petname and summary
     * (`null` clears a field), preserving every other tag of an existing card.
     * Any `:shortcode:` from the account's emoji packs gets its NIP-30 emoji
     * mapping embedded; everything is stored NIP-44 encrypted. The caller is
     * responsible for publishing it.
     */
    suspend fun updatePetNameAndSummary(
        target: HexKey,
        petName: String?,
        summary: String?,
    ): UserAssertionEvent {
        val emojis = emojiPacks.findEmojiTags(listOfNotNull(petName, summary).joinToString(" "))
        val existing = getCard(target)
        return if (existing != null) {
            signer.sign(
                UserAssertionEvent.updatePetNameAndSummary(
                    earlierVersion = existing,
                    petName = petName,
                    summary = summary,
                    emojis = emojis,
                    signer = signer,
                ),
            )
        } else {
            UserAssertionEvent.create(
                targetUser = target,
                petName = petName,
                summary = summary,
                emojis = emojis,
                signer = signer,
            )
        }
    }
}

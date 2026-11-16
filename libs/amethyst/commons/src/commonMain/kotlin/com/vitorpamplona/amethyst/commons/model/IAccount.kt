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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.DualCase

/**
 * Interface for NIP-47 wallet connect signer state.
 * Used by Note.kt for checking NWC payment status.
 */
interface INwcSignerState {
    suspend fun decryptResponse(event: NwcResponseEvent): Response?

    suspend fun decryptRequest(event: NwcRequestEvent): Request?

    fun isNIP47Author(pubKey: String?): Boolean
}

/**
 * Hidden content settings for filtering notes.
 * Used by Note.isHiddenFor() to check if content should be hidden.
 */
data class LiveHiddenUsers(
    val showSensitiveContent: Boolean?,
    val hiddenWordsCase: List<DualCase>,
    val hiddenUsersHashCodes: Set<Int>,
    val spammersHashCodes: Set<Int>,
    // Raw sets for amethyst-specific usage
    val hiddenUsers: Set<String> = emptySet(),
    val spammers: Set<String> = emptySet(),
    val hiddenWords: Set<String> = emptySet(),
    val mutedThreads: Set<String> = emptySet(),
    // Lowercased hashtags (without the leading '#') the user has muted via NIP-51 `"t"` entries.
    val hiddenHashtags: Set<String> = emptySet(),
    val maxHashtagLimit: Int = 8,
) {
    fun isUserHidden(userHex: String) = hiddenUsers.contains(userHex) || spammers.contains(userHex)

    fun isThreadMuted(rootHex: String) = mutedThreads.contains(rootHex)

    fun isHashtagHidden(hashtag: String) = hiddenHashtags.contains(hashtag.lowercase())

    companion object {
        /** Neutral value that hides nothing — a safe default before any list has loaded. */
        val EMPTY =
            LiveHiddenUsers(
                showSensitiveContent = null,
                hiddenWordsCase = emptyList(),
                hiddenUsersHashCodes = emptySet(),
                spammersHashCodes = emptySet(),
            )
    }
}

/**
 * Interface for account operations needed by Note.kt.
 * Abstracts Android-specific Account class for use in commons.
 */
interface IAccount {
    /** NIP-47 wallet connect state for payment verification */
    val nip47SignerState: INwcSignerState

    /** Private zaps decryption cache */
    val privateZapsDecryptionCache: IPrivateZapsDecryptionCache

    /** Current user's profile */
    fun userProfile(): User

    /** Whether account has write permissions */
    fun isWriteable(): Boolean

    /** Nostr signer for signing/encrypting events */
    val signer: NostrSigner

    /** Current user's public key */
    val pubKey: String

    /** Content filter settings */
    val showSensitiveContent: Boolean?
    val hiddenWordsCase: List<DualCase>
    val hiddenUsersHashCodes: Set<Int>
    val spammersHashCodes: Set<Int>

    /** Set of followed user pubkeys (for feed ordering/highlighting) */
    fun followingKeySet(): Set<String>

    /**
     * The Web of Trust provider's score for [pubkey] (1..100), or null when it has none or no
     * network is loaded. Lists of people sort on it after follows: the ones you follow, then the
     * highest scores, then everyone else.
     */
    fun trustRankOf(pubkey: String): Int? = null

    fun isHidden(user: User): Boolean

    /** Chatroom list for private DM conversations */
    val chatroomList: ChatroomList

    /** Marmot MLS group chat list */
    val marmotGroupList: MarmotGroupList

    /** Whether a note is acceptable (not hidden, not blocked, etc.) */
    fun isAcceptable(note: Note): Boolean

    /** Send a NIP-04 encrypted direct message */
    suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<EncryptedDmEvent>)

    /** Send a NIP-17 gift-wrapped direct message */
    suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>)

    /** Send a NIP-17 gift-wrapped encrypted file header */
    suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>)

    /** Broadcast pre-created gift wraps (e.g. reactions within group DMs) */
    suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>)
}

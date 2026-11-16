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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrEventUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.messages.changeSubject
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Slim shared state for DM message composition.
 * Holds only core fields needed for typing and sending messages.
 *
 * Platform-specific concerns (uploads, emoji suggestions, drafts, zapraiser,
 * location, user suggestions) remain in the platform ViewModel layer.
 *
 * Used by both Android ChatNewMessageViewModel and Desktop DM screen.
 */
@Stable
class ChatNewMessageState(
    val account: IAccount,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
    /**
     * Optional resolver for probing kind:10050 via curated indexer relays
     * when a peer's DM inbox isn't in the local cache. When provided,
     * [updateRecipientRelayStatus] falls through to the resolver on a cache
     * miss so the pre-send UI stops falsely reporting "recipient has no DM
     * relay list" for accounts whose 10050 sits on an indexer we haven't
     * subscribed to yet.
     *
     * When null (default, e.g. Android's ChatNewMessageViewModel which
     * hasn't been wired to a resolver yet), behaviour matches the
     * cache-only strict check.
     */
    private val dmInboxResolver: (suspend (HexKey) -> List<NormalizedRelayUrl>?)? = null,
    /**
     * Optional NIP-30 custom-emoji pack state. When provided, [sendNip17]
     * resolves any `:shortcode:` in the outgoing message against the account's
     * selected packs and attaches the matching `emoji` tags so recipients render
     * the custom emojis. Null (default) → no custom-emoji tagging.
     */
    private val emojiPacks: EmojiPackState? = null,
) {
    private val _message = MutableStateFlow(TextFieldValue(""))
    val message: StateFlow<TextFieldValue> = _message.asStateFlow()

    private val _replyTo = MutableStateFlow<Note?>(null)
    val replyTo: StateFlow<Note?> = _replyTo.asStateFlow()

    private val _subject = MutableStateFlow(TextFieldValue(""))
    val subject: StateFlow<TextFieldValue> = _subject.asStateFlow()

    private val _room = MutableStateFlow<ChatroomKey?>(null)
    val room: StateFlow<ChatroomKey?> = _room.asStateFlow()

    /** Whether any recipients are missing DM relay lists, preventing message delivery */
    private val _recipientsMissingDmRelays = MutableStateFlow(false)
    val recipientsMissingDmRelays: StateFlow<Boolean> = _recipientsMissingDmRelays.asStateFlow()

    /** Whether a message can be sent (non-blank text + room set + all recipients have DM relays) */
    val canSend: Boolean
        get() = _message.value.text.isNotBlank() && _room.value != null && !_recipientsMissingDmRelays.value

    /**
     * Load a chatroom. Sets the room key and checks recipient DM relay availability.
     */
    fun load(roomKey: ChatroomKey) {
        _room.value = roomKey
        updateRecipientRelayStatus()
    }

    /**
     * Check whether every participant in the current room is reachable via
     * NIP-17 — i.e. has a published kind:10050 that lists at least one
     * relay.
     *
     * Uses the **strict** check ([com.vitorpamplona.amethyst.commons.model.User.dmInboxRelaysStrict],
     * kind:10050 only) instead of the lenient [dmInboxRelays] that falls
     * back to the NIP-65 read marker — the send path also uses strict
     * resolution (see `DesktopIAccount.resolveDmInboxRelaysStrict`), and
     * disagreement here caused the pre-send UI to green-light sends that
     * would then fail at send time.
     *
     * Two-phase check:
     *
     * 1. Synchronous cache check — every peer's kind:10050 sits in
     *    [cache]. If all present with at least one relay, unblock
     *    immediately.
     * 2. Async resolver probe (only when [dmInboxResolver] is provided) —
     *    for peers whose 10050 isn't cached, kick off a curated-indexer
     *    fan-out. If any peer's relays turn up, update the flag to
     *    unblock the composer without requiring the user to restart the
     *    conversation view.
     *
     * The blocking flag is set optimistically during the probe so the
     * user still sees the warning until we've confirmed the peer is
     * genuinely unreachable via NIP-17. This preserves the "don't allow
     * silent-fail sends" invariant.
     */
    fun updateRecipientRelayStatus() {
        val currentRoom = _room.value
        if (currentRoom == null) {
            _recipientsMissingDmRelays.value = false
            return
        }

        val missing =
            currentRoom.users.filter { hexKey ->
                val user = cache.getOrCreateUser(hexKey)
                user?.dmInboxRelaysStrict().isNullOrEmpty()
            }
        if (missing.isEmpty()) {
            _recipientsMissingDmRelays.value = false
            return
        }

        // Cache miss → block optimistically, then probe indexers for the
        // missing peers. If any of them turn up a kind:10050, unblock.
        _recipientsMissingDmRelays.value = true
        val resolver = dmInboxResolver ?: return

        scope.launch {
            val stillMissing =
                missing.any { hexKey ->
                    val fanOut = resolver(hexKey)
                    fanOut.isNullOrEmpty()
                }
            // The room may have changed while we were probing; only apply
            // the result if we're still looking at the same conversation.
            if (_room.value == currentRoom) {
                _recipientsMissingDmRelays.value = stillMissing
            }
        }
    }

    fun updateMessage(newMessage: TextFieldValue) {
        _message.value = newMessage
    }

    fun updateSubject(newSubject: TextFieldValue) {
        _subject.value = newSubject
    }

    fun setReply(note: Note) {
        _replyTo.value = note
    }

    fun clearReply() {
        _replyTo.value = null
    }

    /**
     * Send the current message as NIP-17. NIP-04 is deprecated for sending.
     *
     * @return true if send was initiated, false if preconditions not met
     */
    suspend fun send(): Boolean {
        val currentRoom = _room.value ?: return false
        val messageText = _message.value.text
        if (messageText.isBlank()) return false
        if (_recipientsMissingDmRelays.value) return false

        sendNip17(currentRoom, messageText)

        return true
    }

    private suspend fun sendNip17(
        room: ChatroomKey,
        messageText: String,
    ) {
        val pTags =
            room.users.mapNotNull { hexKey ->
                cache.getOrCreateUser(hexKey)?.toPTag()
            }

        val replyHint = _replyTo.value?.toEventHint<BaseDMGroupEvent>()
        val subjectText = _subject.value.text.ifBlank { null }
        val emojiTags = emojiPacks?.findEmojiTags(messageText)?.takeIf { it.isNotEmpty() }

        val template =
            if (replyHint == null) {
                ChatMessageEvent.build(messageText, pTags) {
                    subjectText?.let { changeSubject(it) }
                    hashtags(findHashtags(messageText))
                    references(findURLs(messageText))
                    quotes(findNostrEventUris(messageText))
                    emojiTags?.let { emojis(it) }
                }
            } else {
                ChatMessageEvent.reply(messageText, replyHint) {
                    subjectText?.let { changeSubject(it) }
                    hashtags(findHashtags(messageText))
                    references(findURLs(messageText))
                    quotes(findNostrEventUris(messageText))
                    emojiTags?.let { emojis(it) }
                }
            }

        account.sendNip17PrivateMessage(template)
    }

    /**
     * Clear all composition state after sending or cancelling.
     */
    fun clear() {
        _message.value = TextFieldValue("")
        _subject.value = TextFieldValue("")
        _replyTo.value = null
    }

    /**
     * Format room users as npub display string.
     * Useful for showing recipients in the UI.
     */
    fun toUsersDisplay(): String {
        val currentRoom = _room.value ?: return ""
        return currentRoom.users
            .mapNotNull { hexKey ->
                runCatching { Hex.decode(hexKey).toNpub() }.getOrNull()
            }.joinToString(", ") { "@$it" }
    }
}

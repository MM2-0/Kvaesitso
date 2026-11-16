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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.PrivateReplaceableTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * True when this event's `content` field holds ciphertext instead of readable text.
 *
 * These are exactly the kinds whose plaintext has to come out of a decryption cache; their
 * raw `content` is a base64 blob (NIP-04 `<ct>?iv=<iv>` or a NIP-44 payload) and must never
 * reach the screen. Anything else — including a NIP-17 rumor, whose content is already
 * plaintext by the time it lands in the cache — is safe to render verbatim.
 *
 * Deliberately narrower than [Event.isContentEncoded], which also covers merely
 * *machine-readable* content (kind:0 JSON, OTS blobs, marketplace payloads) that callers
 * legitimately read raw.
 */
fun Event.hasEncryptedContent(): Boolean =
    when (this) {
        is EncryptedDmEvent -> true
        is DraftWrapEvent -> true
        is SealEvent -> true
        is GiftWrapEvent -> true
        is ZapRequestEvent -> isPrivateZap()
        // Every NIP-51 list keeps its private members as a NIP-44 payload in `content`. The two
        // base classes cover all ~30 list kinds, present and future, which is the point: naming
        // them one at a time is how kind 30005 came to print its own ciphertext on screen.
        // A list with no private members carries an empty `content`, and the guard below skips it.
        is PrivateTagArrayEvent -> content.isNotEmpty()
        is PrivateReplaceableTagArrayEvent -> content.isNotEmpty()
        else -> false
    }

/**
 * A NIP-17 message that only renames the conversation: it carries a `subject` tag and no text.
 * It has nothing to show as a message body, so the feed, the room list and the notification each
 * narrate it as a rename instead. A rename sent with an explanation is a regular message.
 *
 * Checks the content first: almost every message has text, and `isBlank` stops at its first
 * non-space character, so the tag scan only runs for the rare empty one.
 */
fun Event.isSubjectOnlyChatMessage(): Boolean = this is ChatMessageEvent && content.isBlank() && subject() != null

/**
 * What a chat row should render for a message, once the raw ciphertext is off the table.
 *
 * [Decrypting] and [Undecryptable] are kept apart on purpose: a message that is merely
 * still being decrypted resolves on its own and must not be labelled undecryptable.
 */
sealed interface ChatPreview {
    /** Readable text, ready to render. */
    data class Body(
        val text: String,
    ) : ChatPreview

    /** A message that only renamed the conversation ([isSubjectOnlyChatMessage]) to [subject]. */
    data class SubjectChange(
        val subject: String,
    ) : ChatPreview

    /** Encrypted, decryptable by this account, plaintext not available yet. */
    data object Decrypting : ChatPreview

    /** Encrypted and this account can never read it (no key, or not a party to the DM). */
    data object Undecryptable : ChatPreview

    /** No event at all — the row is referencing something we never received. */
    data object Missing : ChatPreview
}

/**
 * Pure classifier for the preview text of a chat message.
 *
 * @param event the message, or null when the note carries no event yet.
 * @param decrypted plaintext already available from the decryption cache, if any.
 * @param myPubKey the logged-in account's pubkey; null when unknown.
 * @param canDecrypt whether the account holds (or can reach) a key at all — i.e.
 *   `Account.isWriteable()`. A read-only npub login is false.
 */
fun chatPreviewOf(
    event: Event?,
    decrypted: String?,
    myPubKey: HexKey?,
    canDecrypt: Boolean,
): ChatPreview {
    if (event == null) return ChatPreview.Missing

    if (event.isSubjectOnlyChatMessage()) return ChatPreview.SubjectChange(event.subject() ?: "")

    if (!event.hasEncryptedContent()) return ChatPreview.Body(decrypted ?: event.content)

    // Never trust `event.content` from here down: it is ciphertext.
    if (decrypted != null) return ChatPreview.Body(decrypted)

    if (!canDecrypt) return ChatPreview.Undecryptable

    // A kind:4 addressed to neither me nor from me can't be opened with my key, ever.
    if (event is EncryptedDmEvent && myPubKey != null && !event.isIncluded(myPubKey)) {
        return ChatPreview.Undecryptable
    }

    return ChatPreview.Decrypting
}

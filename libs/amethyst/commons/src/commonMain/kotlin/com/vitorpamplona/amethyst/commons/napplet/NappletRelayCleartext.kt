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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2

/**
 * NAP-RELAY read boundary: encrypted event content is decrypted or withheld, never exposed.
 *
 * Decrypting is reading the user's private messages, so it happens only when [mayDecrypt] says the
 * app holds a decrypt grant for that counterparty (the broker's [NappletBroker.mayDecrypt]: a
 * standing or session grant, never a prompt). Without one the event is withheld, as it is when the
 * signer can't decrypt it.
 *
 * The counterparty is never taken from a tag the app could have written: an app that can publish
 * a reaction as the user could put any ciphertext in it, name any peer in its `p` tag, and read the
 * event back decrypted. So an event the user wrote is decrypted against its recipient only when it
 * is a NIP-04 DM; any other is the user's own self-encrypted content (private lists, drafts),
 * decrypted against the user.
 */
object NappletRelayCleartext {
    suspend fun forDelivery(
        event: Event,
        signer: NostrSigner,
        mayDecrypt: suspend (peer: HexKey) -> Boolean,
    ): Event? = forDelivery(event, signer.pubKey, signer::decrypt, mayDecrypt)

    suspend fun forDelivery(
        event: Event,
        userPubKey: HexKey,
        decrypt: suspend (String, HexKey) -> String,
        mayDecrypt: suspend (peer: HexKey) -> Boolean,
    ): Event? {
        if (!isEncrypted(event)) return event

        val peer = peerOf(event, userPubKey) ?: return null
        if (!mayDecrypt(peer)) return null
        val cleartext = runCatching { decrypt(event.content, peer) }.getOrNull() ?: return null

        // NAP-RELAY defines a decrypted read projection. Retain the relay event's identity and
        // signature fields so callers can still correlate it, while making clear that this object
        // must never be republished as a signed event after its content projection has changed.
        return Event(event.id, event.pubKey, event.createdAt, event.kind, event.tags, cleartext, event.sig)
    }

    /** Whose key the content of [event] is encrypted with, from the user's side; null when it isn't the user's to read. */
    fun peerOf(
        event: Event,
        userPubKey: HexKey,
    ): HexKey? =
        when {
            event.pubKey == userPubKey -> if (event.kind == EncryptedDmEvent.KIND) event.recipientPubKey() else userPubKey
            event.isAddressedTo(userPubKey) -> event.pubKey
            else -> null
        }

    fun isEncrypted(event: Event): Boolean =
        event is EncryptedDmEvent ||
            EncryptedInfo.isNIP04(event.content) ||
            isNip44V2(event.content)

    private fun isNip44V2(content: String): Boolean =
        content.length >= MIN_NIP44_V2_LENGTH &&
            runCatching { Nip44v2.EncryptedInfo.decodePayload(content) }.isSuccess

    private fun Event.recipientPubKey(): HexKey? =
        tags.firstNotNullOfOrNull { tag ->
            tag.getOrNull(1)?.takeIf { tag.getOrNull(0) == "p" }
        }

    private fun Event.isAddressedTo(pubKey: HexKey): Boolean = tags.any { tag -> tag.getOrNull(0) == "p" && tag.getOrNull(1) == pubKey }

    private const val MIN_NIP44_V2_LENGTH = 132
}

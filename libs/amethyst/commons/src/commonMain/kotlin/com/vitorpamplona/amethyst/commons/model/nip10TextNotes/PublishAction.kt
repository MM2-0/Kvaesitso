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
package com.vitorpamplona.amethyst.commons.model.nip10TextNotes

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs

/**
 * Shared action for publishing new text notes.
 * Handles replies, hashtags, and URL references.
 */
object PublishAction {
    /**
     * Publishes a text note (NIP-01 kind 1).
     *
     * @param content The text content of the note
     * @param signer The NostrSigner to sign the event
     * @param replyTo Optional event to reply to (adds e-tag and p-tag)
     * @return Signed TextNoteEvent ready to broadcast
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun publishTextNote(
        content: String,
        signer: NostrSigner,
        replyTo: Event? = null,
    ): TextNoteEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot publish: signer is not writeable")
        }

        val template =
            TextNoteEvent.build(content) {
                // If replying, add e-tag and p-tag
                if (replyTo != null) {
                    val etag = ETag(replyTo.id)
                    etag.relay = null
                    etag.author = replyTo.pubKey
                    eTag(etag)
                    pTag(PTag(replyTo.pubKey, relayHint = null))
                }

                // Extract hashtags and URLs from content
                hashtags(findHashtags(content))
                references(findURLs(content))
            }

        return signer.sign(template)
    }

    /**
     * Publishes a reply to an existing note.
     */
    suspend fun publishReply(
        content: String,
        replyTo: Event,
        signer: NostrSigner,
    ): TextNoteEvent = publishTextNote(content, signer, replyTo)
}

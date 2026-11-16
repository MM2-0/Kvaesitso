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
package com.vitorpamplona.amethyst.commons.model.nip23LongContent

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Shared action for publishing long-form content (NIP-23 kind 30023).
 * Handles title, summary, image, tags, and d-tag for addressable events.
 */
object LongFormPublishAction {
    private const val MAX_CONTENT_BYTES = 100_000

    /**
     * Publishes a long-form text note (NIP-23 kind 30023).
     *
     * @param title The article title
     * @param content The markdown body content
     * @param summary Optional article summary
     * @param image Optional banner image URL
     * @param tags List of hashtag topics
     * @param dTag Unique identifier for this addressable event (slug)
     * @param signer The NostrSigner to sign the event
     * @return Signed LongFormContentEvent ready to broadcast
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun publish(
        title: String,
        content: String,
        summary: String?,
        image: String?,
        tags: List<String>,
        dTag: String,
        signer: NostrSigner,
    ): LongFormContentEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot publish: signer is not writeable")
        }

        if (content.encodeToByteArray().size > MAX_CONTENT_BYTES) {
            throw IllegalArgumentException("Content exceeds maximum size of $MAX_CONTENT_BYTES bytes")
        }

        val template =
            LongFormContentEvent.build(
                description = content,
                title = title,
                summary = summary,
                image = image,
                publishedAt = TimeUtils.now(),
                dTag = dTag,
            ) {
                tags.forEach { hashtag(it) }
            }

        return signer.sign(template)
    }
}

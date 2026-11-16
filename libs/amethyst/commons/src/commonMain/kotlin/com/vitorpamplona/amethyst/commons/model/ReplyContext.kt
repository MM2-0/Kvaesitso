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

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

data class ReplyContext(
    val parentNoteId: String?,
    val parentAuthorPubKey: String,
    val parentAuthorDisplay: String,
) {
    companion object {
        fun from(
            event: BaseThreadedEvent,
            cache: ICacheProvider?,
        ): ReplyContext? {
            val raw = event.replyingToAddressOrEvent() ?: return null
            val isAddressable = raw.contains(":")
            val parentNoteId = if (isAddressable) null else raw

            val parentAuthorPubKey =
                when (event) {
                    is CommentEvent -> event.replyAuthor()?.pubKey
                    else -> null
                } ?: parentNoteId?.let { cache?.getNoteIfExists(it)?.author?.pubkeyHex }
                    ?: return null

            val parentAuthorDisplay =
                cache?.getUserIfExists(parentAuthorPubKey)?.toBestDisplayName()
                    ?: (parentAuthorPubKey.take(8) + "…")

            return ReplyContext(parentNoteId, parentAuthorPubKey, parentAuthorDisplay)
        }
    }
}

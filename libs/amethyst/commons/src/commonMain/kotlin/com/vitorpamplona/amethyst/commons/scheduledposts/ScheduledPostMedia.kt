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
@file:Suppress("ktlint:standard:filename")

package com.vitorpamplona.amethyst.commons.scheduledposts

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

private const val TAG = "ScheduledPostMedia"

sealed class MediaUrl {
    abstract val url: String

    data class Image(
        override val url: String,
    ) : MediaUrl()

    data class Video(
        override val url: String,
    ) : MediaUrl()
}

/**
 * Parse the signed-event JSON, run [block], and return its result. Returns null on
 * any non-cancellation parse failure and logs a warning. The shared shape for
 * [extractFirstMediaUrl], [extractEventId], and [extractContentPreview].
 */
private inline fun <T : Any> parseSignedEvent(
    post: ScheduledPost,
    caller: String,
    block: (Event) -> T?,
): T? =
    try {
        block(Event.fromJson(post.signedEventJson))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG) { "$caller: failed to parse signed event for ${post.id}: ${e.message}" }
        null
    }

/**
 * Returns the first media URL referenced via an imeta tag in the post's signed event,
 * or null when there is no imeta tag or the JSON cannot be parsed.
 *
 * Mime starting with `video/` -> [MediaUrl.Video]; anything else (including absent mime)
 * -> [MediaUrl.Image]. Lenient on purpose: most posts attach images and don't always
 * carry an `m` property.
 */
fun extractFirstMediaUrl(post: ScheduledPost): MediaUrl? =
    parseSignedEvent(post, "extractFirstMediaUrl") { event ->
        val firstImeta = event.imetas().firstOrNull() ?: return@parseSignedEvent null
        val mime = firstImeta.properties["m"]?.firstOrNull().orEmpty()
        when {
            mime.startsWith("video/") -> MediaUrl.Video(firstImeta.url)
            else -> MediaUrl.Image(firstImeta.url)
        }
    }

/**
 * Returns the signed event's id, or null when the JSON cannot be parsed.
 */
fun extractEventId(post: ScheduledPost): String? = parseSignedEvent(post, "extractEventId") { it.id }

/**
 * Returns the first [maxLen] chars of the signed event's content (trimmed) or
 * an empty string when the JSON cannot be parsed.
 */
fun extractContentPreview(
    post: ScheduledPost,
    maxLen: Int,
): String =
    parseSignedEvent(post, "extractContentPreview") { event ->
        event.content.take(maxLen).trim()
    } ?: ""

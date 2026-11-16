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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.scheduledposts

import com.vitorpamplona.amethyst.commons.scheduledposts.MediaUrl
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.scheduledposts.extractEventId
import com.vitorpamplona.amethyst.commons.scheduledposts.extractFirstMediaUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledPostMediaTest {
    private fun postWithJson(json: String) =
        ScheduledPost(
            id = "id",
            accountPubkey = "pk",
            signedEventJson = json,
            relayUrls = emptyList(),
            extraEventsJson = emptyList(),
            publishAtSec = 0,
            createdAtSec = 0,
            status = ScheduledPostStatus.PENDING,
        )

    @Test
    fun extractFirstMediaUrl_returns_null_when_no_imeta() {
        val json = """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[],"content":"hi","sig":"x"}"""
        assertNull(extractFirstMediaUrl(postWithJson(json)))
    }

    @Test
    fun extractFirstMediaUrl_returns_image_when_mime_starts_with_image() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","url https://x/a.jpg","m image/jpeg"]],"content":"hi","sig":"x"}"""
        val result = extractFirstMediaUrl(postWithJson(json))
        assertTrue(result is MediaUrl.Image)
        assertEquals("https://x/a.jpg", (result as MediaUrl.Image).url)
    }

    @Test
    fun extractFirstMediaUrl_returns_video_when_mime_starts_with_video() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","url https://x/v.mp4","m video/mp4"]],"content":"hi","sig":"x"}"""
        val result = extractFirstMediaUrl(postWithJson(json))
        assertTrue(result is MediaUrl.Video)
        assertEquals("https://x/v.mp4", (result as MediaUrl.Video).url)
    }

    @Test
    fun extractFirstMediaUrl_defaults_to_image_when_no_mime() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","url https://x/a.jpg"]],"content":"hi","sig":"x"}"""
        val result = extractFirstMediaUrl(postWithJson(json))
        assertTrue(result is MediaUrl.Image)
        assertEquals("https://x/a.jpg", (result as MediaUrl.Image).url)
    }

    @Test
    fun extractFirstMediaUrl_picks_first_when_multiple_imeta() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","url https://x/a.jpg","m image/jpeg"],["imeta","url https://y/v.mp4","m video/mp4"]],"content":"hi","sig":"x"}"""
        val result = extractFirstMediaUrl(postWithJson(json))
        assertTrue(result is MediaUrl.Image)
        assertEquals("https://x/a.jpg", (result as MediaUrl.Image).url)
    }

    @Test
    fun extractFirstMediaUrl_returns_null_when_json_malformed() {
        assertNull(extractFirstMediaUrl(postWithJson("{not json}")))
    }

    @Test
    fun extractFirstMediaUrl_returns_null_when_imeta_has_no_url() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","m image/jpeg"]],"content":"hi","sig":"x"}"""
        assertNull(extractFirstMediaUrl(postWithJson(json)))
    }

    @Test
    fun extractFirstMediaUrl_picks_first_when_video_precedes_image() {
        val json =
            """{"id":"a","pubkey":"p","kind":1,"created_at":0,"tags":[["imeta","url https://x/v.mp4","m video/mp4"],["imeta","url https://y/a.jpg","m image/jpeg"]],"content":"hi","sig":"x"}"""
        val result = extractFirstMediaUrl(postWithJson(json))
        assertTrue(result is MediaUrl.Video)
        assertEquals("https://x/v.mp4", (result as MediaUrl.Video).url)
    }

    @Test
    fun extractEventId_returns_id_for_well_formed_json() {
        val json =
            """{"id":"abc123","pubkey":"p","kind":1,"created_at":0,"tags":[],"content":"hi","sig":"x"}"""
        assertEquals("abc123", extractEventId(postWithJson(json)))
    }

    @Test
    fun extractEventId_returns_null_when_json_malformed() {
        assertNull(extractEventId(postWithJson("{not json}")))
    }
}

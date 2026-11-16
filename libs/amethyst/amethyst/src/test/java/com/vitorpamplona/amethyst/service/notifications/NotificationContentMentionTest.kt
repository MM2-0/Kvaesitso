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
package com.vitorpamplona.amethyst.service.notifications

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [NotificationContent.resolveMentions] rewrites inline `nostr:npub` / `nostr:nprofile` tokens to a
 * cited user's `@DisplayName` and returns those users so a renderer can observe them until their kind:0
 * loads. This is what makes a notification body flip from `@npub1abc…` to `@RealName` in place, matching
 * the author-name enrichment the shade already does for the title.
 */
class NotificationContentMentionTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example/")!!
    private val cited = NostrSignerInternal(KeyPair())

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper (null == null reads
        // as "main"). Distinct mocks make it a worker thread. See BuzzDmNotificationResolutionTest.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    @Test
    fun `an unknown cited npub is replaced with its short handle and returned for observation`() {
        val npub = NPub.create(cited.pubKey)
        val resolved = NotificationContent.resolveMentions("hey nostr:$npub how are you")

        assertFalse("the raw npub token is gone", resolved.text.contains(npub))
        assertTrue("it is replaced by an @handle", resolved.text.contains("@"))
        assertEquals("the cited user is returned for the enrichment window", 1, resolved.citedUsers.size)
        assertEquals(cited.pubKey, resolved.citedUsers.first().pubkeyHex)
    }

    @Test
    fun `a cited npub whose kind0 is loaded renders the real display name`() =
        runBlocking {
            val metadata = cited.sign(MetadataEvent.createNew(name = "Alice"))
            LocalCache.checkDeletionAndConsume(metadata, relay, false)

            val npub = NPub.create(cited.pubKey)
            val resolved = NotificationContent.resolveMentions("gm nostr:$npub")

            assertTrue("the display name fills in", resolved.text.contains("@Alice"))
            assertEquals(1, resolved.citedUsers.size)
        }

    @Test
    fun `event references are left untouched and not treated as cited users`() {
        val nevent = "nevent1qqstna2yrezu5wghjvswqqculvvwxsrcvu7uc0f78gan4xqhvz49d9spr3mhxue69uhkummnw3ez6un9d3shjtnwda"
        val resolved = NotificationContent.resolveMentions("look at nostr:$nevent here")

        assertTrue("the event ref stays verbatim", resolved.text.contains(nevent))
        assertTrue("no user was cited", resolved.citedUsers.isEmpty())
    }

    @Test
    fun `plain text with no mentions passes through unchanged`() {
        val resolved = NotificationContent.resolveMentions("just a normal note with no tags")

        assertEquals("just a normal note with no tags", resolved.text)
        assertTrue(resolved.citedUsers.isEmpty())
    }

    @Test
    fun `an inline image link is pulled out as the big picture and stripped from the body`() {
        val rendered = NotificationContent.renderNoteText("look at this https://example.com/cat.jpg amazing")

        assertEquals("https://example.com/cat.jpg", rendered.imageUrl)
        assertEquals("look at this amazing", rendered.text)
    }

    @Test
    fun `an image url on its own line becomes the big picture while the caption stays the body`() {
        val rendered = NotificationContent.renderNoteText("Check out my new photo\nhttps://example.com/pic.png")

        assertEquals("https://example.com/pic.png", rendered.imageUrl)
        assertEquals("Check out my new photo", rendered.text)
    }

    @Test
    fun `trailing punctuation after an image url is trimmed`() {
        assertEquals("https://example.com/a.webp", NotificationContent.firstImageUrl("nice pic https://example.com/a.webp."))
    }

    @Test
    fun `a video link is not treated as an image and stays in the text`() {
        val rendered = NotificationContent.renderNoteText("watch https://example.com/clip.mp4 now")

        assertEquals(null, rendered.imageUrl)
        assertTrue(rendered.text.contains("https://example.com/clip.mp4"))
    }

    @Test
    fun `a bare filename without a url scheme is not mistaken for an image`() {
        assertEquals(null, NotificationContent.firstImageUrl("my file is called cat.jpg locally"))
    }
}

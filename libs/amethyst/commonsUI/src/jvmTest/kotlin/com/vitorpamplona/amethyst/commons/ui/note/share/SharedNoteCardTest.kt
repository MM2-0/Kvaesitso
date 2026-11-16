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
package com.vitorpamplona.amethyst.commons.ui.note.share

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetas
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedNoteCardTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private val blossomUrl = "https://blossom.primal.net/66332885d206714439e39e441b6539622a07c108"

    private suspend fun kind1(
        content: String,
        imetaUrl: String? = null,
        mime: String? = null,
        alt: String? = null,
    ): Event =
        TextNoteEvent
            .build(content) {
                if (imetaUrl != null) {
                    imetas(
                        listOf(
                            IMetaTagBuilder(imetaUrl)
                                .apply {
                                    mime?.let { add("m", it) }
                                    alt?.let { add("alt", it) }
                                }.build(),
                        ),
                    )
                }
            }.let { aliceSigner.sign(it) }

    // ---- firstContentImage ----

    @Test
    fun imageImeta_isPickedAsTheThumbnailImage() =
        runTest {
            val note = kind1(content = blossomUrl, imetaUrl = blossomUrl, mime = "image/jpeg")

            val image = firstContentImage(note)

            assertEquals(blossomUrl, image?.url)
        }

    @Test
    fun plainTextNote_hasNoContentImage() =
        runTest {
            val note = kind1(content = "just some words, no media")

            assertNull(firstContentImage(note))
        }

    @Test
    fun videoImeta_isNotRenderedAsAnImage() =
        runTest {
            // A video-only imeta must not be fed to the image loader; the card falls back to the avatar.
            val note = kind1(content = "clip", imetaUrl = "https://cdn.example/v", mime = "video/mp4")

            assertNull(firstContentImage(note))
        }

    // ---- secondaryBodyTextFor ----

    @Test
    fun imageOnlyPost_stripsTheBareUrlToNothing() =
        runTest {
            // content is exactly the media URL — the whole point of the fix: never show the raw CDN URL.
            val note = kind1(content = blossomUrl, imetaUrl = blossomUrl, mime = "image/jpeg")

            assertNull(secondaryBodyTextFor(note, isGated = false))
        }

    @Test
    fun imageOnlyPostWithAlt_fallsBackToAltText() =
        runTest {
            val note = kind1(content = blossomUrl, imetaUrl = blossomUrl, mime = "image/jpeg", alt = "A sunset")

            assertEquals("A sunset", secondaryBodyTextFor(note, isGated = false))
        }

    @Test
    fun textPlusImage_keepsTheTextWithoutTheUrl() =
        runTest {
            val note = kind1(content = "check this out $blossomUrl", imetaUrl = blossomUrl, mime = "image/jpeg")

            assertEquals("check this out", secondaryBodyTextFor(note, isGated = false))
        }

    @Test
    fun gatedNote_yieldsNoBodyText() =
        runTest {
            val note = kind1(content = "something sensitive", imetaUrl = blossomUrl, mime = "image/jpeg", alt = "nsfw")

            assertNull(secondaryBodyTextFor(note, isGated = true))
        }

    @Test
    fun article_prefersItsTitle() =
        runTest {
            val note =
                LongFormContentEvent
                    .build("the body", "My Article", dTag = "a1") {}
                    .let { aliceSigner.sign(it) }

            assertEquals("My Article", secondaryBodyTextFor(note, isGated = false))
        }

    // ---- IMetaTag.isImage ----

    @Test
    fun isImage_trueForImageMime_falseForVideoMime() =
        runTest {
            val img = IMetaTagBuilder("https://x/y").add("m", "image/png").build()
            val vid = IMetaTagBuilder("https://x/y").add("m", "video/mp4").build()

            assertTrue(img.isImage())
            assertTrue(!vid.isImage())
        }

    @Test
    fun isImage_fallsBackToUrlExtensionWhenNoMime() =
        runTest {
            val withExt = IMetaTagBuilder("https://x/photo.jpg").build()
            val extensionless = IMetaTagBuilder("https://blossom.example/deadbeef").build()

            assertTrue(withExt.isImage())
            assertTrue(!extensionless.isImage())
        }
}

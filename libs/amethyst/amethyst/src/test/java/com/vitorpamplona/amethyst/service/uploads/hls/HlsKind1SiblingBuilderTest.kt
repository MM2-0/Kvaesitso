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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsKind1SiblingBuilderTest {
    private val masterUrl = "https://cdn.test/master.m3u8"
    private val masterSha = "ffeeddccbbaa00112233445566778899aabbccddeeff00112233445566778899"
    private val posterUrl = "https://cdn.test/poster.jpg"
    private val blurhash = "LFE.@D9F01_2~q%2tRj["
    private val thumbhash = "wJlGAA"
    private val masterDim = DimensionTag(2160, 3840)

    private fun build(
        title: String = "My HD video",
        description: String = "A clip",
        masterSha256: String? = masterSha,
        masterDimension: DimensionTag? = masterDim,
        posterUrlArg: String? = posterUrl,
        blurhashArg: String? = blurhash,
        thumbhashArg: String? = thumbhash,
        createdAt: Long? = 1_700_000_000L,
    ) = HlsKind1SiblingBuilder.build(
        title = title,
        description = description,
        masterUrl = masterUrl,
        masterSha256 = masterSha256,
        masterDimension = masterDimension,
        posterUrl = posterUrlArg,
        blurhash = blurhashArg,
        thumbhash = thumbhashArg,
        createdAt = createdAt,
    )

    private fun Array<Array<String>>.findTag(name: String): Array<String>? = firstOrNull { it.isNotEmpty() && it[0] == name }

    @Test
    fun kindIs1() {
        val template = build()
        assertEquals(TextNoteEvent.KIND, template.kind)
    }

    @Test
    fun contentJoinsTitleDescriptionMasterUrlOnDoubleNewline() {
        val template = build(title = "T", description = "D")
        assertEquals("T\n\nD\n\n$masterUrl", template.content)
    }

    @Test
    fun blankFieldsAreOmittedFromContent() {
        val template = build(title = "", description = "  ")
        assertEquals(masterUrl, template.content)
    }

    @Test
    fun fieldsAreTrimmed() {
        val template = build(title = "  T  ", description = "\nD\n")
        assertEquals("T\n\nD\n\n$masterUrl", template.content)
    }

    @Test
    fun masterImetaCarriesAllVisualFields() {
        val template = build()
        val imeta = template.tags.findTag("imeta")
        assertNotNull(imeta)
        val flat = imeta!!.joinToString("|")
        assertTrue("url: $flat", flat.contains("url $masterUrl"))
        assertTrue("mime: $flat", flat.contains("m application/vnd.apple.mpegurl"))
        assertTrue("hash: $flat", flat.contains("x $masterSha"))
        assertTrue("dim: $flat", flat.contains("dim 2160x3840"))
        assertTrue("image: $flat", flat.contains("image $posterUrl"))
        assertTrue("blurhash: $flat", flat.contains("blurhash $blurhash"))
        assertTrue("thumbhash: $flat", flat.contains("thumbhash $thumbhash"))
    }

    @Test
    fun nullVisualFieldsAreOmittedFromImeta() {
        val template =
            build(
                masterSha256 = null,
                masterDimension = null,
                posterUrlArg = null,
                blurhashArg = null,
                thumbhashArg = null,
            )
        val imeta = template.tags.findTag("imeta")!!
        val flat = imeta.joinToString("|")
        assertTrue("url is required: $flat", flat.contains("url $masterUrl"))
        assertTrue("mime is always present: $flat", flat.contains("m application/vnd.apple.mpegurl"))
        assertFalse("no hash: $flat", flat.contains("x "))
        assertFalse("no dim: $flat", flat.contains("dim "))
        assertFalse("no image: $flat", flat.contains("image "))
        assertFalse("no blurhash: $flat", flat.contains("blurhash "))
        assertFalse("no thumbhash: $flat", flat.contains("thumbhash "))
    }

    @Test
    fun rTagCarriesMasterUrl() {
        val template = build()
        val ref = template.tags.findTag("r")
        assertNotNull(ref)
        assertEquals(masterUrl, ref!![1])
    }

    @Test
    fun noATagIsEmitted() {
        val template = build()
        assertNull(template.tags.findTag("a"))
    }

    @Test
    fun createdAtUsesProvidedValue() {
        val template = build(createdAt = 12_345L)
        assertEquals(12_345L, template.createdAt)
    }

    @Test
    fun createdAtDefaultsToNowWhenNull() {
        val before = System.currentTimeMillis() / 1_000
        val template = build(createdAt = null)
        val after = System.currentTimeMillis() / 1_000
        assertTrue(template.createdAt in before..after)
    }
}

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

import com.davotoula.lightcompressor.Resolution
import com.davotoula.lightcompressor.hls.HlsRenditionSummary
import com.davotoula.lightcompressor.hls.HlsUploaded
import com.davotoula.lightcompressor.hls.Rendition
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsVideoEventBuilderTest {
    private val landscapeRenditions =
        listOf(
            summary(Resolution.SD_360, width = 640, height = 360),
            summary(Resolution.HD_720, width = 1280, height = 720),
        )

    private val portraitRenditions =
        listOf(
            summary(Resolution.SD_360, width = 360, height = 640),
        )

    private fun summary(
        resolution: Resolution,
        width: Int,
        height: Int,
    ): HlsRenditionSummary =
        HlsRenditionSummary(
            rendition = Rendition(resolution, bitrateKbps = 500),
            mediaPlaylist = "", // not needed by the builder
            playlistFilename = "${resolution.label}/media.m3u8",
            width = width,
            height = height,
            codecString = "avc1.64001f",
            combinedFilename = "${resolution.label}.mp4",
        )

    private fun uploadsFor(renditions: List<HlsRenditionSummary>): Map<String, HlsUploaded<MediaUploadResult>> {
        val uploads = linkedMapOf<String, HlsUploaded<MediaUploadResult>>()
        renditions.forEach { r ->
            val label = r.rendition.resolution.label
            uploads["$label.mp4"] =
                HlsUploaded(
                    url = "https://cdn.test/$label.mp4",
                    metadata =
                        MediaUploadResult(
                            url = "https://cdn.test/$label.mp4",
                            sha256 = "$label-sha",
                            size = 1_000_000L,
                        ),
                )
            uploads["$label/media.m3u8"] =
                HlsUploaded(
                    url = "https://cdn.test/$label-media.m3u8",
                    metadata =
                        MediaUploadResult(
                            url = "https://cdn.test/$label-media.m3u8",
                            sha256 = "$label-playlist-sha",
                        ),
                )
        }
        return uploads
    }

    private fun input(
        renditions: List<HlsRenditionSummary>,
        title: String = "My HD Video",
        description: String = "A cool video",
        alt: String? = null,
        duration: Int? = null,
        contentWarning: String? = null,
        dTag: String? = "fixed-d-tag",
        posterUrl: String? = null,
    ): HlsVideoPublishInput =
        HlsVideoPublishInput(
            renditions = renditions,
            uploads = uploadsFor(renditions),
            masterUrl = "https://cdn.test/master.m3u8",
            masterSha256 = "master-sha",
            title = title,
            description = description,
            alt = alt,
            durationSeconds = duration,
            contentWarning = contentWarning,
            dTag = dTag,
            createdAt = 1_700_000_000L,
            posterUrl = posterUrl,
        )

    private fun Array<Array<String>>.findTag(name: String): Array<String>? = firstOrNull { it.isNotEmpty() && it[0] == name }

    private fun Array<Array<String>>.findAllTags(name: String): List<Array<String>> = filter { it.isNotEmpty() && it[0] == name }

    @Test
    fun landscapeRenditionsBuildHorizontalTemplateKind34235() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))

        assertTrue("expected Horizontal template", result.template is HlsVideoEventTemplate.Horizontal)
        val template = (result.template as HlsVideoEventTemplate.Horizontal).template
        assertEquals(AddressableNormalVideoEvent.KIND, template.kind)
        assertEquals("A cool video", template.content)
    }

    @Test
    fun portraitRenditionsBuildVerticalTemplateKind34236() {
        val result = HlsVideoEventBuilder.build(input(portraitRenditions))

        assertTrue("expected Vertical template", result.template is HlsVideoEventTemplate.Vertical)
        val template = (result.template as HlsVideoEventTemplate.Vertical).template
        assertEquals(AddressableShortVideoEvent.KIND, template.kind)
    }

    @Test
    fun horizontalTemplateHasTitleAndDTag() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val title = tags.findTag("title")
        assertNotNull(title)
        assertEquals("My HD Video", title!![1])

        val d = tags.findTag("d")
        assertNotNull(d)
        assertEquals("fixed-d-tag", d!![1])
    }

    @Test
    fun templateContainsOneImetaForMasterAndOnePerRendition() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val imetas = tags.findAllTags("imeta")
        // 1 master + 2 renditions
        assertEquals(3, imetas.size)

        // First imeta is the master
        val masterImeta = imetas[0].joinToString("|")
        assertTrue(masterImeta.contains("url https://cdn.test/master.m3u8"))
        assertTrue(masterImeta.contains("m application/vnd.apple.mpegurl"))

        // Subsequent imetas are per-rendition playlist URLs
        val r360Imeta = imetas[1].joinToString("|")
        assertTrue("360p imeta: $r360Imeta", r360Imeta.contains("url https://cdn.test/360p-media.m3u8"))
        assertTrue(r360Imeta.contains("m application/vnd.apple.mpegurl"))
        assertTrue("360p dim: $r360Imeta", r360Imeta.contains("dim 640x360"))
        assertTrue(r360Imeta.contains("x 360p-sha"))

        val r720Imeta = imetas[2].joinToString("|")
        assertTrue("720p imeta: $r720Imeta", r720Imeta.contains("url https://cdn.test/720p-media.m3u8"))
        assertTrue(r720Imeta.contains("dim 1280x720"))
    }

    @Test
    fun durationTagWhenDurationProvided() {
        val result =
            HlsVideoEventBuilder.build(
                input(landscapeRenditions, duration = 123),
            )
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val duration = tags.findTag("duration")
        assertNotNull(duration)
        assertEquals("123", duration!![1])
    }

    @Test
    fun noDurationTagWhenNotProvided() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags
        assertNull(tags.findTag("duration"))
    }

    @Test
    fun contentWarningTagWhenProvided() {
        val result =
            HlsVideoEventBuilder.build(
                input(landscapeRenditions, contentWarning = "NSFW"),
            )
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val warning = tags.findTag("content-warning")
        assertNotNull(warning)
        assertEquals("NSFW", warning!![1])
    }

    @Test
    fun noContentWarningTagWhenNull() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags
        assertNull(tags.findTag("content-warning"))
    }

    @Test
    fun horizontalTemplateOmitsDeprecatedAltTag() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags
        assertNull(tags.findTag("alt"))
    }

    @Test
    fun verticalTemplateOmitsDeprecatedAltTag() {
        val result = HlsVideoEventBuilder.build(input(portraitRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Vertical).template.tags
        assertNull(tags.findTag("alt"))
    }

    @Test
    fun posterUrlIsPropagatedToEveryImetaImage() {
        val result =
            HlsVideoEventBuilder.build(
                input(landscapeRenditions, posterUrl = "https://cdn.test/poster.jpg"),
            )
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val imetas = tags.findAllTags("imeta")
        // 1 master + 2 renditions, each must carry image <posterUrl>
        assertEquals(3, imetas.size)
        imetas.forEach { imeta ->
            val flat = imeta.joinToString("|")
            assertTrue("imeta missing poster image: $flat", flat.contains("image https://cdn.test/poster.jpg"))
        }
    }

    @Test
    fun noPosterUrlMeansNoImagePropertyOnImetas() {
        val result = HlsVideoEventBuilder.build(input(landscapeRenditions))
        val tags = (result.template as HlsVideoEventTemplate.Horizontal).template.tags

        val imetas = tags.findAllTags("imeta")
        imetas.forEach { imeta ->
            val flat = imeta.joinToString("|")
            assertTrue("imeta unexpectedly carries image: $flat", !flat.contains("image "))
        }
    }
}

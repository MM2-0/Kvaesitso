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
package com.vitorpamplona.amethyst.commons.model.nip71Video

import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kind 34236 events carry a whole HLS ladder in their imeta tags. Rendering tag[0] blindly plays
 * whichever rung the publisher happened to list first — the master (adaptive) for Amethyst's own
 * uploads, a single low rung for clients that order the tags differently.
 */
class VideoTrackSelectionTest {
    private val hls = "application/vnd.apple.mpegurl"

    private fun event(vararg metas: VideoMeta) =
        AddressableShortVideoEvent(
            id = "bfe2f2244fefc7cebc7b2eae825495f99dabb4649ee3f90ab1fa33bcd1e9bb9f",
            pubKey = "3b6187c08b9dd5617150ea047e788a0fdd44b4394cb5566cba76f683ddc027d2",
            createdAt = 1780894816,
            tags = arrayOf(arrayOf("d", "abc")) + metas.map { it.toIMetaArray() },
            content = "",
            sig = "",
        )

    private fun rendition(
        label: String,
        width: Int,
        height: Int,
        mimeType: String? = hls,
    ) = VideoMeta(
        url = "https://host/$label.m3u8",
        mimeType = mimeType,
        dimension = DimensionTag(width, height),
    )

    @Test
    fun anHlsPropertyOnTheImetaBecomesTheSelectedTrack() {
        // divine.video's shape: one imeta for the progressive file, with the adaptive manifest
        // hung off the non-standard `hls` key instead of a second imeta.
        val progressive =
            VideoMeta(
                url = "https://media.divine.video/abc",
                mimeType = "video/mp4",
                dimension = DimensionTag(1080, 1920),
                hash = "abc",
                size = 6579464,
                hls = "https://media.divine.video/abc/hls/master.m3u8",
            )
        val selected = event(progressive).selectVideoTrack()

        assertEquals("https://media.divine.video/abc/hls/master.m3u8", selected?.url)
        assertEquals(hls, selected?.mimeType)
        // Shape metadata is the same footage, so it carries over...
        assertEquals("1080x1920", selected?.dimension.toString())
        // ...but `x` and `size` describe the mp4 blob, and a manifest is not that blob.
        assertNull(selected?.hash)
        assertNull(selected?.size)
    }

    @Test
    fun aRedundantHlsPropertyDoesNotDuplicateTheTrack() {
        val selfReferential =
            VideoMeta(
                url = "https://host/master.m3u8",
                mimeType = hls,
                hls = "https://host/master.m3u8",
            )

        assertEquals("https://host/master.m3u8", event(selfReferential).selectVideoTrack()?.url)
    }

    @Test
    fun picksTheMasterWhenItIsListedFirst() {
        // Amethyst's own layout (HlsVideoEventBuilder): master first, dim = the top rung.
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls, dimension = DimensionTag(1080, 1920))
        val selected = event(master, rendition("360", 360, 640), rendition("1080", 1080, 1920)).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun picksTheMasterWhenItIsListedLast() {
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls, dimension = DimensionTag(1080, 1920))
        val selected = event(rendition("360", 360, 640), rendition("720", 720, 1280), master).selectVideoTrack()

        // The 1080 master ties with nothing here, so max-dim alone finds it.
        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun aDimlessRenditionNeverBeatsADimensionedMaster() {
        // Regression (PR #4028 review): a missing `dim` is not a master signal on its own. The
        // sloppy-publisher shape is a master that declares its top resolution alongside a rendition
        // that forgot one — preferring the dim-less entry there pins playback to a single low rung,
        // which is the bug this selector exists to fix.
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls, dimension = DimensionTag(1080, 1920))
        val dimlessRung = VideoMeta(url = "https://host/360.m3u8", mimeType = hls)
        val selected = event(master, dimlessRung).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun fallsBackToTagOrderWhenNoHlsEntryDeclaresADimension() {
        // With nothing to compare there is no ladder to reason about, so the master-first
        // convention decides.
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls)
        val rung = VideoMeta(url = "https://host/360.m3u8", mimeType = hls)
        val selected = event(master, rung).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun fillsPresentationMetadataFromSiblingsWhenTheChosenEntryIsBare() {
        // The master declares the ladder top but carries none of the presentation metadata; the
        // rungs do.
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls, dimension = DimensionTag(1080, 1920))
        val rung =
            VideoMeta(
                url = "https://host/720.m3u8",
                mimeType = hls,
                dimension = DimensionTag(720, 1280),
                blurhash = "LEHV6nWB2yk8",
                thumbhash = "1QcSHQRnh493",
                image = listOf("https://host/poster.jpg"),
                alt = "a koi pond",
            )
        val selected = event(master, rung).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
        assertEquals(1080, selected?.dimension?.width)
        assertEquals(1920, selected?.dimension?.height)
        assertEquals("LEHV6nWB2yk8", selected?.blurhash)
        assertEquals("1QcSHQRnh493", selected?.thumbhash)
        assertEquals(listOf("https://host/poster.jpg"), selected?.image)
        assertEquals("a koi pond", selected?.alt)
    }

    @Test
    fun takesThePosterFromASeparateImageImeta() {
        // Publishers routinely ship the still as its own image/* entry rather than as `image` on
        // the video entries. canBeTheVideo() excludes it from selection, but it is still where the
        // poster lives — the notification big-picture path depends on finding it.
        val poster = VideoMeta(url = "https://host/poster.jpg", mimeType = "image/jpeg")
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls, dimension = DimensionTag(1080, 1920))
        val selected = event(poster, master).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
        assertEquals(listOf("https://host/poster.jpg"), selected?.image)
    }

    @Test
    fun neverOverridesMetadataTheSelectedTrackDeclares() {
        val master =
            VideoMeta(
                url = "https://host/master.m3u8",
                mimeType = hls,
                dimension = DimensionTag(1080, 1920),
                blurhash = "own",
                alt = "own alt",
            )
        val rung = rendition("720", 720, 1280).copy(blurhash = "sibling", alt = "sibling alt")
        val selected = event(master, rung).selectVideoTrack()

        assertEquals("own", selected?.blurhash)
        assertEquals("own alt", selected?.alt)
    }

    @Test
    fun picksTheTopRungWhenNoMasterIsPublished() {
        val selected =
            event(
                rendition("360", 360, 640),
                rendition("720", 720, 1280),
                rendition("1080", 1080, 1920),
            ).selectVideoTrack()

        assertEquals("https://host/1080.m3u8", selected?.url)
    }

    @Test
    fun prefersHlsOverAProgressiveFile() {
        val mp4 = VideoMeta(url = "https://host/video.mp4", mimeType = "video/mp4", dimension = DimensionTag(1080, 1920))
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls)
        val selected = event(mp4, master).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun identifiesAnHlsPlaylistByExtensionWhenNoMimeIsDeclared() {
        val master = VideoMeta(url = "https://host/master.m3u8")
        val mp4 = VideoMeta(url = "https://host/video.mp4", mimeType = "video/mp4")
        val selected = event(mp4, master).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun doesNotMistakeAQueryStringForAPlaylist() {
        val mp4 = VideoMeta(url = "https://host/video.mp4?ref=a.m3u8", mimeType = "video/mp4")
        val other = VideoMeta(url = "https://host/other.mp4", mimeType = "video/mp4")
        val selected = event(mp4, other).selectVideoTrack()

        // Neither is HLS, so the first candidate stands — the old behaviour, unchanged.
        assertEquals("https://host/video.mp4?ref=a.m3u8", selected?.url)
    }

    @Test
    fun treatsTheLegacyAudioMpegurlAliasAsAPlaylistNotAnAudioTrack() {
        // Audit finding: `audio/x-mpegurl` and `audio/mpegurl` are two of the four HLS playlist
        // MIMEs this repo recognises — legacy aliases for the manifest format, not a claim that the
        // content is audio. Reading them as an audio track dropped the master out of the ladder.
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = "audio/mpegurl", dimension = DimensionTag(1080, 1920))
        val rung = rendition("360", 360, 640)

        assertEquals("https://host/master.m3u8", event(master, rung).selectVideoTrack()?.url)
    }

    @Test
    fun stillRendersWhenEveryPlaylistUsesTheAudioAlias() {
        // Same finding, worse case: with every entry labelled that way the candidate list emptied
        // and selection fell back to imetas.first() — the poster JPEG, handed to the video player.
        val poster = VideoMeta(url = "https://host/poster.jpg", mimeType = "image/jpeg")
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = "audio/x-mpegurl", dimension = DimensionTag(1080, 1920))
        val rung = VideoMeta(url = "https://host/360.m3u8", mimeType = "audio/x-mpegurl", dimension = DimensionTag(360, 640))

        assertEquals("https://host/master.m3u8", event(poster, master, rung).selectVideoTrack()?.url)
    }

    @Test
    fun identifiesAPlaylistServedAsOctetStream() {
        // A server default is not a claim about the file, so the .m3u8 path still decides. Without
        // this the master lost to a correctly labelled low rung.
        val master =
            VideoMeta(
                url = "https://host/master.m3u8",
                mimeType = "application/octet-stream",
                dimension = DimensionTag(1080, 1920),
            )
        val rung = rendition("360", 360, 640)

        assertEquals("https://host/master.m3u8", event(master, rung).selectVideoTrack()?.url)
    }

    @Test
    fun neverTakesTheAspectRatioFromThePoster() {
        // A 16:9 thumbnail alongside a vertical short would otherwise size the player's box at 16:9,
        // since JustVideoDisplay lays out from the selected entry's dim.
        val poster = VideoMeta(url = "https://host/poster.jpg", mimeType = "image/jpeg", dimension = DimensionTag(1600, 900))
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls)
        val rung = rendition("1080", 1080, 1920)
        val selected = event(poster, master, rung).selectVideoTrack()

        assertEquals(1080, selected?.dimension?.width)
        assertEquals(1920, selected?.dimension?.height)
        // The poster itself is still picked up as the still image.
        assertEquals(listOf("https://host/poster.jpg"), selected?.image)
    }

    @Test
    fun skipsTheSeparateAudioTrack() {
        // NIP-71 PR #2255 splits audio into its own imeta so resolution can change without
        // interrupting sound. It is never the thing to hand the video player.
        val audio = VideoMeta(url = "https://host/audio.m3u8", mimeType = "audio/mp4")
        val master = VideoMeta(url = "https://host/master.m3u8", mimeType = hls)
        val selected = event(audio, master).selectVideoTrack()

        assertEquals("https://host/master.m3u8", selected?.url)
    }

    @Test
    fun skipsAPosterImage() {
        val poster = VideoMeta(url = "https://host/poster.jpg", mimeType = "image/jpeg")
        val mp4 = VideoMeta(url = "https://host/video.mp4", mimeType = "video/mp4")
        val selected = event(poster, mp4).selectVideoTrack()

        assertEquals("https://host/video.mp4", selected?.url)
    }

    @Test
    fun fallsBackToTheFirstImetaWhenNothingLooksLikeAVideo() {
        // A NIP-71 event asserts its own type; the renderers already handle an image-shaped one,
        // so an all-image event must keep rendering rather than disappear.
        val poster = VideoMeta(url = "https://host/poster.jpg", mimeType = "image/jpeg")
        val other = VideoMeta(url = "https://host/other.jpg", mimeType = "image/jpeg")

        assertEquals("https://host/poster.jpg", event(poster, other).selectVideoTrack()?.url)
    }

    @Test
    fun returnsNullWithoutImetas() {
        assertNull(event().selectVideoTrack())
    }

    @Test
    fun aSingleImetaIsReturnedUntouched() {
        val only = VideoMeta(url = "https://host/only.m3u8", mimeType = hls)
        assertEquals("https://host/only.m3u8", event(only).selectVideoTrack()?.url)
    }
}

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
package com.vitorpamplona.amethyst.commons.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [UrlInfoItem.playableMediaUrl] — the gate between "the page mentioned an `og:audio`/`og:video`"
 * and "we are willing to point a media player at it".
 */
class UrlInfoItemMediaTest {
    private fun item(
        url: String = "https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3",
        image: String = "",
        audio: String = "",
        audioType: String = "",
        video: String = "",
        videoType: String = "",
        type: String = "music.song",
    ) = UrlInfoItem(
        url = url,
        mimeType = "text/html",
        image = image,
        audio = audio,
        audioType = audioType,
        video = video,
        videoType = videoType,
        type = type,
    )

    @Test
    fun aDeclaredAudioMimeMakesTheFilePlayable() {
        val info =
            item(
                audio = "https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3",
                audioType = "audio/mpeg",
            )

        assertEquals("https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3", info.playableMediaUrl)
        assertEquals("audio/mpeg", info.playableMediaType)
    }

    @Test
    fun aDeclaredVideoMimeMakesTheFilePlayable() {
        // nostr.build's video player page, the exact mirror of its audio one:
        // https://e.nostr.build/v_<id>_mp4 carries og:video = https://v.nostr.build/<id>.mp4
        val info =
            item(
                url = "https://e.nostr.build/v_ETvKzX2OdOGmFEp1avRlm5_mp4",
                video = "https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4",
                videoType = "video/mp4",
            )

        assertEquals("https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4", info.playableMediaUrl)
        assertEquals("video/mp4", info.playableMediaType)
    }

    @Test
    fun youTubesEmbedDeclarationIsRefused() {
        // Verified against the live page: YouTube declares og:video:url as its /embed/ page with
        // og:video:type = text/html. It is markup, not a video; handing it to ExoPlayer is the
        // same failure this whole change exists to stop. It must fall through to the link card.
        val info =
            item(
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                image = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                video = "https://www.youtube.com/embed/dQw4w9WgXcQ",
                videoType = "text/html",
            )

        assertNull(info.playableMediaUrl)
    }

    @Test
    fun anHlsPlaylistDeclarationIsPlayable() {
        // nostr.build serves HLS too, and its playlist MIME is not in the `video/` family.
        val info =
            item(
                video = "https://v.nostr.build/x/index.m3u8",
                videoType = "application/x-mpegurl",
            )

        assertEquals("https://v.nostr.build/x/index.m3u8", info.playableMediaUrl)
    }

    @Test
    fun aMediaExtensionCarriesAPageThatOmittedTheType() {
        assertEquals(
            "https://a.nostr.build/x.mp3",
            item(audio = "https://a.nostr.build/x.mp3").playableMediaUrl,
        )
        assertEquals(
            "https://v.nostr.build/x.mp4",
            item(video = "https://v.nostr.build/x.mp4").playableMediaUrl,
        )
        assertNull(item(video = "https://v.nostr.build/x.mp4").playableMediaType)
    }

    @Test
    fun aNonMediaDeclarationIsRefused() {
        // A page is free to put anything under og:audio/og:video. Pointing the player at an HTML
        // page is the exact failure this whole change is about, so an untyped extensionless URL
        // and a contradicting MIME both stay null and fall through to the link card.
        assertNull(item(audio = "https://example.com/player.html").playableMediaUrl)
        assertNull(item(video = "https://example.com/watch/12345").playableMediaUrl)
        assertNull(item(audio = "https://example.com/x.mp3", audioType = "text/html").playableMediaUrl)
        assertNull(item(video = "https://example.com/x.mp4", videoType = "image/png").playableMediaUrl)
    }

    @Test
    fun videoWinsAPageThatDeclaresBoth() {
        // The audio path renders a pictureless track, so the video is the richer of the two.
        val info =
            item(
                audio = "https://a.nostr.build/x.mp3",
                audioType = "audio/mpeg",
                video = "https://v.nostr.build/x.mp4",
                videoType = "video/mp4",
            )

        assertEquals("https://v.nostr.build/x.mp4", info.playableMediaUrl)
        assertEquals("video/mp4", info.playableMediaType)
    }

    @Test
    fun aRefusedVideoStillFallsBackToAPlayableAudio() {
        val info =
            item(
                audio = "https://a.nostr.build/x.mp3",
                audioType = "audio/mpeg",
                video = "https://example.com/embed/x",
                videoType = "text/html",
            )

        assertEquals("https://a.nostr.build/x.mp3", info.playableMediaUrl)
        assertEquals("audio/mpeg", info.playableMediaType)
    }

    @Test
    fun aPageWithoutMediaHasNothingToPlay() {
        assertNull(item().playableMediaUrl)
        assertNull(item().playableMediaType)
    }

    @Test
    fun aRelativeMediaPathResolvesAgainstThePage() {
        assertEquals(
            "https://e.nostr.build/media/x.mp3",
            item(url = "https://e.nostr.build/a_x_mp3", audio = "/media/x.mp3", audioType = "audio/mpeg").playableMediaUrl,
        )
        assertEquals(
            "https://e.nostr.build/media/x.mp4",
            item(url = "https://e.nostr.build/v_x_mp4", video = "/media/x.mp4", videoType = "video/mp4").playableMediaUrl,
        )
    }

    @Test
    fun aTrackPageWithNoCoverArtStillCountsAsAFetchedPreview() {
        // fetchComplete() gates whether UrlCachedPreviewer keeps the result or files it as Empty
        // and falls back to a bare link. Playable media is evidence on its own.
        val noArt = item(audio = "https://a.nostr.build/x.mp3", audioType = "audio/mpeg")

        assertEquals("", noArt.image)
        assertTrue(noArt.fetchComplete())

        assertFalse(item().fetchComplete())
    }

    @Test
    fun onlyAPlayerPageTakesOverTheCard() {
        // A news story that embeds a clip declares `article` and keeps its card -- headline,
        // description, host, tap-through. Replacing all of that with a bare player is a strictly
        // worse rendering of an article.
        assertNull(
            item(video = "https://example.com/clip.mp4", videoType = "video/mp4", type = "article").playableMediaUrl,
        )
        // A page that declares no type at all is a document until it says otherwise.
        assertNull(
            item(video = "https://example.com/clip.mp4", videoType = "video/mp4", type = "").playableMediaUrl,
        )
        // Every music.* and video.* subtype a media host uses is a player page.
        assertEquals(
            "https://example.com/clip.mp4",
            item(video = "https://example.com/clip.mp4", videoType = "video/mp4", type = "video.other").playableMediaUrl,
        )
        assertEquals(
            "https://example.com/x.mp3",
            item(audio = "https://example.com/x.mp3", audioType = "audio/mpeg", type = "music.album").playableMediaUrl,
        )
    }

    @Test
    fun aDocumentRelativeMediaPathResolvesToo() {
        // `startsWith("/")` was the old test, so this spelling used to reach the player verbatim
        // as "media/x.mp3" -- a relative string the player can only hang on.
        assertEquals(
            "https://e.nostr.build/dir/media/x.mp3",
            item(url = "https://e.nostr.build/dir/page", audio = "media/x.mp3", audioType = "audio/mpeg").playableMediaUrl,
        )
    }

    @Test
    fun onlyHttpSchemesArePlayable() {
        // The page is remote and untrusted; the scheme is what stops it aiming the local player
        // at a local read.
        assertNull(item(audio = "file:///sdcard/secret.mp3", audioType = "audio/mpeg").playableMediaUrl)
        assertNull(item(audio = "content://media/external/audio/1", audioType = "audio/mpeg").playableMediaUrl)
        assertNull(item(audio = "javascript:alert(1)", audioType = "audio/mpeg").playableMediaUrl)
    }

    @Test
    fun playableMediaTypeFollowsTheUrlItDescribes() {
        // A refused page reports no type either, so a caller cannot hand a MIME to a null URL.
        val refused = item(video = "https://example.com/clip.mp4", videoType = "video/mp4", type = "article")

        assertNull(refused.playableMediaUrl)
        assertNull(refused.playableMediaType)
    }

    @Test
    fun imageResolutionIsUnchanged() {
        assertEquals(
            "https://e.nostr.build/poster.jpg",
            item(url = "https://e.nostr.build/a_x_mp3", image = "/poster.jpg").imageUrlFullPath,
        )
    }
}

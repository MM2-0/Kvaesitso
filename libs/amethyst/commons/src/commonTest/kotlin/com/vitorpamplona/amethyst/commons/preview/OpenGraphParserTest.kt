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

/**
 * The three attributes a preview can be declared under -- `property` (Open Graph), `name`
 * (Twitter cards and plain HTML) and `itemprop` (schema.org) -- and what happens when a page
 * declares the same thing under more than one.
 */
class OpenGraphParserTest {
    private fun extract(html: String) = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(html))

    @Test
    fun readsOpenGraphProperties() {
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:title" content="T">
                |  <meta property="og:description" content="D">
                |  <meta property="og:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun fallsBackToTwitterCardNames() {
        val info =
            extract(
                """
                |<head>
                |  <meta name="twitter:title" content="T">
                |  <meta name="twitter:description" content="D">
                |  <meta name="twitter:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun fallsBackToPlainNames() {
        val info =
            extract(
                """
                |<head>
                |  <meta name="title" content="T">
                |  <meta name="description" content="D">
                |  <meta name="image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun readsSchemaOrgItemprops() {
        val info =
            extract(
                """
                |<head>
                |  <meta itemprop="name" content="ignored, not a title key">
                |  <meta itemprop="title" content="T">
                |  <meta itemprop="description" content="D">
                |  <meta itemprop="image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun takesEachFieldFromWhicheverTagCarriesItFirst() {
        // NOTE: this is document order, not source priority. A page that puts a plain
        // <meta name="description"> above its <meta property="og:description"> -- a very common
        // CMS layout -- has the plain one win, even though og: is the more specific declaration.
        // Pinned as the current behavior; changing it means preferring og: over name: explicitly.
        val info =
            extract(
                """
                |<head>
                |  <meta name="description" content="plain, comes first">
                |  <meta property="og:description" content="og, comes second">
                |  <meta property="og:title" content="T">
                |  <meta property="og:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("plain, comes first", info.description)
    }

    @Test
    fun missingFieldsComeBackEmptyRatherThanNull() {
        val info = extract("""<head><meta property="og:title" content="T"></head>""")

        assertEquals("T", info.title)
        assertEquals("", info.description)
        assertEquals("", info.image)
    }

    @Test
    fun ignoresAMetaTagWithNoRecognizedKey() {
        val info = extract("""<head><meta name="viewport" content="width=device-width"></head>""")

        assertEquals("", info.title)
        assertEquals("", info.description)
        assertEquals("", info.image)
    }

    @Test
    fun readsTheAudioFileAPlayerPageDeclares() {
        // Trimmed from the real document at
        // https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3?t=Aria&by=The+Fishcake -- an HTML
        // player page (Content-Type: text/html) whose `og:audio` is the actual mp3. Note that
        // `og:audio` comes AFTER title, description and image: an early exit that stops once
        // those three are filled never reaches it.
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:type" content="music.song"/>
                |  <meta property="og:title" content="Aria by The Fishcake"/>
                |  <meta property="og:description" content="nostr.build media player"/>
                |  <meta property="og:image" content="https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3/poster.jpg"/>
                |  <meta property="og:audio" content="https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3"/>
                |  <meta property="og:audio:secure_url" content="https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3"/>
                |  <meta property="og:audio:type" content="audio/mpeg"/>
                |</head>
                """.trimMargin(),
            )

        assertEquals("Aria by The Fishcake", info.title)
        assertEquals("https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3", info.audio)
        assertEquals("audio/mpeg", info.audioType)
        assertEquals("music.song", info.type)
    }

    @Test
    fun prefersOgAudioOverTheSecureUrlThatFollowsIt() {
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:audio" content="https://example.com/first.mp3"/>
                |  <meta property="og:audio:secure_url" content="https://example.com/second.mp3"/>
                |</head>
                """.trimMargin(),
            )

        assertEquals("https://example.com/first.mp3", info.audio)
    }

    @Test
    fun fallsBackToTheSecureUrlWhenItIsTheOnlyOne() {
        val info = extract("""<head><meta property="og:audio:secure_url" content="https://example.com/a.mp3"></head>""")

        assertEquals("https://example.com/a.mp3", info.audio)
    }

    @Test
    fun aPageWithoutAudioReportsEmptyAudioFields() {
        val info = extract("""<head><meta property="og:title" content="T"></head>""")

        assertEquals("", info.audio)
        assertEquals("", info.audioType)
    }

    @Test
    fun readsTheVideoFileAPlayerPageDeclares() {
        // The mirror of the audio page above, from the same host:
        // https://e.nostr.build/v_<id>_mp4 -- `og:type` flips to video.other and the media pair
        // becomes og:video/og:video:type.
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:type" content="video.other"/>
                |  <meta property="og:title" content="Clip by Someone"/>
                |  <meta property="og:image" content="https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4/poster.jpg"/>
                |  <meta property="og:video" content="https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4"/>
                |  <meta property="og:video:secure_url" content="https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4"/>
                |  <meta property="og:video:type" content="video/mp4"/>
                |  <meta property="og:video:width" content="1280"/>
                |  <meta property="og:video:height" content="720"/>
                |</head>
                """.trimMargin(),
            )

        assertEquals("https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4", info.video)
        assertEquals("video/mp4", info.videoType)
        assertEquals("video.other", info.type)
        assertEquals("", info.audio)
    }

    @Test
    fun readsWhatThePageSaysItIs() {
        // og:type is what separates a media host's player page from an article that embeds a clip;
        // only the former should have its card replaced by a player.
        assertEquals("music.song", extract("""<head><meta property="og:type" content="music.song"></head>""").type)
        assertEquals("article", extract("""<head><meta property="og:type" content="article"></head>""").type)
        assertEquals("", extract("""<head><meta property="og:title" content="T"></head>""").type)
    }

    @Test
    fun readsTheUrlAliasForHostsThatOnlyEmitIt() {
        // YouTube writes og:video:url and never the bare og:video. Parsing it is not trusting it
        // -- see UrlInfoItemMediaTest, where this exact declaration is refused on its text/html
        // type -- but a host that spells it this way with a real file must still be readable.
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:video:url" content="https://www.youtube.com/embed/dQw4w9WgXcQ">
                |  <meta property="og:video:type" content="text/html">
                |</head>
                """.trimMargin(),
            )

        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", info.video)
        assertEquals("text/html", info.videoType)
    }

    @Test
    fun mediaTagsAreReadEvenWhenTheyTrailACompleteCard() {
        // The regression the removed early exit caused: title, description and image are all
        // filled before og:audio/og:video appear, so stopping there skipped the media entirely.
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:title" content="T">
                |  <meta property="og:description" content="D">
                |  <meta property="og:image" content="https://example.com/i.png">
                |  <meta property="og:audio" content="https://example.com/a.mp3">
                |  <meta property="og:audio:type" content="audio/mpeg">
                |  <meta property="og:video" content="https://example.com/v.mp4">
                |  <meta property="og:video:type" content="video/mp4">
                |</head>
                """.trimMargin(),
            )

        assertEquals("https://example.com/a.mp3", info.audio)
        assertEquals("https://example.com/v.mp4", info.video)
    }

    @Test
    fun readsSiteNameAndAuthor() {
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:title" content="Rewilding Software Engineering">
                |  <meta property="og:site_name" content="Medium">
                |  <meta property="article:author" content="https://medium.com/@tudor">
                |  <meta name="author" content="Tudor Girba">
                |</head>
                """.trimMargin(),
            )

        assertEquals("Medium", info.siteName)
        // The profile URL in article:author is skipped, so the name-valued key fills it.
        assertEquals("Tudor Girba", info.author)
    }

    @Test
    fun dropsBotCheckOpenGraphTitles() {
        val info = extract("""<head><meta property="og:title" content="Attention Required! | Cloudflare"></head>""")

        assertEquals("", info.title)
    }

    @Test
    fun dropsBotCheckTitles() {
        val info = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse("<head><title>Just a moment...</title></head>", includeTitleAndIcons = true))

        assertEquals("", info.title)
    }

    @Test
    fun keepsRealTitlesThatStartLikeAnErrorPage() {
        val info = extract("""<head><meta property="og:title" content="Access denied: how DRM failed"></head>""")

        assertEquals("Access denied: how DRM failed", info.title)
    }

    @Test
    fun keepsTitlesThatMerelyMentionWaiting() {
        val info = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse("<head><title>Why we wait: a short essay</title></head>", includeTitleAndIcons = true))

        assertEquals("Why we wait: a short essay", info.title)
    }
}

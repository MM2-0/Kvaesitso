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

class OpenGraphParser {
    class Result(
        /** The OpenGraph/Twitter/meta title, or the document's `<title>` when it declares none. */
        val title: String,
        val description: String,
        val image: String,
        /** The `og:audio` URL the page declares for itself, verbatim (may be relative). */
        val audio: String = "",
        /** The `og:audio:type` MIME the page declares for [audio], e.g. `audio/mpeg`. */
        val audioType: String = "",
        /** The `og:video` URL the page declares for itself, verbatim (may be relative). */
        val video: String = "",
        /** The `og:video:type` MIME the page declares for [video], e.g. `video/mp4`. */
        val videoType: String = "",
        /** `og:type` — what the page says it *is*, e.g. `music.song`, `video.other`, `article`. */
        val type: String = "",
        /** The best icon `<link>` the page declares, verbatim (may be relative). Empty when none. */
        val icon: String = "",
        /** `og:site_name` — the publication the page belongs to, e.g. `Medium`. Empty when none. */
        val siteName: String = "",
        /** The page author's name (`author`, `article:author`...), never a profile URL. Empty when none. */
        val author: String = "",
    )

    companion object {
        val ATTRIBUTE_VALUE_PROPERTY = "property"
        val ATTRIBUTE_VALUE_NAME = "name"
        val ATTRIBUTE_VALUE_ITEMPROP = "itemprop"

        // for <meta itemprop=... to get title
        private val META_X_TITLE =
            arrayOf(
                "og:title",
                "twitter:title",
                "title",
            )

        // for <meta itemprop=... to get description
        private val META_X_DESCRIPTION =
            arrayOf(
                "og:description",
                "twitter:description",
                "description",
            )

        // for <meta itemprop=... to get image
        private val META_X_IMAGE =
            arrayOf(
                "og:image",
                "twitter:image",
                "image",
            )

        // The media file a host page is *about*. Media hosts publish a human-facing HTML player
        // page and point at the real file from here. nostr.build does exactly this, in both
        // families: `e.nostr.build/a_<id>_mp3` carries `og:audio` = `a.nostr.build/<id>.mp3`, and
        // `e.nostr.build/v_<id>_mp4` carries `og:video` = `v.nostr.build/<id>.mp4`. Without these
        // such a page can only be rendered as a card that links out.
        //
        // `:url` and `:secure_url` are the spec's aliases for the bare key and are read because
        // hosts disagree on which to emit -- nostr.build writes the bare `og:video`, YouTube only
        // `og:video:url`. Reading a key is not the same as trusting it: what a page declares here
        // is filtered by the `:type` below before anything is handed to a player.
        //
        // Deliberately `og:` only. `twitter:player:stream` is not included because it is usually
        // an embed, and a player pointed at the wrong thing is worse than a link.
        private val META_X_AUDIO =
            arrayOf(
                "og:audio",
                "og:audio:url",
                "og:audio:secure_url",
            )

        private val META_X_AUDIO_TYPE =
            arrayOf(
                "og:audio:type",
            )

        private val META_X_VIDEO =
            arrayOf(
                "og:video",
                "og:video:url",
                "og:video:secure_url",
            )

        private val META_X_VIDEO_TYPE =
            arrayOf(
                "og:video:type",
            )

        // What the page says it is. A media host's player page declares `music.*` or `video.*`; an
        // article that merely embeds a clip declares `article` and keeps its link card. Without
        // this an `og:video` on a news story would replace the whole card with a bare player.
        private val META_X_TYPE =
            arrayOf(
                "og:type",
            )

        // The publication a page belongs to: a byline for sources that carry no nostr author.
        private val META_X_SITE_NAME =
            arrayOf(
                "og:site_name",
                "application-name",
            )

        // Who wrote the page. `article:author` is often a profile URL rather than a name; those
        // are skipped where the value is read, so a later name-valued key can still fill it.
        private val META_X_AUTHOR =
            arrayOf(
                "author",
                "article:author",
                "citation_author",
            )

        /**
         * Titles of the pages a bot-protection layer or an error serves in place of the real one.
         * A preview built from one would caption a highlight "Just a moment...", which is worse
         * than no preview, so such a title is dropped and the page counts as having none.
         */
        private val INTERSTITIAL_TITLES =
            setOf(
                "just a moment",
                "attention required!",
                "please wait",
                "checking your browser",
                "checking your browser before accessing",
                "security check",
                "access denied",
                "are you a robot?",
                "ddos-guard",
                "403 forbidden",
                "404 not found",
                "429 too many requests",
                "too many requests",
            )

        /**
         * Whether [title] is a bot-check or error page's title rather than the page's own.
         *
         * Matched whole -- after dropping a trailing ellipsis and a " | Cloudflare" -- never as a
         * prefix: an essay titled "Access denied: how DRM failed" is a real title.
         */
        fun isInterstitialTitle(title: String): Boolean {
            val normalized =
                title
                    .trim()
                    .lowercase()
                    .removeSuffix(" | cloudflare")
                    .trimEnd('.', '\u2026', ' ')
            return normalized in INTERSTITIAL_TITLES
        }

        private val CONTENT = "content"
        private val HREF = "href"
        private val REL = "rel"
        private val SIZES = "sizes"
        private val TYPE = "type"

        // How an icon with no `sizes` is ranked. An `apple-touch-icon` is 180px by Apple's
        // convention; a plain `icon` without sizes is usually the 16/32px tab favicon. A scalable
        // one (SVG, or `sizes="any"`) beats every bitmap: it is sharp at any card size.
        private const val APPLE_TOUCH_ICON_DEFAULT_SIZE = 180
        private const val ICON_DEFAULT_SIZE = 32
        private const val SCALABLE_ICON_SIZE = Int.MAX_VALUE
    }

    /** Which field of [Result] a meta tag's key fills, or null when the key is not one we read. */
    private enum class Field {
        TITLE,
        DESCRIPTION,
        IMAGE,
        AUDIO,
        AUDIO_TYPE,
        VIDEO,
        VIDEO_TYPE,
        TYPE,
        SITE_NAME,
        AUTHOR,
    }

    private fun fieldFor(key: String): Field? =
        when (key) {
            in META_X_TITLE -> Field.TITLE
            in META_X_DESCRIPTION -> Field.DESCRIPTION
            in META_X_IMAGE -> Field.IMAGE
            in META_X_AUDIO -> Field.AUDIO
            in META_X_AUDIO_TYPE -> Field.AUDIO_TYPE
            in META_X_VIDEO -> Field.VIDEO
            in META_X_VIDEO_TYPE -> Field.VIDEO_TYPE
            in META_X_TYPE -> Field.TYPE
            in META_X_SITE_NAME -> Field.SITE_NAME
            in META_X_AUTHOR -> Field.AUTHOR
            else -> null
        }

    /**
     * Reads every `<meta>` the scanner yields, keeping the first value seen for each field.
     *
     * There is deliberately no early exit once the fields are filled. The one that used to be here
     * stopped at title+description+image, which is a set every page completes before it reaches
     * `og:audio`/`og:video` -- so on nostr.build's player pages it skipped the single field that
     * makes the page playable. The scan is bounded anyway: [MetaTagsParser] stops at `</head>`.
     */
    fun extractUrlInfo(metaTags: Sequence<MetaTag>): Result {
        var title = ""
        var description = ""
        var image = ""
        var audio = ""
        var audioType = ""
        var video = ""
        var videoType = ""
        var type = ""
        var documentTitle = ""
        var icon = ""
        var iconSize = -1
        var siteName = ""
        var author = ""

        metaTags.forEach {
            when (it.element) {
                HeadElement.META -> {}

                HeadElement.TITLE -> {
                    if (documentTitle.isEmpty()) documentTitle = it.attr(CONTENT)
                    return@forEach
                }

                HeadElement.LINK -> {
                    val href = it.attr(HREF)
                    if (href.isNotBlank()) {
                        val size = iconSize(it)
                        // Strictly greater, so among equals the first declared wins.
                        if (size > iconSize) {
                            icon = href
                            iconSize = size
                        }
                    }
                    return@forEach
                }
            }

            // A meta tag names its key in exactly one of these three attributes, but which one
            // varies by site, so each is tried in turn until one is a key we read.
            val field =
                fieldFor(it.attr(ATTRIBUTE_VALUE_PROPERTY))
                    ?: fieldFor(it.attr(ATTRIBUTE_VALUE_NAME))
                    ?: fieldFor(it.attr(ATTRIBUTE_VALUE_ITEMPROP))

            // First value wins, so `og:audio` is preferred over the `og:audio:secure_url` that
            // follows it, and `og:title` over `twitter:title`.
            when (field) {
                Field.TITLE -> if (title.isEmpty()) title = it.attr(CONTENT)
                Field.DESCRIPTION -> if (description.isEmpty()) description = it.attr(CONTENT)
                Field.IMAGE -> if (image.isEmpty()) image = it.attr(CONTENT)
                Field.AUDIO -> if (audio.isEmpty()) audio = it.attr(CONTENT)
                Field.AUDIO_TYPE -> if (audioType.isEmpty()) audioType = it.attr(CONTENT)
                Field.VIDEO -> if (video.isEmpty()) video = it.attr(CONTENT)
                Field.VIDEO_TYPE -> if (videoType.isEmpty()) videoType = it.attr(CONTENT)
                Field.TYPE -> if (type.isEmpty()) type = it.attr(CONTENT)
                Field.SITE_NAME -> if (siteName.isEmpty()) siteName = it.attr(CONTENT).trim()
                Field.AUTHOR ->
                    if (author.isEmpty()) {
                        author =
                            it
                                .attr(CONTENT)
                                .trim()
                                .takeUnless(::isUrl)
                                .orEmpty()
                    }
                null -> Unit
            }
        }

        val bestTitle = title.ifEmpty { documentTitle }.takeUnless(::isInterstitialTitle).orEmpty()

        return Result(bestTitle, description, image, audio, audioType, video, videoType, type, icon, siteName, author)
    }

    private fun isUrl(value: String): Boolean = value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    /** The pixel size an icon `<link>` is ranked by: its largest declared size, or a default. */
    private fun iconSize(link: MetaTag): Int {
        val sizes = link.attr(SIZES)
        if (sizes.contains("any", ignoreCase = true)) return SCALABLE_ICON_SIZE
        if (link.attr(TYPE).equals("image/svg+xml", ignoreCase = true)) return SCALABLE_ICON_SIZE
        if (link
                .attr(HREF)
                .substringBefore('?')
                .substringBefore('#')
                .endsWith(".svg", ignoreCase = true)
        ) {
            return SCALABLE_ICON_SIZE
        }

        // `sizes="16x16 32x32"`: rank by the largest, which is the one a decoder will pick.
        val declared =
            sizes
                .split(' ')
                .mapNotNull { it.lowercase().substringBefore('x', "").toIntOrNull() }
                .maxOrNull()
        if (declared != null) return declared

        val isAppleTouch = link.attr(REL).contains("apple-touch-icon", ignoreCase = true)
        return if (isAppleTouch) APPLE_TOUCH_ICON_DEFAULT_SIZE else ICON_DEFAULT_SIZE
    }
}

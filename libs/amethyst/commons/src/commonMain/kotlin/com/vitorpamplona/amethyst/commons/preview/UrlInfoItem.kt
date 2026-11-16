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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.util.absoluteUrlHost
import com.vitorpamplona.amethyst.commons.util.resolveHttpUrl

@Immutable
class UrlInfoItem(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val mimeType: String,
    /** `og:audio` — the audio file an HTML player page is about. Empty when not declared. */
    val audio: String = "",
    /** `og:audio:type` — the MIME the page declares for [audio]. Empty when not declared. */
    val audioType: String = "",
    /** `og:video` — the video file an HTML player page is about. Empty when not declared. */
    val video: String = "",
    /** `og:video:type` — the MIME the page declares for [video]. Empty when not declared. */
    val videoType: String = "",
    /** `og:type` — what the page says it is, e.g. `music.song`, `video.other`, `article`. */
    val type: String = "",
    /** The page's best declared icon `<link>` (favicon / apple-touch-icon). Empty when none. */
    val icon: String = "",
    /** `og:site_name` — the publication, e.g. `Medium`. Empty when not declared. */
    val siteName: String = "",
    /** The page author's name, never a profile URL. Empty when not declared. */
    val author: String = "",
) {
    /** The page's host, or null when [url] is not an absolute URL. */
    val verifiedHost = absoluteUrlHost(url)

    /**
     * Resolves an OpenGraph URL against the page's own address, or null when it cannot be made
     * into an absolute `http(s)` one.
     *
     * Two things are deliberate. It resolves *any* relative reference, not just root-relative
     * ones: the earlier `startsWith("/")` test passed a document-relative `media/x.mp3` through
     * untouched, and a relative string reaching a media player is a load that can only hang.
     * And it requires http(s), so a page cannot point the local player at `file:///` -- the page
     * is remote and untrusted, the scheme is the only thing standing between it and a local read.
     */
    private fun resolve(path: String): String? {
        if (path.isBlank()) return null
        return resolveHttpUrl(url.takeIf { verifiedHost != null }, path)
    }

    // Falls back to the raw value so an unresolvable image degrades to "the loader shows nothing",
    // which is what it did before. Only the playable gate below treats unresolvable as refusal.
    val imageUrlFullPath = resolve(image) ?: image

    /**
     * The page's icon, absolute: the one it declared, else `/favicon.ico` at its origin -- the
     * same guess every browser makes, since most sites that declare nothing still serve one.
     *
     * A `data:` icon is used as-is when it is an image (the image loader decodes those inline),
     * and taken at its word when it is not: `href="data:,"` is how a site tells browsers it has no
     * favicon, so guessing `/favicon.ico` there is a request that is known to miss.
     *
     * Null for a non-HTML URL (an image or a video is its own preview) or an unresolvable one.
     */
    val iconUrlFullPath: String? =
        when {
            !mimeType.startsWith("text/html", ignoreCase = true) -> null
            icon.startsWith("data:image/", ignoreCase = true) -> if (icon.length <= MAX_INLINE_ICON_LENGTH) icon else resolve(DEFAULT_FAVICON)
            icon.startsWith("data:", ignoreCase = true) -> null
            else -> resolve(icon) ?: resolve(DEFAULT_FAVICON)
        }

    /**
     * The declared `og:video`, absolute, but only when the declaration holds up: a [videoType] the
     * renderer can actually play (a `video/` MIME, an `audio/` one, or an HLS playlist type), or
     * — when the page omitted the type — a genuine media file extension.
     *
     * The type check is the entire safeguard, and it is not theoretical. YouTube declares
     * `og:video:url` = `youtube.com/embed/<id>` with `og:video:type` = `text/html`: an embed page,
     * not a video. Playing that is the same mistake, in the same shape, as the one that made
     * nostr.build's player page unplayable — an HTML document handed to ExoPlayer. `text/html`
     * classifies as nothing, so it is refused here and the page falls through to its link card.
     */
    private val playableVideoUrl: String? =
        resolve(video)
            ?.takeIf { RichTextParser.classifyMedia(it, videoType.ifEmpty { null }) == MediaContentKind.VIDEO }

    /**
     * The declared `og:audio`, absolute, under the same rule: an [audioType] in the `audio/`
     * family, or a genuine audio file extension when the page omitted the type.
     */
    private val playableAudioUrl: String? =
        resolve(audio)
            ?.takeIf { RichTextParser.isAudioContent(audioType.ifEmpty { null }, it) }

    /**
     * Whether the page presents itself as a player *for* this media rather than a document that
     * merely embeds some.
     *
     * Media hosts declare `music.song` / `video.other`; a news story with a clip in it declares
     * `article`. Without this distinction an `og:video` on an article would replace its whole
     * card -- headline, description, host, tap-through -- with a bare player, which is a strictly
     * worse rendering of an article. A page that declares no type at all is treated as a document
     * and keeps its card.
     */
    private val isMediaPlayerPage =
        type.startsWith("music.", ignoreCase = true) || type.startsWith("video.", ignoreCase = true)

    /**
     * The media file this page declares itself to be a player for, or null when it declared none
     * we are willing to play.
     *
     * `og:video` wins a page that declares both, because it is the richer render: the audio path
     * deliberately strips the picture.
     */
    val playableMediaUrl: String? = if (isMediaPlayerPage) playableVideoUrl ?: playableAudioUrl else null

    /** The MIME the page declared for [playableMediaUrl], or null when it named none. */
    val playableMediaType: String? =
        when {
            playableMediaUrl == null -> null
            playableVideoUrl != null -> videoType.ifEmpty { null }
            else -> audioType.ifEmpty { null }
        }

    /**
     * Whether the page has text to show without an image: a title (OpenGraph's or the document's
     * own `<title>`) or a description. Such a page gets the compact icon + text card rather than
     * a bare link.
     */
    val hasTextPreview: Boolean = title.isNotBlank() || description.isNotBlank()

    /**
     * Whether the fetch produced something worth rendering. An image is the usual evidence, but a
     * page that declared playable media counts too — a track page that ships no cover art would
     * otherwise be thrown away as Empty and fall back to a bare link, which is exactly the player
     * we went to the trouble of finding. So does a page with no OpenGraph at all that still has a
     * title or description: it renders as the compact card instead of a bare link.
     */
    fun fetchComplete(): Boolean = url.isNotEmpty() && (image.isNotEmpty() || playableMediaUrl != null || hasTextPreview)

    fun allFetchComplete(): Boolean = title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()

    companion object {
        private const val DEFAULT_FAVICON = "/favicon.ico"

        // Previews are cached, so an inline icon is held for as long as its card is. Real inline
        // favicons are a few KB; anything far past that is not worth keeping in memory.
        private const val MAX_INLINE_ICON_LENGTH = 64 * 1024
    }
}

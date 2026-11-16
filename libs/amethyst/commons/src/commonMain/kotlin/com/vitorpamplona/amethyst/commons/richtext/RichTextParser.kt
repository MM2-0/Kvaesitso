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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.cartridges.CartLinks
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipLinks
import com.vitorpamplona.amethyst.commons.util.isValidUrl
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.experimental.inlineMetadata.Nip54InlineMetadata
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbhashTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.DurationTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.WaveformTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlin.coroutines.cancellation.CancellationException

class RichTextParser {
    fun createMediaContent(
        fullUrl: String,
        eventTags: Map<String, IMetaTag>,
        description: String?,
        callbackUri: String? = null,
        authorPubKey: String? = null,
        cartImetas: Map<String, IMetaTag>? = null,
    ): MediaUrlContent? {
        val frags = Nip54InlineMetadata().parse(fullUrl)

        val tags = eventTags.get(fullUrl)?.properties ?: emptyMap()

        val contentType = frags[MimeTypeTag.TAG_NAME] ?: tags[MimeTypeTag.TAG_NAME]?.firstOrNull()

        // Returning null here drops the URL to a plain link, discarding the imeta's `dim`/blurhash
        // and forcing a URL-preview round-trip to rediscover a type the imeta already declared —
        // which is why classifyMedia falls back to the extension before giving up.
        val kind = classifyMedia(fullUrl, contentType)
        val declaredSize = (frags[SizeTag.TAG_NAME] ?: tags[SizeTag.TAG_NAME]?.firstOrNull())?.toLongOrNull()

        return if (kind == MediaContentKind.IMAGE) {
            MediaUrlImage(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                contentWarning = frags[ContentWarningTag.TAG_NAME] ?: tags[ContentWarningTag.TAG_NAME]?.firstOrNull(),
                uri = callbackUri,
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
                cartImetas = cartImetas,
                sizeBytes = declaredSize,
            )
        } else if (kind == MediaContentKind.VIDEO) {
            MediaUrlVideo(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                contentWarning = frags[ContentWarningTag.TAG_NAME] ?: tags[ContentWarningTag.TAG_NAME]?.firstOrNull(),
                uri = callbackUri,
                // Poster URL from the imeta's `image` property — downstream gallery-add reads
                // this as the entry's `image` tag so the gallery thumbnail can render the
                // poster JPEG instead of falling back to the blurhash placeholder.
                artworkUri = frags[ImageTag.TAG_NAME] ?: tags[ImageTag.TAG_NAME]?.firstOrNull(),
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
                sizeBytes = declaredSize,
                waveform = (frags[WaveformTag.TAG_NAME] ?: tags[WaveformTag.TAG_NAME]?.firstOrNull())?.let { WaveformTag.parseWave(it) },
                durationSeconds = (frags[DurationTag.TAG_NAME] ?: tags[DurationTag.TAG_NAME]?.firstOrNull())?.toDoubleOrNull()?.takeIf { it > 0 },
            )
        } else if (kind == MediaContentKind.PDF) {
            MediaUrlPdf(
                url = fullUrl,
                description = description ?: frags[AltTag.TAG_NAME] ?: tags[AltTag.TAG_NAME]?.firstOrNull(),
                hash = frags[HashSha256Tag.TAG_NAME] ?: tags[HashSha256Tag.TAG_NAME]?.firstOrNull(),
                blurhash = frags[BlurhashTag.TAG_NAME] ?: tags[BlurhashTag.TAG_NAME]?.firstOrNull(),
                dim = frags[DimensionTag.TAG_NAME]?.let { DimensionTag.parse(it) } ?: tags[DimensionTag.TAG_NAME]?.firstOrNull()?.let { DimensionTag.parse(it) },
                uri = callbackUri,
                mimeType = contentType,
                thumbhash = frags[ThumbhashTag.TAG_NAME] ?: tags[ThumbhashTag.TAG_NAME]?.firstOrNull(),
                authorPubKey = authorPubKey,
                sizeBytes = declaredSize,
            )
        } else {
            null
        }
    }

    fun fixMissingSpaces(
        input: String,
        urlList: Set<String>,
    ): String {
        if (urlList.isEmpty()) return input

        // Walk the text, and wherever one of the detected URLs sits glued to a
        // non-space/non-newline neighbour, insert a single separating space so the
        // word-by-word segmenter downstream can recognise it as a standalone URL.
        //
        // This used to be a `Regex("([^ \n])?($escapedWords)([^ \n])?")` replace,
        // but Kotlin/Native's regex engine mishandles the optional capture groups
        // `([^ \n])?` (it fails to backtrack them to zero width), corrupting every
        // URL on iOS — e.g. "https://x" came back as "h https://x". A direct scan
        // sidesteps the engine entirely and is platform-independent.
        //
        // This runs on the main thread per rendered note, so it stays linear in the
        // text length: URLs are bucketed by their first character, and the inner
        // match attempt only fires at positions whose character can actually start
        // a URL — every other character costs a single map lookup. Within a bucket
        // the URLs are kept longest-first so a URL that is a prefix of a longer one
        // never shadows it.
        val byFirstChar = HashMap<Char, MutableList<String>>()
        urlList
            .asSequence()
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .forEach { byFirstChar.getOrPut(it[0]) { ArrayList(1) }.add(it) }

        val result = StringBuilder(input.length)
        val length = input.length
        var i = 0
        while (i < length) {
            val candidates = byFirstChar[input[i]]
            var match: String? = null
            if (candidates != null) {
                for (url in candidates) {
                    if (input.startsWith(url, i)) {
                        match = url
                        break
                    }
                }
            }

            if (match != null) {
                // Separate from a glued prefix character.
                if (result.isNotEmpty()) {
                    val prev = result[result.length - 1]
                    if (prev != ' ' && prev != '\n') result.append(' ')
                }

                result.append(match)
                i += match.length

                // Separate from a glued suffix character.
                if (i < length) {
                    val next = input[i]
                    if (next != ' ' && next != '\n') result.append(' ')
                }
            } else {
                result.append(input[i])
                i++
            }
        }

        return result.toString()
    }

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String? = null,
    ): RichTextViewerState {
        val imetas = tags.lists.imetasByUrl()
        val cartImetas = CartLinks.tagsToKeep(imetas)
        val urlSet = UrlParser().parseValidUrls(content)

        val mediaContents =
            urlSet.withScheme.mapNotNull { fullUrl ->
                createMediaContent(fullUrl, imetas, content, callbackUri, authorPubKey, cartImetas)
            } +
                urlSet.withoutScheme.mapNotNull { fullUrl ->
                    createMediaContent(fullUrl, imetas, content, callbackUri, authorPubKey, cartImetas)
                }

        val mediaForPager = mediaContents.associateBy { it.url }

        val imageUrls = mediaForPager.filterValues { it is MediaUrlImage }.keys
        val videoUrls = mediaForPager.filterValues { it is MediaUrlVideo }.keys
        val pdfUrls = mediaForPager.filterValues { it is MediaUrlPdf }.keys
        val webxdcApps = webxdcApps(urlSet.withScheme, imetas, callbackUri)

        val emojiMap = CustomEmoji.createEmojiMap(tags.lists)

        val allUrls = urlSet.withScheme + urlSet.withoutScheme + urlSet.emails + urlSet.bech32s + urlSet.relayUrls + urlSet.blossomUris + urlSet.groupLinks

        val newContent = fixMissingSpaces(content, allUrls)

        val segments = findTextSegments(newContent, imageUrls, videoUrls, pdfUrls, webxdcApps, urlSet, emojiMap, tags)

        val mediaForPagerWithBase64 =
            mediaForPager +
                segments
                    .flatMap { paragraph ->
                        paragraph.words
                            .mapNotNull {
                                if (it is Base64Segment) {
                                    createMediaContent(it.segmentText, emptyMap(), content, callbackUri)
                                } else if (it is BlossomUriSegment) {
                                    createMediaContent(it.segmentText, imetas, content, callbackUri, cartImetas = cartImetas)
                                } else {
                                    null
                                }
                            }
                    }.associateBy { it.url }

        return RichTextViewerState(
            urlSet = urlSet,
            mediaForPager = mediaForPagerWithBase64.toImmutableMap(),
            mediaList = mediaForPagerWithBase64.values.toImmutableList(),
            customEmoji = emojiMap.toImmutableMap(),
            paragraphs = segments,
            tags = tags,
        )
    }

    /**
     * The webxdc apps among the post's links: typed `application/x-webxdc` by their `imeta`, or a
     * bare `.xdc` link. Only full URLs: a schemeless `name.xdc` is a word, not an app.
     */
    private fun webxdcApps(
        urls: Set<String>,
        imetas: Map<String, IMetaTag>,
        postUri: String?,
    ): Map<String, WebxdcSegment> {
        var found: HashMap<String, WebxdcSegment>? = null
        for (url in urls) {
            val properties = imetas[url]?.properties
            val app = WebxdcApp.from(url) { properties?.get(it)?.firstOrNull() } ?: continue
            (found ?: HashMap<String, WebxdcSegment>().also { found = it })[url] = WebxdcSegment(url, app, postUri)
        }
        return found ?: emptyMap()
    }

    private fun findTextSegments(
        content: String,
        images: Set<String>,
        videos: Set<String>,
        pdfs: Set<String>,
        webxdcs: Map<String, WebxdcSegment>,
        urls: Urls,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): ImmutableList<ParagraphState> {
        // Trailing spaces and newlines would otherwise produce empty trailing
        // paragraphs, each rendered as a blank line between the last visible
        // word and the end of the component.
        val trimmedContent = content.trimEnd()
        if (trimmedContent.isEmpty()) return persistentListOf()

        val lines = trimmedContent.split('\n')
        val paragraphSegments = ArrayList<ParagraphState>(lines.size)

        lines.forEach { paragraph ->
            val isRTL = isArabic(paragraph)

            // split() behaves like `line.split(' ')`, but keeps math spans
            // (`$...$`, `$$...$$`) whole instead of tearing them at internal spaces.
            val tokens = MathParser.split(paragraph.trimEnd())
            val segments = ArrayList<Segment>(tokens.size)

            tokens.forEach { token ->
                when (token) {
                    is MathParser.Token.Math -> segments.add(MathSegment(token.raw, token.latex, token.displayMode, token.leading, token.trailing))
                    is MathParser.Token.Word -> {
                        // An opening bracket or quote glued to the front of a NIP-19 entity becomes
                        // its own word, so wordIdentifier can still see the scheme. See
                        // [nip19OpeningPunctuationLength].
                        val prefixLength = nip19OpeningPunctuationLength(token.text)
                        if (prefixLength > 0) {
                            segments.add(RegularTextSegment(token.text.substring(0, prefixLength)))
                            segments.add(wordIdentifier(token.text.substring(prefixLength), images, videos, pdfs, webxdcs, urls, emojis, tags))
                        } else {
                            segments.add(wordIdentifier(token.text, images, videos, pdfs, webxdcs, urls, emojis, tags))
                        }
                    }
                }
            }

            paragraphSegments.add(ParagraphState(segments.toPersistentList(), isRTL))
        }

        val segmentsWithGalleries = GalleryParser().processParagraphs(paragraphSegments)

        return segmentsWithGalleries
            .map { paragraph ->
                if (paragraph.words.isEmpty() || paragraph.words.any { it !is RegularTextSegment }) {
                    paragraph
                } else {
                    ParagraphState(
                        persistentListOf<Segment>(RegularTextSegment(paragraph.words.joinToString(" ") { it.segmentText })),
                        paragraph.isRTL,
                    )
                }
            }.toImmutableList()
    }

    private fun isPhoneNumberChar(c: Char): Boolean =
        when (c) {
            in '0'..'9' -> true
            '-' -> true
            ' ' -> true
            '.' -> true
            else -> false
        }

    fun isPotentialPhoneNumber(word: String): Boolean {
        if (word.length !in 7..14) return false
        var isPotentialNumber = true

        for (c in word) {
            if (!isPhoneNumberChar(c)) {
                isPotentialNumber = false
                break
            }
        }
        return isPotentialNumber
    }

    fun isDate(word: String): Boolean = shortDatePattern.matches(word) || longDatePattern.matches(word)

    private fun isArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    private fun wordIdentifier(
        word: String,
        images: Set<String>,
        videos: Set<String>,
        pdfs: Set<String>,
        webxdcs: Map<String, WebxdcSegment>,
        urls: Urls,
        emojis: Map<String, String>,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        if (word.isEmpty()) return RegularTextSegment(word)

        if (word.startsWith("data:image/")) {
            if (Patterns.BASE64_IMAGE.matches(word)) return Base64Segment(word)
        }

        if (images.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                ImageSegment("https://$word")
            } else {
                ImageSegment(word)
            }
        }

        if (videos.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                VideoSegment("https://$word")
            } else {
                VideoSegment(word)
            }
        }

        if (pdfs.contains(word)) {
            return if (urls.withoutScheme.contains(word)) {
                PdfSegment("https://$word")
            } else {
                PdfSegment(word)
            }
        }

        if (urls.withoutScheme.contains(word)) {
            parseNowhereLink(word)?.let { return it }
            return SchemelessUrlSegment(word)
        }

        webxdcs[word]?.let { return it }

        if (urls.withScheme.contains(word)) {
            // Concord and Buzz invites are plain https URLs that would otherwise render as bare
            // links. The single `/invite/` substring gate keeps the base64/bech32 parse off the
            // hot path for ordinary URLs; the two shapes are disjoint (Concord `…/invite/<naddr>#…`
            // carries a fragment, Buzz `…/invite/<token>` does not), so only one branch decodes.
            if (word.contains("/invite/")) {
                if (word.contains('#') && ConcordActions.parseInviteLink(word) != null) {
                    return ConcordInviteLinkSegment(word)
                }
                if (BuzzInviteLink.parse(word) != null) {
                    return BuzzInviteLinkSegment(word)
                }
            }
            parseNowhereLink(word)?.let { return it }
            return LinkSegment(word)
        }

        if (urls.emails.contains(word)) return EmailSegment(word)

        if (urls.bech32s.contains(word)) return BechSegment(word)

        // Checked before relayUrls: a group link `wss://relay'groupId` embeds a relay URL,
        // so it must win over the bare relay-URL interpretation.
        if (urls.groupLinks.contains(word)) return RelayGroupLinkSegment(word)

        if (urls.relayUrls.contains(word)) return RelayUrlSegment(word)

        if (urls.blossomUris.contains(word)) return BlossomUriSegment(word)

        if (startsWithNIP19Scheme(word)) return BechSegment(word)

        if (CustomEmoji.fastMightContainEmoji(word, emojis) && emojis.any { word.contains(it.key) }) return EmojiSegment(word)

        if (word.startsWith("lnbc", true)) return InvoiceSegment(word)

        if (word.startsWith("lnurl", true)) return WithdrawSegment(word)

        if (word.startsWith("cashuA", true) || word.startsWith("cashuB", true)) return CashuSegment(word)

        if (word.startsWith("noffer1", true)) {
            (ClinkPointerParser.parse(word) as? NOffer)?.let { return ClinkOfferSegment(word, it) }
        }

        if (LocalTimestamp.mightContain(word)) {
            LocalTimestamp.split(word)?.let { return TimestampSegment(word, it) }
        }

        if (word.startsWith('#')) return parseHash(word, tags)

        if (EmojiCoder.isCoded(word)) return SecretEmoji(word)

        if (isPotentialPhoneNumber(word) && !isDate(word)) {
            if (Patterns.PHONE.matches(word)) return PhoneSegment(word)
        }

        NipLinks.mention(word)?.let { (number, extras) -> return NipMentionSegment(word, number, extras.ifEmpty { null }) }

        return RegularTextSegment(word)
    }

    private fun parseHash(
        word: String,
        tags: ImmutableListOfLists<String>,
    ): Segment {
        // First #[n]
        // [tagIndex] requires the literal "#[", so a word without it can never match.
        // Every plain "#hashtag" reaches this function, and the regex scan is far more
        // expensive than the substring check that rules it out — so gate on the literal.
        // Uses contains(), not startsWith(), because find() also matches "#[n]" mid-word.
        try {
            val matcher = if (word.contains("#[")) tagIndex.find(word) else null
            if (matcher != null) {
                val index = matcher.groups[1]?.value?.toInt()
                val suffix = matcher.groups[2]?.value

                if (index != null && index >= 0 && index < tags.lists.size) {
                    val tag = tags.lists[index]

                    if (tag.size > 1) {
                        if (tag[0] == "p") {
                            return HashIndexUserSegment(word, tag[1], suffix)
                        } else if (tag[0] == "e" || tag[0] == "a") {
                            return HashIndexEventSegment(word, tag[1], suffix)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("Tag Parser", "Couldn't link tag $word", e)
        }

        // Second #Amethyst
        try {
            val hashtagMatcher = hashTagsPattern.find(word)
            if (hashtagMatcher != null) {
                val hashtag = hashtagMatcher.groups[1]?.value
                if (hashtag != null) {
                    return HashTagSegment(word, hashtag, hashtagMatcher.groups[2]?.value?.ifEmpty { null })
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
        }

        return RegularTextSegment(word)
    }

    companion object {
        val longDatePattern: Regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val shortDatePattern: Regex = Regex("^\\d{2}-\\d{2}-\\d{2}$")

        val noProtocolUrlValidator =
            Regex(
                "(([a-zA-Z0-9_-]+@)?([a-zA-Z0-9_-]+\\.)*[a-zA-Z0-9_-]+[\\.\\:][a-zA-Z0-9_]+([\\/ \\?\\=\\&\\#\\.][a-zA-Z0-9_-]+)*\\/?)(.*)",
            )

        val imageExt = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg", "avif")

        // Audio is folded into [videoExt] because both play through the same video pipeline — but
        // audio has no picture, so anything that reasons about the shape of the media (aspect
        // ratios, player sizing) has to tell them apart. `m3u8` stays video-only: a playlist
        // carries either.
        //
        // Composing [videoExt] out of [audioExt] is what keeps "every audio extension is also a
        // video extension" true by construction rather than by convention — the two lists cannot
        // drift apart. It is also why [audioExt] is declared first; the compiler rejects the
        // reverse order outright ("Variable 'audioExt' must be initialized"), so this note is
        // intent, not a guard rail.
        //
        // `mpga` is what Blossom servers name an `audio/mpeg` upload (podcast episodes, trailers and
        // tracks), so a bare `.mpga` link plays as the MP3 it is.
        val audioExt = listOf("mp3", "mpga", "ogg", "wav", "flac", "aac", "opus", "m4a", "f4a")
        val videoExt = listOf("mp4", "avi", "wmv", "mpg", "amv", "webm", "mov", "m3u8") + audioExt
        val pdfExt = listOf("pdf")

        // The lower+UPPER doubled forms, kept for callers that need the literal spellings — the
        // `data:image/(png|PNG|...)` alternation in [Patterns.BASE64_IMAGE], for one. Do NOT reach
        // for these to match a URL: [hasExtensionIn] compares case-insensitively, so the doubled
        // list only doubles the work, and it still misses mixed case like `.Mp3`.
        val imageExtensions = imageExt + imageExt.map { it.uppercase() }
        val videoExtensions = videoExt + videoExt.map { it.uppercase() }
        val pdfExtensions = pdfExt + pdfExt.map { it.uppercase() }
        val audioExtensions = audioExt + audioExt.map { it.uppercase() }

        val tagIndex = Regex("\\#\\[([0-9]+)\\](.*)")
        val hashTagsPattern: Regex =
            Regex("#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)(.*)", RegexOption.IGNORE_CASE)

        val acceptedNIP19schemes =
            listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed") +
                listOf("npub1", "naddr1", "note1", "nprofile1", "nevent1", "nembed").map {
                    it.uppercase()
                }

        /** Index just past the URL's path — the first `?` or `#`, or the whole length. */
        private fun pathEndIndex(url: String): Int {
            val queryStart = url.indexOf('?')
            if (queryStart >= 0) return queryStart
            val fragmentStart = url.indexOf('#')
            return if (fragmentStart >= 0) fragmentStart else url.length
        }

        /**
         * Whether the last path segment of [url] ends in one of [extensions], as a real
         * dot-introduced file extension.
         *
         * **The dot is mandatory**, and that is the whole point of this function. The substring
         * test it replaced (`url.endsWith("mp3")` after the query was trimmed) matched any path
         * that merely ended in an extension's letters. nostr.build serves an HTML *player page* at
         * `https://e.nostr.build/a_<id>_mp3?t=...` — underscore, not dot — and that page was
         * classified as audio and handed to ExoPlayer, which can only fail on `text/html`; the
         * accidental match also stole the link preview that would otherwise have rendered the
         * page's OpenGraph card. Ordinary prose slugs (`.../my-thoughts-on-mp3`) were misrouted
         * the same way.
         *
         * Matching is case-insensitive, so a mixed-case `.Mp3` — which the doubled
         * lower/UPPER extension lists never covered — now resolves too. [extensions] may be
         * spelled with or without the leading dot (`"mp4"` and `".mp4"` both work), because both
         * spellings reach here: this module's lists are bare, while the video-feed filters'
         * `SupportedContent` has historically been handed either.
         *
         * Called per URL during feed render, so it allocates nothing: the path end and the
         * extension boundary are located by index and compared in place.
         */
        fun hasExtensionIn(
            url: String,
            extensions: Collection<String>,
        ): Boolean {
            val end = pathEndIndex(url)
            var i = end - 1
            while (i >= 0) {
                val c = url[i]
                // A `/` before any `.` means the last path segment has no extension. This is also
                // what keeps the dot in a host name (`https://e.nostr.build/a_x_mp3`) from being
                // read as the start of one.
                if (c == '/') return false
                if (c == '.') {
                    val start = i + 1
                    val length = end - start
                    return extensions.any {
                        val from = if (it.startsWith('.')) 1 else 0
                        it.length - from == length && url.regionMatches(start, it, from, length, ignoreCase = true)
                    }
                }
                i--
            }
            return false
        }

        fun isImageExtension(ext: String) = imageExt.any { it.equals(ext, ignoreCase = true) }

        fun isImageOrVideoExtension(ext: String) = isImageExtension(ext) || videoExt.any { it.equals(ext, ignoreCase = true) }

        fun isImageOrVideoUrl(url: String): Boolean = isImageUrl(url) || isVideoUrl(url)

        fun isImageUrl(url: String): Boolean = hasExtensionIn(url, imageExt)

        fun isVideoUrl(url: String): Boolean = hasExtensionIn(url, videoExt)

        fun isAudioUrl(url: String): Boolean = hasExtensionIn(url, audioExt)

        // A declared MIME type is authoritative when present; the URL extension is only a fallback
        // for the common bare-URL case.
        fun isAudioContent(
            mimeType: String?,
            url: String,
        ): Boolean = mimeType?.startsWith("audio/") ?: isAudioUrl(url)

        // Mirrors the canonical HLS-playlist MIME list also kept in MediaItemCache.toExoPlayerMimeType.
        // Called per URL during feed render — uses `equals(ignoreCase)` instead of `lowercase()` to
        // avoid a per-call String allocation on the common non-HLS path.
        fun isHlsMimeType(mimeType: String?): Boolean {
            if (mimeType == null) return false
            return mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
                mimeType.equals("application/x-mpegurl", ignoreCase = true) ||
                mimeType.equals("audio/x-mpegurl", ignoreCase = true) ||
                mimeType.equals("audio/mpegurl", ignoreCase = true)
        }

        fun isPdfUrl(url: String): Boolean = hasExtensionIn(url, pdfExt)

        /**
         * Resolves which renderer can display a declared blob — the single decision every media
         * renderer must make, from a NIP-94 `m` tag, a NIP-92 imeta, or a bare URL.
         *
         * A declared MIME type wins, after [normalizeMimeType] repairs the bare-subtype form some
         * clients emit; the URL extension is the fallback both for the no-MIME case and for a
         * declaration too mangled to repair. `data:` URIs carry their type in the prefix, so a miss
         * there is genuine and the base64 payload is never extension-probed.
         *
         * Returns **null** when nothing can render the file. Callers must not substitute a media
         * kind for that null: handing an arbitrary blob — a webxdc app, a zip, an APK — to the
         * video player yields a permanently-buffering ExoPlayer where a plain link belongs. The one
         * defensible default is on kinds whose *event* already asserts the type (a NIP-71 video
         * event is a video however odd its imeta), and those call sites say so explicitly.
         */
        fun classifyMedia(
            url: String,
            rawMimeType: String?,
        ): MediaContentKind? {
            val mimeType = normalizeMimeType(rawMimeType)
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) return MediaContentKind.IMAGE
                // HLS playlists are advertised with a non-`video/*` MIME; see [isHlsMimeType].
                if (mimeType.startsWith("video/") || mimeType.startsWith("audio/") || isHlsMimeType(mimeType)) return MediaContentKind.VIDEO
                if (mimeType.startsWith("application/pdf")) return MediaContentKind.PDF
            } else if (url.startsWith("data:")) {
                if (url.startsWith("data:image/")) return MediaContentKind.IMAGE
                if (url.startsWith("data:video/") || url.startsWith("data:audio/")) return MediaContentKind.VIDEO
                if (url.startsWith("data:application/pdf")) return MediaContentKind.PDF
            }

            if (url.startsWith("data:")) return null

            if (hasExtensionIn(url, imageExt)) return MediaContentKind.IMAGE
            if (hasExtensionIn(url, videoExt)) return MediaContentKind.VIDEO
            if (hasExtensionIn(url, pdfExt)) return MediaContentKind.PDF

            return null
        }

        fun isValidURL(url: String?): Boolean = isValidUrl(url)

        fun parseImageOrVideo(fullUrl: String): BaseMediaContent {
            val isImage = hasExtensionIn(fullUrl, imageExt)
            val isVideo = hasExtensionIn(fullUrl, videoExt)

            return if (isImage) {
                MediaUrlImage(fullUrl)
            } else if (isVideo) {
                MediaUrlVideo(fullUrl)
            } else {
                MediaUrlImage(fullUrl)
            }
        }

        fun startsWithNIP19Scheme(
            word: String,
            from: Int = 0,
        ): Boolean {
            if (from >= word.length) return false
            val first = word[from]
            return if (first == 'n' || first == 'N') {
                if (word.startsWith("nostr:n", from) || word.startsWith("NOSTR:N", from)) {
                    acceptedNIP19schemes.any { word.startsWith(it, from + 6) }
                } else {
                    acceptedNIP19schemes.any { word.startsWith(it, from) }
                }
            } else if (first == '@') {
                acceptedNIP19schemes.any { word.startsWith(it, from + 1) }
            } else {
                false
            }
        }

        /**
         * Brackets and quotes that may open a NIP-19 entity. Only characters that can never be part
         * of an entity and that a writer would plausibly wrap one in — no trailing punctuation
         * (that is the entity's `additionalChars`) and no markdown emphasis markers.
         *
         * A `when` over char literals, not a `CharArray` + `in`: every word of every rendered note
         * asks this, and almost all of them answer no, which is the case a linear `contains` scan
         * is slowest at — it compares against all twelve before rejecting. `when` compiles to a
         * single lookupswitch on the char.
         */
        private fun isNip19OpeningPunctuation(c: Char): Boolean =
            when (c) {
                '(', '[', '{', '<', '"', '\'', '\u00AB', '\u2039', '\u201C', '\u201E', '\u2018', '\u201A' -> true
                else -> false
            }

        /**
         * How many leading bracket/quote characters of [word] hide a NIP-19 entity behind them, or
         * 0 when the word neither starts with such punctuation nor holds an entity right after it.
         *
         * [wordIdentifier] can only classify a word by its first character, so `(@npub1...)` and
         * `"npub1..."` fell through to plain text: the leading `(`/`"` hides the scheme from
         * [startsWithNIP19Scheme]. Splitting the punctuation into its own word restores it.
         * Trailing punctuation needs no such help — it is captured as the entity's
         * `additionalChars` downstream.
         *
         * The `nostr:`-prefixed spelling already behaves this way for free: the URL detector finds
         * the URI inside the parentheses and [fixMissingSpaces] separates it. Bare
         * `npub1...`/`@npub1...` are not URIs, so nothing splits them; this does.
         */
        private fun nip19OpeningPunctuationLength(word: String): Int {
            if (word.isEmpty() || !isNip19OpeningPunctuation(word[0])) return 0

            var i = 1
            while (i < word.length && isNip19OpeningPunctuation(word[i])) i++

            return if (startsWithNIP19Scheme(word, i)) i else 0
        }

        fun isUrlWithoutScheme(url: String) = noProtocolUrlValidator.matches(url)

        // Nowhere links (https://github.com/5t34k/nowhere) encode an entire mini-site in the URL
        // fragment. Servers never see the fragment, so OpenGraph previews return nothing useful
        // (and hostednowhere.com 403s scrapers). Detect them by host + presence of a fragment
        // so the renderer can show a branded card instead of falling through to LoadUrlPreview.
        private val nowhereHosts = listOf("nowhr.xyz", "hostednowhere.com")

        fun parseNowhereLink(word: String): NowhereLinkSegment? {
            val afterScheme =
                when {
                    word.startsWith("https://", ignoreCase = true) -> word.substring(8)
                    word.startsWith("http://", ignoreCase = true) -> word.substring(7)
                    else -> word
                }
            val slash = afterScheme.indexOf('/')
            if (slash < 0) return null
            val host = afterScheme.substring(0, slash).lowercase()
            if (host !in nowhereHosts) return null
            val hash = afterScheme.indexOf('#', slash)
            if (hash < 0) return null
            val pathSegment = afterScheme.substring(slash + 1, hash).substringBefore('/').takeIf { it.isNotEmpty() }
            return NowhereLinkSegment(word, host, pathSegment)
        }
    }
}

val mimeTypeMap: Map<String, String> =
    mapOf(
        // Images
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
        "tiff" to "image/tiff",
        // Video
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        // Dead entry: "ogg" is re-keyed under Audio below and mapOf keeps the last, so every
        // lookup of it yields audio/ogg. Kept only to show the extension is genuinely ambiguous —
        // see [ambiguousMimeSubtypes]. Don't read this line as reachable.
        "ogg" to "video/ogg",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        // Audio
        "mp3" to "audio/mpeg",
        "mpga" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "f4a" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        // Documents
        "pdf" to "application/pdf",
    )

/**
 * Subtypes that name more than one top-level type: `mpeg`, `mp4`, `ogg`, `webm` and `3gpp` all exist
 * as both `audio/` and `video/`, so a bare token spelling one of them identifies no family on its
 * own. See [normalizeMimeType] for what that costs them.
 */
private val ambiguousMimeSubtypes = setOf("mpeg", "mp4", "ogg", "webm", "3gpp")

/**
 * The subtype half of every MIME in [mimeTypeMap], so a bare token can be looked up as what it
 * actually is. [mimeTypeMap] is keyed by *extension*, which only doubles as a subtype index where
 * the two spellings coincide — `quicktime`, `x-matroska` and `svg+xml` are subtypes no extension
 * spells. Consulted after [mimeTypeMap] so the extension spelling keeps priority where they
 * disagree (`mp4` stays `video/mp4` rather than the later `audio/mp4` entry).
 */
private val mimeSubtypeMap: Map<String, String> = mimeTypeMap.values.associateBy { it.substringAfter('/') }

/**
 * NIP-92's `m` property is meant to carry a full `type/subtype`, but several clients emit the
 * bare subtype instead — Primal iOS writes `m jpeg` rather than `m image/jpeg`. That value is
 * useless as a MIME type: it matches none of the `startsWith("image/")`-style checks, and once
 * it is stored on the media model it travels all the way into Android's `ACTION_SEND` as
 * `Intent.type = "jpeg"`. No `<data android:mimeType>` filter matches a type without a slash,
 * so the share sheet opens with zero targets and the image cannot be shared at all.
 *
 * Map a bare subtype back onto its canonical MIME. This is called from the two chokepoints every
 * `m` value passes through — [RichTextParser.classifyMedia] for the render decision and
 * [MediaUrlContent] for the value the share intent and the gallery entry's republished `m` tag
 * read — so consumers see a well-formed type without each having to remember to repair it.
 *
 * Anything already containing a `/` is passed through untouched, and an unrecognised bare token is
 * dropped to null rather than propagated — that leaves the caller's extension-based detection to
 * decide, which is strictly better than carrying garbage forward.
 *
 * The same refusal covers a token in [ambiguousMimeSubtypes] that would land in `audio/`. `audio/`
 * is the one destructive family: it is what [RichTextParser.isAudioContent] reads to drop the
 * picture, so guessing it for what may be a video loses content, while guessing `video/` for what
 * may be audio only costs some chrome. That asymmetry is why the guard is one-sided rather than a
 * blanket refusal — `mp4` and `webm` resolve to `video/` and keep their rescue, so an extensionless
 * Blossom URL declaring `m mp4` still renders, whereas `ogg` (whose only live [mimeTypeMap] entry
 * is `audio/ogg`, its `video/ogg` one being a dead duplicate key) and `mpeg` decline.
 */
fun normalizeMimeType(rawMimeType: String?): String? {
    if (rawMimeType == null) return null
    if (rawMimeType.contains('/')) return rawMimeType
    val token = rawMimeType.lowercase()
    val resolved = mimeTypeMap[token] ?: mimeSubtypeMap[token] ?: return null
    if (token in ambiguousMimeSubtypes && resolved.startsWith("audio/")) return null
    return resolved
}

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
package com.vitorpamplona.amethyst.commons.rendering

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.BuzzInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji

/**
 * The shared rich-text post-processor: every text-bearing kind runs its text through
 * here, so hashtag / mention / link / media detection is the exact parse the
 * Android and Desktop note viewers use (`RichTextParser`, through its shared
 * [CachedRichTextParser]) rather than a second one.
 */
object BodySpans {
    class Parsed(
        val spans: List<BodySpan>,
        val media: List<MediaRef>,
    )

    fun parse(
        text: String,
        tags: TagArray,
        authorPubKey: String? = null,
    ): Parsed {
        if (text.isEmpty()) return Parsed(emptyList(), emptyList())

        // The viewers' own parse cache: a note the UI already laid out is not parsed twice.
        val state = CachedRichTextParser.parseText(text, ImmutableListOfLists(tags), callbackUri = null, authorPubKey = authorPubKey)

        val spans = mutableListOf<BodySpan>()
        state.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) spans.appendText("\n")
            paragraph.words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) spans.appendText(" ")
                addSpans(word, state, spans)
            }
        }

        return Parsed(spans.mergeText(), media(state))
    }

    private fun addSpans(
        word: Segment,
        state: RichTextViewerState,
        out: MutableList<BodySpan>,
    ) {
        val text = word.segmentText
        when (word) {
            is EmojiSegment -> addEmojiSpans(text, state, out)
            else -> out.add(toSpan(word, text))
        }
    }

    private fun toSpan(
        word: Segment,
        text: String,
    ): BodySpan =
        when (word) {
            is ImageSegment -> BodySpan.Image(text, text)
            is VideoSegment -> BodySpan.Video(text, text)
            is LinkSegment, is PdfSegment, is RelayUrlSegment, is RelayGroupLinkSegment,
            is ConcordInviteLinkSegment, is BuzzInviteLinkSegment, is BlossomUriSegment, is NowhereLinkSegment,
            -> BodySpan.Link(text, text)
            is SchemelessUrlSegment -> BodySpan.Link(text, "https://$text")
            is EmailSegment -> BodySpan.Link(text, "mailto:$text")
            is HashTagSegment -> BodySpan.Hashtag(text, word.hashtag)
            is InvoiceSegment -> BodySpan.Payment(text, "lightning_invoice")
            is WithdrawSegment -> BodySpan.Payment(text, "lnurl_withdraw")
            is CashuSegment -> BodySpan.Payment(text, "cashu_token")
            is ClinkOfferSegment -> BodySpan.Payment(text, "clink_offer")
            is HashIndexUserSegment -> if (word.hex.isValid()) BodySpan.UserMention(text, word.hex) else BodySpan.Text(text)
            // `#[n]` can point at an `e` tag (an id) or an `a` tag (a coordinate).
            is HashIndexEventSegment ->
                when {
                    word.hex.isValid() -> BodySpan.EventMention(text, EventRef(eventId = word.hex))
                    Address.parse(word.hex) != null -> BodySpan.EventMention(text, EventRef(eventId = null, address = word.hex))
                    else -> BodySpan.Text(text)
                }
            is BechSegment -> bechSpan(text)
            else -> BodySpan.Text(text)
        }

    /** A word can hold several `:code:`s and punctuation; each code that has an `emoji` tag becomes its own span. */
    private fun addEmojiSpans(
        text: String,
        state: RichTextViewerState,
        out: MutableList<BodySpan>,
    ) {
        var last = 0
        CustomEmoji.customEmojiPattern.findAll(text).forEach { match ->
            val url = state.customEmoji[match.value] ?: return@forEach
            if (match.range.first > last) out.add(BodySpan.Text(text.substring(last, match.range.first)))
            out.add(BodySpan.CustomEmoji(match.value, url))
            last = match.range.last + 1
        }
        if (last < text.length) out.add(BodySpan.Text(text.substring(last)))
    }

    private fun bechSpan(text: String): BodySpan =
        when (val entity = Nip19Parser.uriToRoute(text)?.entity) {
            is NPub -> BodySpan.UserMention(text, entity.hex)
            is NProfile -> BodySpan.UserMention(text, entity.hex)
            is NNote -> BodySpan.EventMention(text, EventRef(eventId = entity.hex))
            is NEvent -> BodySpan.EventMention(text, EventRef(eventId = entity.hex, relay = entity.relay.firstOrNull()?.url, author = entity.author, kind = entity.kind))
            is NAddress -> BodySpan.EventMention(text, EventRef(eventId = null, address = entity.aTag(), relay = entity.relay.firstOrNull()?.url, author = entity.author, kind = entity.kind))
            else -> BodySpan.Text(text)
        }

    private fun media(state: RichTextViewerState): List<MediaRef> =
        state.mediaList.mapNotNull { media ->
            val type =
                when (media) {
                    is MediaUrlImage -> "image"
                    is MediaUrlVideo -> "video"
                    is MediaUrlPdf -> "file"
                    else -> return@mapNotNull null
                }
            MediaRef(
                url = media.url,
                type = type,
                mimeType = media.mimeType,
                alt = media.description,
                dimensions = media.dim?.let { "${it.width}x${it.height}" },
                blurhash = media.blurhash,
            )
        }

    private fun MutableList<BodySpan>.appendText(text: String) = add(BodySpan.Text(text))

    /** Collapses runs of adjacent [BodySpan.Text] into one, so a plain sentence is one span, not one per word. */
    private fun List<BodySpan>.mergeText(): List<BodySpan> {
        val out = ArrayList<BodySpan>(size)
        val buffer = StringBuilder()
        for (span in this) {
            if (span is BodySpan.Text) {
                buffer.append(span.text)
            } else {
                if (buffer.isNotEmpty()) {
                    out.add(BodySpan.Text(buffer.toString()))
                    buffer.clear()
                }
                out.add(span)
            }
        }
        if (buffer.isNotEmpty()) out.add(BodySpan.Text(buffer.toString()))
        return out
    }
}

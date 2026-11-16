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
package com.vitorpamplona.amethyst.commons.rendering.json

import com.vitorpamplona.amethyst.commons.rendering.AuthorRef
import com.vitorpamplona.amethyst.commons.rendering.BodySpan
import com.vitorpamplona.amethyst.commons.rendering.EventRef
import com.vitorpamplona.amethyst.commons.rendering.MediaRef
import com.vitorpamplona.amethyst.commons.rendering.RenderedDetails
import com.vitorpamplona.amethyst.commons.rendering.RenderedEvent

/**
 * [RenderedEvent] → a JSON-ready tree of maps, lists and primitives with stable
 * snake_case keys. amy serialises it as its `--json` contract, so renaming or
 * removing a key here is a breaking change for every script that reads amy.
 *
 * Kept free of any JSON library so it stays in `commonMain`: the caller picks the
 * serialiser.
 */
object JsonEventFormatter {
    fun toMap(
        rendered: RenderedEvent,
        includeBody: Boolean = true,
    ): Map<String, Any?> =
        buildMap {
            put("event_id", rendered.eventId)
            put("kind", rendered.kind)
            put("kind_name", rendered.kindName)
            put("author", author(rendered.author))
            put("created_at", rendered.createdAt)
            put("address", rendered.address)
            put("title", rendered.title)
            put("summary", rendered.summary)
            put("content", rendered.text)
            if (includeBody) put("body", rendered.body.map(::span))
            put("reply_to", rendered.replyTo?.let(::ref))
            put("root", rendered.root?.let(::ref))
            put("mentions", rendered.mentions)
            put("quotes", rendered.quotes.map(::ref))
            put("hashtags", rendered.hashtags)
            put("media", rendered.media.map(::media))
            put("content_warning", rendered.contentWarning)
            put("details", rendered.details?.let { details(it, includeBody) })
        }

    fun author(author: AuthorRef): Map<String, Any?> =
        mapOf(
            "pubkey" to author.pubKey,
            "npub" to author.npub,
            "name" to author.name,
            "display_name" to author.displayName,
            "nip05" to author.nip05,
            "picture" to author.picture,
        )

    fun ref(ref: EventRef): Map<String, Any?> =
        mapOf(
            "event_id" to ref.eventId,
            "address" to ref.address,
            "relay" to ref.relay,
            "author" to ref.author,
            "kind" to ref.kind,
            "external" to ref.external,
        )

    fun media(media: MediaRef): Map<String, Any?> =
        mapOf(
            "url" to media.url,
            "type" to media.type,
            "mime_type" to media.mimeType,
            "alt" to media.alt,
            "dimensions" to media.dimensions,
            "blurhash" to media.blurhash,
        )

    fun span(span: BodySpan): Map<String, Any?> =
        when (span) {
            is BodySpan.Text -> mapOf("type" to "text", "text" to span.text)
            is BodySpan.Link -> mapOf("type" to "link", "text" to span.text, "url" to span.url)
            is BodySpan.Image -> mapOf("type" to "image", "text" to span.text, "url" to span.url)
            is BodySpan.Video -> mapOf("type" to "video", "text" to span.text, "url" to span.url)
            is BodySpan.Hashtag -> mapOf("type" to "hashtag", "text" to span.text, "hashtag" to span.hashtag)
            is BodySpan.UserMention -> mapOf("type" to "user_mention", "text" to span.text, "pubkey" to span.pubKey)
            is BodySpan.EventMention -> mapOf("type" to "event_mention", "text" to span.text, "ref" to ref(span.ref))
            is BodySpan.CustomEmoji -> mapOf("type" to "custom_emoji", "text" to span.text, "url" to span.url)
            is BodySpan.Payment -> mapOf("type" to span.type, "text" to span.text)
        }

    private fun details(
        details: RenderedDetails,
        includeBody: Boolean,
    ): Map<String, Any?> =
        when (details) {
            is RenderedDetails.Profile ->
                mapOf(
                    "type" to "profile",
                    "name" to details.name,
                    "display_name" to details.displayName,
                    "about" to details.about,
                    "picture" to details.picture,
                    "banner" to details.banner,
                    "website" to details.website,
                    "nip05" to details.nip05,
                    "lud16" to details.lud16,
                )
            is RenderedDetails.ContactList ->
                mapOf("type" to "contact_list", "follow_count" to details.follows.size, "follows" to details.follows)
            is RenderedDetails.Reaction ->
                mapOf("type" to "reaction", "content" to details.content, "reaction" to details.type, "emoji_url" to details.emojiUrl)
            is RenderedDetails.Repost ->
                mapOf(
                    "type" to "repost",
                    "target" to details.target?.let(::ref),
                    "embedded" to details.embedded?.let { toMap(it, includeBody) },
                )
            is RenderedDetails.Deletion ->
                mapOf("type" to "deletion", "event_ids" to details.eventIds, "addresses" to details.addresses, "reason" to details.reason)
            is RenderedDetails.Zap ->
                mapOf(
                    "type" to "zap",
                    "amount_sats" to details.amountSats,
                    "sender" to details.sender,
                    "recipient" to details.recipient,
                    "comment" to details.comment,
                )
            is RenderedDetails.RelayList ->
                mapOf(
                    "type" to "relay_list",
                    "relays" to details.relays.map { mapOf("url" to it.url, "read" to it.read, "write" to it.write) },
                )
            is RenderedDetails.LongForm ->
                mapOf("type" to "long_form", "identifier" to details.identifier, "image" to details.image, "published_at" to details.publishedAt)
        }
}

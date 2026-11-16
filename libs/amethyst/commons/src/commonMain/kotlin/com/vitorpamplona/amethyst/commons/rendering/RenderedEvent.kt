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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A UI-agnostic view of one Nostr event: what every front end (amy's JSON, Desktop
 * and Android composables) needs to *show* the event, already parsed out of its raw
 * tags and content. Built by [EventRendererRegistry.render]; consumers never
 * re-parse [raw].
 *
 * Fields that a kind does not have stay empty (`null` / empty list) rather than
 * being guessed — a kind:7 has no [title], a kind:3 has no [replyTo]. Everything
 * that only one family of kinds carries lives in [details].
 */
data class RenderedEvent(
    val kind: Int,
    /** English label from quartz's kind registry, or null for an unknown kind. */
    val kindName: String?,
    val eventId: HexKey,
    val author: AuthorRef,
    val createdAt: Long,
    /** Addressable coordinate (`kind:pubkey:d`) when the event is addressable. */
    val address: String?,
    val title: String?,
    val summary: String?,
    /** The event's text after kind-specific extraction (e.g. empty for a kind:6 whose content is the embedded JSON). */
    val text: String,
    val body: List<BodySpan>,
    val mentions: List<HexKey>,
    val quotes: List<EventRef>,
    val hashtags: List<String>,
    val media: List<MediaRef>,
    /** The event this one answers (NIP-10 reply marker, NIP-22 parent, or the reacted/reposted/zapped target). */
    val replyTo: EventRef?,
    /** The thread root. Always set when known — for a direct reply it equals [replyTo]. */
    val root: EventRef?,
    val contentWarning: String?,
    val details: RenderedDetails?,
    val raw: Event,
)

/** Who signed the event, enriched with whatever profile the caller pre-resolved into [RenderContext]. */
data class AuthorRef(
    val pubKey: HexKey,
    /** Empty when [pubKey] is not a valid 32-byte key (it can come from an embedded, unsigned event). */
    val npub: String,
    val name: String?,
    val displayName: String?,
    val nip05: String?,
    val picture: String?,
) {
    /** Display name, then name, then a shortened npub (or pubkey) — never blank. */
    fun bestName(): String = displayName?.ifBlank { null } ?: name?.ifBlank { null } ?: npub.ifEmpty { pubKey }.take(12) + "…"
}

/** A pointer to another event (by id and/or addressable coordinate), with whatever hints the tags carried. */
data class EventRef(
    val eventId: HexKey?,
    val address: String? = null,
    val relay: String? = null,
    val author: HexKey? = null,
    val kind: Int? = null,
    /** A NIP-73 external id (URL, ISBN, podcast GUID, geohash…) when the target is not a Nostr event. */
    val external: String? = null,
)

/**
 * One piece of the event text, in display order. Concatenating [text] of every span gives
 * the body as the app's rich-text viewer lays it out, which can differ from the raw content
 * in whitespace (trailing spaces, `\r`, gallery line breaks) and in schemeless media URLs.
 * Use [RenderedEvent.text] for the raw content; don't derive offsets into it from spans.
 */
sealed interface BodySpan {
    val text: String

    data class Text(
        override val text: String,
    ) : BodySpan

    data class Link(
        override val text: String,
        val url: String,
    ) : BodySpan

    data class Image(
        override val text: String,
        val url: String,
    ) : BodySpan

    data class Video(
        override val text: String,
        val url: String,
    ) : BodySpan

    data class Hashtag(
        override val text: String,
        val hashtag: String,
    ) : BodySpan

    /** A `nostr:npub…` / `nostr:nprofile…` / `#[n]` pointing at a user. */
    data class UserMention(
        override val text: String,
        val pubKey: HexKey,
    ) : BodySpan

    /** A `nostr:note…` / `nevent…` / `naddr…` / `#[n]` pointing at another event. */
    data class EventMention(
        override val text: String,
        val ref: EventRef,
    ) : BodySpan

    data class CustomEmoji(
        override val text: String,
        val url: String?,
    ) : BodySpan

    /** Lightning invoice, LNURL withdraw, Cashu token, CLINK offer… — payment-ish tokens. */
    data class Payment(
        override val text: String,
        val type: String,
    ) : BodySpan
}

data class MediaRef(
    val url: String,
    /** `image`, `video` or `file`. */
    val type: String,
    val mimeType: String? = null,
    val alt: String? = null,
    val dimensions: String? = null,
    val blurhash: String? = null,
)

/** Kind-specific payload. One subtype per family of kinds that needs more than the common fields. */
sealed interface RenderedDetails {
    data class Profile(
        val name: String?,
        val displayName: String?,
        val about: String?,
        val picture: String?,
        val banner: String?,
        val website: String?,
        val nip05: String?,
        val lud16: String?,
    ) : RenderedDetails

    data class ContactList(
        val follows: List<HexKey>,
    ) : RenderedDetails

    data class Reaction(
        val content: String,
        /** `like`, `dislike`, `emoji`, or `custom_emoji`. */
        val type: String,
        val emojiUrl: String?,
    ) : RenderedDetails

    data class Repost(
        val target: EventRef?,
        /** The reposted event when it was embedded in the repost's content. */
        val embedded: RenderedEvent?,
    ) : RenderedDetails

    data class Deletion(
        val eventIds: List<HexKey>,
        val addresses: List<String>,
        val reason: String,
    ) : RenderedDetails

    data class Zap(
        val amountSats: Long?,
        /**
         * The zapper (the zap request's signer, not the LNURL provider that signed the receipt) —
         * null unless the request proves it (see `provenZapper`); so is [comment].
         */
        val sender: HexKey?,
        val recipient: HexKey?,
        val comment: String?,
    ) : RenderedDetails

    data class RelayList(
        val relays: List<RelayEntry>,
    ) : RenderedDetails

    data class LongForm(
        val identifier: String?,
        val image: String?,
        val publishedAt: Long?,
    ) : RenderedDetails
}

data class RelayEntry(
    val url: String,
    val read: Boolean,
    val write: Boolean,
)

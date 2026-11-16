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

import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuotes
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag

/**
 * The common assembly every renderer shares: author lookup, rich-text body,
 * mention / quote / hashtag / media extraction. Kind renderers override only the
 * fields their kind defines.
 */
object RenderSupport {
    fun build(
        event: Event,
        ctx: RenderContext,
        text: String = event.content,
        title: String? = null,
        summary: String? = null,
        replyTo: EventRef? = null,
        root: EventRef? = null,
        details: RenderedDetails? = null,
    ): RenderedEvent {
        val parsed = BodySpans.parse(text, event.tags, event.pubKey)

        val inlineUsers = parsed.spans.mapNotNull { (it as? BodySpan.UserMention)?.pubKey }
        val inlineEvents = parsed.spans.mapNotNull { (it as? BodySpan.EventMention)?.ref }
        val inlineHashtags = parsed.spans.mapNotNull { (it as? BodySpan.Hashtag)?.hashtag }

        val quoteTags =
            event.tags
                .taggedQuotes()
                .map {
                    when (it) {
                        is QEventTag -> it.eventId.takeIf { id -> id.isValid() }?.let { id -> EventRef(eventId = id, relay = it.relay?.url, author = it.author) }
                        is QAddressableTag -> EventRef(eventId = null, address = it.address.toValue(), relay = it.relay?.url, author = it.address.pubKeyHex, kind = it.address.kind)
                        else -> null
                    }
                }.filterNotNull()

        return RenderedEvent(
            kind = event.kind,
            kindName = KindNames.nameFor(event.kind),
            eventId = event.id,
            author = authorRef(event.pubKey, ctx),
            createdAt = event.createdAt,
            address = (event as? AddressableEvent)?.addressTag(),
            title = title,
            summary = summary,
            text = text,
            body = parsed.spans,
            mentions = (event.taggedUserIds() + inlineUsers).filter { it.isValid() }.distinct(),
            quotes = (quoteTags + inlineEvents).distinctBy { it.eventId ?: it.address },
            // Lowercase: hashtags are case-insensitive and `#t` filters match the lowercase form.
            hashtags = (event.hashtags() + inlineHashtags).map { it.lowercase() }.distinct(),
            media = parsed.media,
            replyTo = replyTo,
            root = root,
            contentWarning = event.tags.firstNotNullOfOrNull(ContentWarningTag::parse)?.let { it.reason ?: "" },
            details = details,
            raw = event,
        )
    }

    fun authorRef(
        pubKey: HexKey,
        ctx: RenderContext,
    ): AuthorRef {
        val metadata = ctx.profileOf(pubKey)
        return AuthorRef(
            pubKey = pubKey,
            // The pubkey of an embedded event (a zap request, a repost) is attacker-controlled.
            npub = if (pubKey.isValid()) NPub.create(pubKey) else "",
            name = metadata?.name,
            displayName = metadata?.displayName,
            nip05 = metadata?.nip05,
            picture = metadata?.picture,
        )
    }

    /** An `e`-tag ref, or null when the tag does not hold a 32-byte event id. */
    fun ref(
        tag: GenericETag?,
        kind: Int? = null,
    ): EventRef? = tag?.takeIf { it.eventId.isValid() }?.let { EventRef(eventId = it.eventId, relay = it.relay?.url, author = it.author, kind = kind) }

    /**
     * The last-resort rendering when a renderer failed on a malformed event: the raw text,
     * no spans or refs. Nothing on a relay can make [EventRendererRegistry.render] throw.
     */
    fun minimal(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent =
        RenderedEvent(
            kind = event.kind,
            kindName = KindNames.nameFor(event.kind),
            eventId = event.id,
            author = authorRef(event.pubKey, ctx),
            createdAt = event.createdAt,
            address = null,
            title = null,
            summary = null,
            text = event.content,
            body = listOf(BodySpan.Text(event.content)),
            mentions = emptyList(),
            quotes = emptyList(),
            hashtags = emptyList(),
            media = emptyList(),
            replyTo = null,
            root = null,
            contentWarning = null,
            details = null,
            raw = event,
        )
}

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
package com.vitorpamplona.amethyst.commons.rendering.renderers

import com.vitorpamplona.amethyst.commons.rendering.EventRef
import com.vitorpamplona.amethyst.commons.rendering.EventRenderer
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.RenderedDetails
import com.vitorpamplona.amethyst.commons.rendering.RenderedEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

/**
 * kind:1 — NIP-10 threading, in quartz's `replyingTo()` precedence (`reply` marker, then
 * `root` marker, then the legacy positional form). Positional `e` tags are read the way
 * the app reads them for `Note.replyTo` (`tagsWithoutCitations`): marker-less tags, with
 * or without a pubkey in the marker slot, minus the events the text merely cites inline.
 */
object TextNoteRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val note = event as? TextNoteEvent ?: return RenderSupport.build(event, ctx)
        val markedRoot = note.markedRoot()
        val marked = note.markedReply() ?: markedRoot

        val positional =
            if (marked != null) {
                emptyList()
            } else {
                val cited = note.findCitations()
                note.tags.mapNotNull { tag ->
                    val id = MarkedETag.parseOnlyPositionalThreadTagsIds(tag)
                    if (id != null && id.isValid() && id !in cited) MarkedETag.parseAllThreadTags(tag) else null
                }
            }

        val reply = marked ?: positional.lastOrNull()
        // A lone `reply` marker (legacy single-level form) names the root too.
        val root = markedRoot ?: positional.firstOrNull() ?: marked
        return RenderSupport.build(
            event,
            ctx,
            title = note.subject(),
            // A kind:1 answering an addressable (an article) may carry only an `a` tag; the app
            // threads it under that address (tagsWithoutCitations), so the refs fall back to it.
            replyTo = RenderSupport.ref(reply) ?: threadAddress(note.tags, last = true),
            root = RenderSupport.ref(root) ?: threadAddress(note.tags, last = false),
        )
    }

    /**
     * The `a` tag that roots ([last] false) or answers ([last] true) a thread, with the same
     * marker precedence as `e` tags. Community tags only scope the note, so they don't count.
     */
    private fun threadAddress(
        tags: Array<Array<String>>,
        last: Boolean,
    ): EventRef? {
        var root: Array<String>? = null
        var reply: Array<String>? = null
        var firstPositional: Array<String>? = null
        var lastPositional: Array<String>? = null
        tags.forEach { tag ->
            if (tag.size < 2 || tag[0] != "a") return@forEach
            val address = Address.parse(tag[1]) ?: return@forEach
            if (address.kind == CommunityDefinitionEvent.KIND) return@forEach
            when (tag.getOrNull(3)) {
                "root" -> root = tag
                "reply" -> reply = tag
                null, "" -> {
                    if (firstPositional == null) firstPositional = tag
                    lastPositional = tag
                }
            }
        }
        val chosen = if (last) reply ?: root ?: lastPositional else root ?: firstPositional ?: reply
        return chosen?.let { tag ->
            val address = Address.parse(tag[1]) ?: return null
            EventRef(
                eventId = null,
                address = tag[1],
                relay = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it)?.url },
                author = address.pubKeyHex,
                kind = address.kind,
            )
        }
    }
}

/** kind:1111 — NIP-22: lowercase tags name the parent, uppercase name the root scope. */
object CommentRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val comment = event as? CommentEvent ?: return RenderSupport.build(event, ctx)

        val replyKind = comment.tags.firstNotNullOfOrNull(ReplyKindTag::parse)?.toIntOrNull()
        val rootKind = comment.tags.firstNotNullOfOrNull(RootKindTag::parse)?.toIntOrNull()

        // A scope can name an event (`e`/`E`), an addressable (`a`/`A`, usually beside its `e`)
        // and/or an external id (`i`/`I`); the ref carries every one that is present.
        val replyEvent =
            comment.tags
                .lastNotNullOfOrNull(ReplyEventTag::parse)
                ?.ref
                ?.takeIf { it.eventId.isValid() }
        val replyAddress = comment.tags.lastNotNullOfOrNull(ReplyAddressTag::parseAddressId)
        val replyExternal = comment.tags.lastNotNullOfOrNull { ReplyIdentifierTag.parse(it) }
        val reply =
            if (replyEvent != null || replyAddress != null || replyExternal != null) {
                EventRef(replyEvent?.eventId, replyAddress, replyEvent?.relayHint?.url, replyEvent?.author, replyKind, replyExternal)
            } else {
                null
            }

        val rootEvent =
            comment.tags
                .firstNotNullOfOrNull(RootEventTag::parse)
                ?.ref
                ?.takeIf { it.eventId.isValid() }
        val rootAddress = comment.tags.firstNotNullOfOrNull(RootAddressTag::parseAddressId)
        val rootExternal = comment.tags.firstNotNullOfOrNull { RootIdentifierTag.parse(it) }
        val root =
            if (rootEvent != null || rootAddress != null || rootExternal != null) {
                EventRef(rootEvent?.eventId, rootAddress, rootEvent?.relayHint?.url, rootEvent?.author, rootKind, rootExternal)
            } else {
                null
            }

        return RenderSupport.build(event, ctx, replyTo = reply, root = root)
    }
}

/** kind:30023 — NIP-23 long-form article. */
object LongFormRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val article = event as? LongFormContentEvent ?: return RenderSupport.build(event, ctx)
        return RenderSupport.build(
            event,
            ctx,
            title = article.title(),
            summary = article.summary(),
            details =
                RenderedDetails.LongForm(
                    identifier = article.dTag(),
                    image = article.image(),
                    publishedAt = article.publishedAt(),
                ),
        )
    }
}

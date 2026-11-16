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

import com.vitorpamplona.amethyst.commons.model.provenZapper
import com.vitorpamplona.amethyst.commons.rendering.EventRef
import com.vitorpamplona.amethyst.commons.rendering.EventRenderer
import com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.RenderedDetails
import com.vitorpamplona.amethyst.commons.rendering.RenderedEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull
import com.vitorpamplona.quartz.utils.toLongValue

/** kind:7 — NIP-25: the target is the LAST `e` tag, or the `a` tag for an addressable-only reaction. */
object ReactionRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val reaction = event as? ReactionEvent ?: return RenderSupport.build(event, ctx)
        val content = reaction.content
        val emojiUrl = CustomEmoji.createEmojiMap(reaction.tags)[content]
        val type =
            when {
                content == "+" || content.isEmpty() -> "like"
                content == "-" -> "dislike"
                emojiUrl != null -> "custom_emoji"
                else -> "emoji"
            }
        return RenderSupport.build(
            event,
            ctx,
            // The content is the reaction itself, not prose: no body to parse.
            text = "",
            replyTo = RenderSupport.ref(reaction.tags.lastNotNullOfOrNull(ETag::parse)) ?: addressRef(reaction.tags),
            details = RenderedDetails.Reaction(content, type, emojiUrl),
        )
    }
}

/** kind:6 / kind:16 — NIP-18 reposts. The content, when present, is the reposted event's JSON. */
object RepostRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val repost = event as? BaseRepostEvent ?: return RenderSupport.build(event, ctx)
        val targetTag = event.tags.lastNotNullOfOrNull(ETag::parse)
        val target =
            RenderSupport.ref(targetTag, repost.boostedKind())
                ?: repost.boostedAddress()?.let { EventRef(eventId = null, address = it.toValue(), author = it.pubKeyHex, kind = it.kind) }

        // An embedded copy is only shown when it is the very event the tags name and its
        // signature holds — the content of a repost is attacker-controlled JSON. With no
        // target there is nothing to check it against, so nothing is embedded.
        val embedded =
            when (event) {
                is RepostEvent -> event.containedPost()
                is GenericRepostEvent -> event.containedPost()
                else -> null
            }?.takeIf { embedded ->
                val matches =
                    when {
                        target == null -> false
                        target.eventId != null -> embedded.id == target.eventId
                        else -> embedded is AddressableEvent && embedded.addressTag() == target.address
                    }
                matches && embedded.verify()
            }

        return RenderSupport.build(
            event,
            ctx,
            text = "",
            replyTo = target,
            details = RenderedDetails.Repost(target, embedded?.let { EventRendererRegistry.render(it, ctx) }),
        )
    }
}

/** kind:9735 — NIP-57 zap receipt. The sender is the zap request's signer, not the receipt's. */
object ZapReceiptRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val receipt = event as? ZapReceiptEvent ?: return RenderSupport.build(event, ctx)
        // The request's signer and comment only count when the request is proven (see provenZapper).
        val sender = receipt.provenZapper()
        return RenderSupport.build(
            event,
            ctx,
            text = "",
            replyTo =
                receipt.zappedPost().lastOrNull { it.isValid() }?.let { EventRef(eventId = it) }
                    ?: addressRef(receipt.tags),
            details =
                RenderedDetails.Zap(
                    amountSats = receipt.amount?.let(::satsOrNull),
                    sender = sender,
                    recipient = receipt.zappedAuthor().firstOrNull(),
                    comment = if (sender != null) receipt.zapRequest?.content?.ifBlank { null } else null,
                ),
        )
    }
}

/** kind:5 — NIP-09 deletion request. The content is the optional reason. */
object DeletionRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val deletion = event as? DeletionRequestEvent ?: return RenderSupport.build(event, ctx)
        return RenderSupport.build(
            event,
            ctx,
            details =
                RenderedDetails.Deletion(
                    eventIds = deletion.deleteEventIds(),
                    addresses = deletion.deleteAddressIds(),
                    reason = deletion.content,
                ),
        )
    }
}

/** A receipt's amount in whole sats; null when the invoice names none, or one too large to be real. */
private fun satsOrNull(amount: BigDecimal): Long? = if (amount.signum() <= 0 || amount.subtract(MAX_SATS).signum() > 0) null else amount.toLongValue()

/** 21 million BTC in sats: no receipt pays more, and a bigger value would wrap in a Long. */
private val MAX_SATS = BigDecimal("2100000000000000")

/** The last `a` tag as an address ref — the target of a reaction or zap aimed at an addressable only. */
private fun addressRef(tags: Array<Array<String>>): EventRef? =
    tags.lastNotNullOfOrNull(ATag::parseAddressId)?.let { address ->
        val parsed = Address.parse(address)
        EventRef(eventId = null, address = address, author = parsed?.pubKeyHex, kind = parsed?.kind)
    }

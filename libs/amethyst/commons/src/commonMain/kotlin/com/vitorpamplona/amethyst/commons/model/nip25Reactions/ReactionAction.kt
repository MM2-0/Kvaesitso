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
package com.vitorpamplona.amethyst.commons.model.nip25Reactions

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/**
 * Shared action for reacting to events (likes, emoji reactions).
 * Supports public reactions, custom emoji reactions, and NIP-17 private group reactions.
 *
 * Full-featured version extracted from Android for maximum code reuse across platforms.
 */
object ReactionAction {
    /**
     * Creates a signed reaction event.
     *
     * @param event The event to react to
     * @param reaction The reaction content (e.g., "+", "❤️", ":custom_emoji:")
     * @param signer The NostrSigner to sign the event
     * @param relayHint Optional relay hint URL where the original event was seen
     * @return Signed ReactionEvent ready to broadcast
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun reactTo(
        eventHint: EventHintBundle<Event>,
        reaction: String,
        signer: NostrSigner,
    ): ReactionEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot react: signer is not writeable")
        }
        if (eventHint.event.sig.isEmpty()) {
            // Unsealed private rumor: a public kind-7 would e-tag the private
            // rumor id onto public relays. Use reactToWithGroupSupport, which
            // gift-wraps reactions for empty-sig targets.
            throw IllegalStateException("Cannot react publicly to a private rumor")
        }

        return signer.sign(buildPublicReaction(eventHint, reaction))
    }

    /**
     * Builds a public reaction template for [eventHint], decoding a custom-emoji
     * reaction when present and falling back to plain text otherwise.
     *
     * When the target is a NIP-29 group event (it carries an `h` tag), the
     * reaction copies that `h` tag so the like stays scoped to the group and
     * lands on the group's host relay — where the recipient's group-notification
     * subscription (`#p`=them + `#h`=their groups, kind 7 included) can match it.
     * Without the `h` tag the like is a plain kind-7 that the host-relay query
     * never sees, so a reaction to someone's group message would only reach them
     * on the off chance NIP-65 routing delivered it to one of their inbox relays
     * — never for a host-relay-only group. This mirrors how kind-9 replies carry
     * the `h` tag to be notifiable.
     */
    private fun buildPublicReaction(
        eventHint: EventHintBundle<Event>,
        reaction: String,
    ): EventTemplate<ReactionEvent> {
        val groupScope: TagArrayBuilder<ReactionEvent>.() -> Unit = {
            eventHint.event.groupId()?.let { hTag(it) }
        }

        if (reaction.startsWith(":")) {
            val emojiUrl = EmojiUrlTag.decode(reaction)
            if (emojiUrl != null) {
                return ReactionEvent.build(emojiUrl, eventHint, initializer = groupScope)
            }
            // Fallback to text if emoji decode fails
        }
        return ReactionEvent.build(reaction, eventHint, initializer = groupScope)
    }

    /**
     * Creates a "like" reaction ("+").
     */
    suspend fun like(
        event: EventHintBundle<Event>,
        signer: NostrSigner,
    ): ReactionEvent = reactTo(event, "+", signer)

    /**
     * Advanced: React to an event with support for NIP-17 private groups
     * and unsealed rumors (private replies/posts in the public feed).
     *
     * This method handles both public and private reactions:
     * - For NIP17Group events: Creates private reactions within the group
     * - For unsealed rumors (empty signature): Creates gift-wrapped
     *   reactions fanned out to the rumor's author and every tagged user,
     *   so the private rumor id never lands on a public relay
     * - For regular events: Creates public reactions
     *
     * @param event The event to react to
     * @param eventHint EventHintBundle with relay information
     * @param reaction The reaction content (e.g., "+", "❤️", ":custom_emoji:")
     * @param signer The NostrSigner to sign the event
     * @param onPublic Callback for public reactions (returns signed ReactionEvent)
     * @param onPrivate Callback for private group reactions (returns NIP17Factory.Result)
     */
    suspend fun reactToWithGroupSupport(
        eventHint: EventHintBundle<Event>,
        reaction: String,
        signer: NostrSigner,
        onPublic: suspend (ReactionEvent) -> Unit,
        onPrivate: suspend (NIP17Factory.Result) -> Unit,
    ) {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot react: signer is not writeable")
        }

        val event = eventHint.event

        // Privacy is inherited from the target: reactions to private group
        // messages and to unsealed rumors must themselves be gift-wrapped.
        // createWraps adds the sender's self-copy back, so removing the
        // signer here only avoids a redundant entry.
        val privateRecipients: List<HexKey>? =
            when {
                event is NIP17Group -> event.groupMembers().toList()
                event.sig.isEmpty() -> (event.taggedUserIds() + event.pubKey).distinct().minus(signer.pubKey)
                else -> null
            }

        if (privateRecipients != null) {
            // Handle custom emoji reactions in groups
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrlTag.decode(reaction)
                if (emojiUrl != null) {
                    onPrivate(
                        NIP17Factory().createReactionWithinGroup(
                            emojiUrl = emojiUrl,
                            originalNote = eventHint,
                            to = privateRecipients,
                            signer = signer,
                        ),
                    )
                    return
                }
            }

            // Regular text reaction in group
            onPrivate(
                NIP17Factory().createReactionWithinGroup(
                    content = reaction,
                    originalNote = eventHint,
                    to = privateRecipients,
                    signer = signer,
                ),
            )
        } else {
            // Public reaction
            onPublic(signer.sign(buildPublicReaction(eventHint, reaction)))
        }
    }

    /**
     * Android-compatible overload: React to a note with full validation.
     *
     * This accepts Note/User directly and handles all validation including
     * duplicate checking and writeability checks.
     *
     * @param note The note to react to
     * @param reaction The reaction content
     * @param by The user reacting
     * @param signer The NostrSigner to sign the event
     * @param onPublic Callback for public reactions
     * @param onPrivate Callback for private group reactions
     */
    suspend fun reactTo(
        note: Note,
        reaction: String,
        by: User,
        signer: NostrSigner,
        onPublic: (ReactionEvent) -> Unit,
        onPrivate: suspend (NIP17Factory.Result) -> Unit,
    ) {
        // All validation in commons
        if (!signer.isWriteable()) return
        if (note.hasReacted(by, reaction)) return

        val eventHint = note.toEventHint<Event>() ?: return

        reactToWithGroupSupport(eventHint, reaction, signer, onPublic, onPrivate)
    }
}

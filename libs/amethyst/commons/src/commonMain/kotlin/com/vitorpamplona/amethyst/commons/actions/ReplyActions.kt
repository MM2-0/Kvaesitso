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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.model.AMETHYST_CLIENT_TAG_NAME
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.isClient

/**
 * Pure event-building "verbs" for kind:1 short-note replies (NIP-10).
 *
 * Builds a signed [TextNoteEvent] reply but does NOT publish it. The Amethyst
 * Android UI flow does more than these builders — non-UI callers are
 * responsible for the rest:
 *
 *  * **Publish.** Hand the returned event to your relay client. Android uses
 *    `Account.sendMyPublicAndPrivateOutbox`, the desktop deck pipes through
 *    `dispatch(signed, localCache, relayManager)`, amy uses `Context.publish`.
 *  * **Writeable check.** Skip the call when the active signer is read-only
 *    (e.g. an npub-only login). Building will fail at the sign step otherwise.
 *  * **Parent kind.** Only kind:1 [TextNoteEvent] parents are well-defined here
 *    — replies to articles / comments belong on the NIP-22 path
 *    (`CommentEvent.replyBuilder`). Callers must filter; this signature enforces
 *    it via [EventHintBundle] of `TextNoteEvent`.
 *  * **Local cache update.** If your caller has a local event cache, feed the
 *    new event back in so the UI / next read sees the update without a relay
 *    round-trip.
 *
 * Canonical entry point for non-UI callers — the underlying
 * [TextNoteEvent.build] reply-aware overload handles full NIP-10 tag carry:
 * `marker=root` (parent's root e-tag if present, else parent.id),
 * `marker=reply` (parent.id), the parent's full p-tag chain plus parent.pubKey,
 * and the relay hint from [EventHintBundle].
 */
object ReplyActions {
    /**
     * Build a kind:1 [TextNoteEvent] that replies to [parent], wrapping it with
     * NIP-10-correct marked e-tags and the parent's p-tag chain.
     *
     * Returns the signed event ready to be published. The reply preserves the
     * parent's root reference so conformant clients can reconstruct the thread.
     *
     * [mentions] are the users the text cites, as a `NewMessageTagger` collects them
     * (see `pTagsWithHints`); the rest of the text-derived tags come from [messageTags],
     * the composer's own.
     */
    suspend fun replyTo(
        parent: EventHintBundle<TextNoteEvent>,
        content: String,
        signer: NostrSigner,
        mentions: List<PTag> = emptyList(),
    ): TextNoteEvent {
        // Per NIP-10, replies MUST carry the p-tags of the event being replied
        // to plus the author's pubkey. TextNoteEvent.build(replyingTo=) only
        // emits the e-tag chain — p-tag carry is the caller's responsibility.
        // Only the parent's own `p` tags: linkedPubKeys() is a relay-hint set and also
        // holds zap-split beneficiaries, who are not participants in the thread.
        val carriedPubKeys =
            (parent.event.mentionKeys() + parent.event.pubKey)
                .distinct()
                .map { PTag(it, relayHint = null) }
        val chain = carriedPubKeys.mapTo(HashSet()) { it.pubKey }

        val template =
            TextNoteEvent.build(content, replyingTo = parent) {
                notify(carriedPubKeys + mentions.filter { it.pubKey !in chain }.distinctBy { it.pubKey })
                // A reply in a NIP-29 group stays in the group (and on its host relay).
                parent.event.groupId()?.let { hTag(it) }
                messageTags(content)
            }
        return signer.sign(template)
    }

    /**
     * Amethyst answers a top-level kind:1 that was itself posted from Amethyst (it
     * carries Amethyst's NIP-89 client tag) with a NIP-22 comment instead of a NIP-10
     * reply. Every other kind:1 gets a NIP-10 reply. The composer
     * (`ShortNotePostViewModel`) and amy share this rule so both put the same kind on
     * the wire.
     */
    fun repliesAsComment(parent: Event): Boolean =
        parent is TextNoteEvent &&
            parent.isNewThread() &&
            parent.isClient(AMETHYST_CLIENT_TAG_NAME)

    /**
     * Build a NIP-22 kind:1111 comment on [parent] — the reply form for every kind
     * other than kind:1, and for the kind:1s [repliesAsComment] selects. A comment on
     * a comment keeps the original root scope; a comment on a NIP-29 group event
     * carries the group's `h` tag so it stays in the group, as reactions do (see
     * `ReactionAction`).
     */
    suspend fun commentOn(
        parent: EventHintBundle<Event>,
        content: String,
        signer: NostrSigner,
        mentions: List<PTag> = emptyList(),
    ): CommentEvent {
        require(parent.event !is TextNoteEvent || repliesAsComment(parent.event)) {
            "this kind:1 takes a NIP-10 reply, not a NIP-22 comment"
        }
        val template =
            CommentEvent.replyBuilder(content, parent) {
                parent.event.groupId()?.let { hTag(it) }
                // replyBuilder already tags the parent's author.
                notify(mentions.filter { it.pubKey != parent.event.pubKey }.distinctBy { it.pubKey })
                messageTags(content)
            }
        return signer.sign(template)
    }

    /**
     * The reply a client should send to [parent], whatever its kind: a NIP-10
     * kind:1 reply for kind:1 notes (except those [repliesAsComment] selects), a
     * NIP-22 kind:1111 comment for everything else.
     * Refuses private rumors (empty signature): a public reply would e-tag a
     * private id onto public relays.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun reply(
        parent: EventHintBundle<Event>,
        content: String,
        signer: NostrSigner,
        mentions: List<PTag> = emptyList(),
    ): Event {
        check(signer.isWriteable()) { "Cannot reply: signer is not writeable" }
        check(parent.event.sig.isNotEmpty()) { "Cannot publicly reply to a private rumor" }
        return if (parent.event is TextNoteEvent && !repliesAsComment(parent.event)) {
            replyTo(parent as EventHintBundle<TextNoteEvent>, content, signer, mentions)
        } else {
            commentOn(parent, content, signer, mentions)
        }
    }
}

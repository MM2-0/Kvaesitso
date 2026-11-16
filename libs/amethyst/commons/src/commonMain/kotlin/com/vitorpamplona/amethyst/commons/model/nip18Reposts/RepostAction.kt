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
package com.vitorpamplona.amethyst.commons.model.nip18Reposts

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

/**
 * Shared action for reposting (boosting) events.
 * Supports both NIP-18 reposts (kind 6 for kind 1) and generic reposts (kind 16 for other kinds).
 */
object RepostAction {
    /**
     * Creates a signed repost event.
     *
     * @param eventHint The event to repost
     * @param signer The NostrSigner to sign the event
     * @return Signed repost event ready to broadcast
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun repost(
        eventHint: EventHintBundle<Event>,
        signer: NostrSigner,
    ): Event {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot repost: signer is not writeable")
        }
        if (eventHint.event.sig.isEmpty()) {
            // Unsealed private rumor: a public kind-6/16 would e-tag the
            // private rumor id onto public relays.
            throw IllegalStateException("Cannot repost a private rumor")
        }

        // Use NIP-18 RepostEvent (kind 6) for text notes (kind 1)
        // Use GenericRepostEvent (kind 16) for all other kinds
        val template =
            if (eventHint.event.kind == 1) {
                RepostEvent.build(eventHint)
            } else {
                GenericRepostEvent.build(eventHint)
            }

        return signer.sign(template)
    }

    /**
     * Android-compatible overload: Repost a note with full validation.
     *
     * This accepts Note directly and handles all validation including
     * duplicate checking (5-minute window) and writeability checks.
     *
     * @param note The note to repost
     * @param signer The NostrSigner to sign the event
     * @return Signed repost event, or null if validation fails
     */
    suspend fun repost(
        note: Note,
        signer: NostrSigner,
    ): Event? {
        // All validation in commons
        if (!signer.isWriteable()) return null
        if (note.isPrivateRumor()) return null
        if (note.hasBoostedInTheLast5Minutes(signer.pubKey)) return null

        val hint = note.toEventHint<Event>() ?: return null

        return repost(hint, signer)
    }
}

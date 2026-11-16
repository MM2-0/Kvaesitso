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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent

/**
 * NIP-09 deletion requests for the user's own public events. Builds and signs; the
 * caller publishes — to its outbox AND every relay the targets were seen on, since
 * a relay can only honour a deletion it receives.
 *
 * Private rumors (empty signature — NIP-17 DMs, Marmot / Concord messages) are
 * refused: a public kind:5 would put their ids on public relays. Those are
 * retracted inside their own conversation (`Account.deletePrivately`).
 */
object DeletionActions {
    /** Targets per kind:5, keeping each deletion well under the relays' ~65 KB event limit. */
    const val MAX_TARGETS_PER_EVENT = 200

    suspend fun delete(
        events: List<Event>,
        signer: NostrSigner,
    ): List<DeletionRequestEvent> {
        check(signer.isWriteable()) { "Cannot delete: signer is not writeable" }
        val notMine = events.filter { it.pubKey != signer.pubKey }
        require(notMine.isEmpty()) { "Cannot delete events signed by someone else: ${notMine.joinToString { it.id }}" }
        val rumors = events.filter { it.sig.isEmpty() }
        require(rumors.isEmpty()) { "Cannot publicly delete private rumors: ${rumors.joinToString { it.id }}" }

        return events
            .distinctBy { it.id }
            .chunked(MAX_TARGETS_PER_EVENT)
            .map { signer.sign(DeletionRequestEvent.build(it)) }
    }
}

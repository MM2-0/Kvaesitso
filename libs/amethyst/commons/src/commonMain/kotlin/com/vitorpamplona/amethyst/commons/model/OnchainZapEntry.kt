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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable

/**
 * NIP-BC onchain zap verification state.
 *
 * The chain backend may not have indexed the transaction at the moment we first
 * see the zap event — especially for the sender's own outgoing zaps, where we
 * consume the kind:8333 event milliseconds after broadcasting the transaction.
 * Tracking the verification status as a state machine lets us attach the zap
 * to the thread optimistically and upgrade it as the chain catches up.
 *
 * [level] establishes the monotonic upgrade order independently of declaration
 * order. The `Note.addOnchainZap` upgrade guard compares [level] (not
 * `ordinal`), so future contributors can safely reorder or insert states.
 */
enum class OnchainZapStatus(
    val level: Int,
) {
    /** Not yet checked against the chain (or the chain didn't have the tx yet). */
    UNVERIFIED(0),

    /** Verified against the chain, 0 confirmations (in mempool). */
    PENDING(1),

    /** Verified against the chain, ≥1 confirmation. */
    CONFIRMED(2),
}

/**
 * Per-(note, txid) NIP-BC onchain zap state.
 *
 * @property source The OnchainZapEvent note that contributed this entry. `source.author` is the
 *                  sender shown in the reactions gallery. When an entry is upgraded
 *                  (UNVERIFIED → PENDING/CONFIRMED, PENDING → CONFIRMED), the upgrading
 *                  event's note replaces the existing `source`.
 * @property claimedSats Sender-claimed amount from the kind:8333 event's `amount` tag. This
 *                       value is UNTRUSTED — only display it for the signed-in user's own
 *                       outgoing zaps (where the user knows what they sent). Never render
 *                       it for incoming zaps from other senders, as a spoofed amount tag
 *                       would mislead the viewer.
 * @property verifiedSats Satoshis verified to have paid the recipient's derived Taproot
 *                        address on chain. Zero while [status] is [OnchainZapStatus.UNVERIFIED].
 *                        NEVER the sender-claimed `amount` tag.
 * @property status See [OnchainZapStatus]. Only [OnchainZapStatus.CONFIRMED] entries are added
 *                  to the note's aggregate zap total.
 */
@Stable
data class OnchainZapEntry(
    val source: Note,
    val claimedSats: Long,
    val verifiedSats: Long,
    val status: OnchainZapStatus,
)

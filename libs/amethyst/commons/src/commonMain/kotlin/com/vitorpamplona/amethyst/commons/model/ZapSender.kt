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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent

/**
 * Who sent this zap, as far as the receipt alone can prove it: the signer of the embedded
 * NIP-57 zap request — but only when that request's signature verifies, it zaps the same
 * recipient the receipt names, and it is not an anonymous or private zap (whose signer is
 * a throwaway key). Null otherwise.
 *
 * The receipt's own signer is the recipient's LNURL provider, never the zapper, and the
 * embedded request is attacker-controlled JSON: anyone can publish a receipt naming any
 * pubkey as its zapper. Full NIP-57 Appendix F validation (the receipt signer is the
 * recipient's advertised `nostrPubkey`) needs an LNURL lookup and is not done here.
 *
 * Verifies a Schnorr signature: cache the result rather than calling it per frame.
 */
fun ZapReceiptEvent.provenZapper(): HexKey? {
    val request = zapRequest ?: return null
    if (request.hasAnonTag() || !request.pubKey.isValid()) return null
    val recipients = zappedAuthor()
    if (recipients.isNotEmpty() && request.zappedAuthor().none { it in recipients }) return null
    return if (request.verify()) request.pubKey else null
}

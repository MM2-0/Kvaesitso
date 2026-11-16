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
 * Per-payment NIP-B1 BOLT12 zap entry attached to a target Note.
 *
 * Unlike NIP-BC onchain zaps (which carry an async chain-verification state
 * machine), a BOLT12 zap is validated **synchronously** at consumption time —
 * the `lnp` payer proof is a self-contained cryptographic settlement proof — so
 * every entry stored here has already passed [com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidator]
 * and its amount is counted directly, the same way a NIP-57 lightning zap
 * receipt's amount is.
 *
 * @property source The kind:9736 Bolt12ZapEvent note. `source.author` is the
 *                  payer shown in the reactions gallery and notifications card
 *                  (the `P` tag; an anonymous zap uses an ephemeral key).
 * @property amountMillisats The validated amount in **millisatoshis** (the
 *                  `amount` tag, checked against the proof's `invoice_amount`).
 * @property cryptoVerified True when the payer proof's signatures were fully
 *                  verified. False when the zap is structurally valid and bound
 *                  to its intent but the proof is compressed and its signatures
 *                  can't yet be checked (pending the lightning/bolts#1346 merkle
 *                  reconstruction). The UI SHOULD label the latter as unverified.
 */
@Stable
data class Bolt12ZapEntry(
    val source: Note,
    val amountMillisats: Long,
    val cryptoVerified: Boolean,
)

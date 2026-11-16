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
package com.vitorpamplona.amethyst.commons.model.payments

import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget

/**
 * Picks the NIP-A3 payment targets a sender can hand off to when paying a note's
 * author.
 *
 * The rule is capability, not symmetry: a target is offered when something on
 * this device can actually open its URI. Paying a Monero address needs a wallet,
 * not a published address of one — so what the sender happens to publish about
 * themselves says nothing about whether they can pay, and is not consulted.
 */
object PayToRailMatcher {
    /**
     * The recipient's payable targets, de-duplicated by canonical type and in the
     * recipient's published order.
     *
     * Wallet-covered types (lightning, bitcoin) are dropped: those are the
     * picker's existing Lightning and on-chain rails, and re-offering them as a
     * hand-off would draw a second bolt icon beside the first.
     */
    fun match(recipientTargets: List<PaymentTarget>): List<PaymentTarget> {
        if (recipientTargets.isEmpty()) return emptyList()

        val seen = mutableSetOf<String>()
        return recipientTargets.filter { target ->
            val type = PaymentTargetTypes.canonical(target.type)
            type.isNotEmpty() &&
                target.authority.isNotBlank() &&
                !PaymentTargetTypes.isWalletCovered(type) &&
                seen.add(type)
        }
    }

    /**
     * Every gate on the hand-off chip, as one pure decision.
     *
     * Kept free of `Note`, `Context` and the availability singleton so the gates
     * are testable on their own — the caller supplies what it read from those.
     *
     * @param hasAuthor a note with no author pubkey has nobody to pay.
     * @param hasZapSplit a `payto` hand-off leaves with one authority and returns
     *   no receipt, so it cannot honour a note that asks to divide the zap.
     * @param canOpen whether an installed app resolves this target's URI. This is
     *   the substantive gate: everything else here is a precondition.
     * @param recipientTargets read lazily. Parsing the recipient's kind:10133 walks
     *   its tag array and allocates, and the common case is that a gate has already
     *   failed — the setting is off, or the note carries a split — so the cheap
     *   checks must run first. `peek` is on the one-tap zap path too.
     */
    fun selectFor(
        enabled: Boolean,
        hasAuthor: Boolean,
        hasZapSplit: Boolean,
        canOpen: (PaymentTarget) -> Boolean,
        recipientTargets: () -> List<PaymentTarget>,
    ): List<PaymentTarget> {
        if (!enabled || !hasAuthor || hasZapSplit) return emptyList()
        return match(recipientTargets()).filter(canOpen)
    }
}

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

import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm

/**
 * A user-configured way to pay a BOLT-11 — the unit the zap button (and any other
 * "pay this invoice" path) selects a default from. Today either a NIP-47 NWC wallet
 * or a CLINK Debits pointer; absence of any source means falling back to an external
 * wallet app (intent).
 *
 * [canShowBalance] is the honest capability marker: NWC can report balance/history,
 * a CLINK debit cannot, so the UI renders the two rows differently.
 */
sealed interface PaymentSource {
    val id: String
    val name: String
    val canShowBalance: Boolean

    data class Nwc(
        val wallet: NwcWalletEntryNorm,
    ) : PaymentSource {
        override val id: String get() = wallet.id
        override val name: String get() = wallet.name
        override val canShowBalance: Boolean get() = true
    }

    data class ClinkDebit(
        val wallet: ClinkDebitWalletEntryNorm,
    ) : PaymentSource {
        override val id: String get() = wallet.id
        override val name: String get() = wallet.name
        override val canShowBalance: Boolean get() = false
    }
}

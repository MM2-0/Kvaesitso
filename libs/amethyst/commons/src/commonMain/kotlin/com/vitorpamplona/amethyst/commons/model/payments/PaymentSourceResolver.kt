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
 * Builds the unified list of configured [PaymentSource]s (NWC + CLINK debit) and
 * resolves which one is the default for "pay this invoice" paths.
 *
 * The default is a single id spanning both lists (ids are random UUIDs, unique across
 * types), so one selector picks the spend rail regardless of type. When no explicit
 * default is set, the first configured source wins — NWC wallets are listed before
 * debits, preserving today's "first NWC wallet" fallback.
 */
object PaymentSourceResolver {
    fun all(
        nwcWallets: List<NwcWalletEntryNorm>,
        debitWallets: List<ClinkDebitWalletEntryNorm>,
    ): List<PaymentSource> = nwcWallets.map { PaymentSource.Nwc(it) } + debitWallets.map { PaymentSource.ClinkDebit(it) }

    fun resolveDefault(
        nwcWallets: List<NwcWalletEntryNorm>,
        debitWallets: List<ClinkDebitWalletEntryNorm>,
        defaultId: String?,
    ): PaymentSource? = resolveDefault(all(nwcWallets, debitWallets), defaultId)

    fun resolveDefault(
        sources: List<PaymentSource>,
        defaultId: String?,
    ): PaymentSource? = defaultId?.let { id -> sources.firstOrNull { it.id == id } } ?: sources.firstOrNull()
}

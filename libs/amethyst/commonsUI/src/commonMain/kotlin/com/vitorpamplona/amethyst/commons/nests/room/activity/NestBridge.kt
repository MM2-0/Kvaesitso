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
package com.vitorpamplona.amethyst.commons.nests.room.activity

import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlin.concurrent.Volatile

/**
 * Process-level singleton that hands the active [AccountViewModel] from the
 * main activity to [NestActivity]. Mirrors the
 * [com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge] pattern so a
 * separately-tasked Activity in the same process can reach the logged-in
 * account without serialising a `NostrSigner` through an Intent extra.
 *
 * Set immediately before `startActivity(Intent(..., NestActivity::class))`
 * is called and cleared on logout / account switch.
 */
object NestBridge {
    @Volatile
    var accountViewModel: AccountViewModel? = null
        private set

    fun set(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
    }

    fun clear() {
        accountViewModel = null
    }

    /** Drops [viewModel] if it is still the one held; a newer account's ViewModel may have replaced it. */
    fun clear(viewModel: AccountViewModel) {
        if (accountViewModel === viewModel) accountViewModel = null
    }
}

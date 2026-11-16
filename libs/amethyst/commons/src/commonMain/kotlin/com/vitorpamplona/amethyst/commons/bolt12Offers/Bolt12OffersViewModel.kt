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
package com.vitorpamplona.amethyst.commons.bolt12Offers

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits the logged-in user's NIP-B1 BOLT12 offer list (kind 10058). Mirrors
 * [com.vitorpamplona.amethyst.commons.paymentTargets.PaymentTargetsViewModel];
 * each entry is a canonical raw `lno1...` offer string.
 */
@Stable
class Bolt12OffersViewModel : ViewModel() {
    private lateinit var accountViewModel: BaseAccountViewModel
    private lateinit var account: Account

    private val _offers = MutableStateFlow<List<String>>(emptyList())
    val offers = _offers.asStateFlow()
    private var isModified = false

    fun init(accountViewModel: BaseAccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() {
        refresh()
    }

    fun refresh() {
        isModified = false
        viewModelScope.launch {
            _offers.update { account.bolt12OfferList.flow.value }
        }
    }

    /** Returns the canonical offer if [raw] is a well-formed BOLT12 offer, else null. */
    fun canonicalOfferOrNull(raw: String): String? {
        val canonical = Bolt12Bech32.canonicalize(raw)
        return if (Bolt12Bech32.isOffer(canonical)) canonical else null
    }

    /** Adds [raw] if it's a valid offer not already present; returns true when added. */
    fun addOffer(raw: String): Boolean {
        val canonical = canonicalOfferOrNull(raw) ?: return false
        if (_offers.value.contains(canonical)) return false
        _offers.update { it.plus(canonical) }
        isModified = true
        return true
    }

    fun removeOffer(offer: String) {
        _offers.update { it.minus(offer) }
        isModified = true
    }

    fun saveOffers() {
        if (isModified) {
            accountViewModel.launchSigner {
                saveOffersSuspend()
            }
        }
    }

    suspend fun saveOffersSuspend() {
        if (isModified) {
            account.saveBolt12Offers(_offers.value)
            refresh()
        }
    }
}

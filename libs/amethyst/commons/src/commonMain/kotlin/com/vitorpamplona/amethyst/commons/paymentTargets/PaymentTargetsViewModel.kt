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
package com.vitorpamplona.amethyst.commons.paymentTargets

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.payments.PaymentTargetTypes
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class PaymentTargetsViewModel : ViewModel() {
    private lateinit var accountViewModel: BaseAccountViewModel
    private lateinit var account: Account

    private val _paymentTargets = MutableStateFlow<List<PaymentTarget>>(emptyList())
    val paymentTargets = _paymentTargets.asStateFlow()
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
            _paymentTargets.update {
                account.paymentTargetsState.flow.value
            }
        }
    }

    fun addTarget(
        type: String,
        authority: String,
    ) {
        // Stores the NIP-A3 spelling of known aliases (btc -> bitcoin, cashapp -> cashme).
        val trimmedType = PaymentTargetTypes.canonical(type)
        val trimmedAuthority = authority.trim()
        if (trimmedType.isEmpty() || trimmedAuthority.isEmpty()) return
        val target = PaymentTarget(trimmedType, trimmedAuthority)
        if (_paymentTargets.value.any { it.type == target.type && it.authority == target.authority }) return
        _paymentTargets.update { it.plus(target) }
        isModified = true
    }

    fun removeTarget(target: PaymentTarget) {
        _paymentTargets.update { it.minus(target) }
        isModified = true
    }

    fun savePaymentTargets() {
        if (isModified) {
            accountViewModel.launchSigner {
                savePaymentTargetsSuspend()
            }
        }
    }

    suspend fun savePaymentTargetsSuspend() {
        if (isModified) {
            account.savePaymentTargets(_paymentTargets.value)
            refresh()
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.profile.zaps.dal

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.compareToValue
import com.vitorpamplona.quartz.utils.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

@Immutable
data class ZapAmount(
    val user: User,
    val amount: BigDecimal,
)

@Stable
class UserProfileZapsViewModel(
    val user: User,
    val account: Account,
) : ViewModel() {
    val zapsToUser =
        Filter(
            kinds = listOf(ZapReceiptEvent.KIND, OnchainZapEvent.KIND, Bolt12ZapEvent.KIND),
            tags = mapOf("p" to listOf(user.pubkeyHex)),
        )

    // Largest amount first, then by pubkey so equal amounts keep a stable order.
    val sortingModel: Comparator<ZapAmount> =
        Comparator<ZapAmount> { a, b -> b.amount.compareToValue(a.amount) }.thenBy { it.user.pubkeyHex }

    suspend fun mapRequest(zapEvent: ZapReceiptEvent): ZapAmount? {
        val zapRequest =
            zapEvent.zapRequest ?: return ZapAmount(
                LocalCache.getOrCreateUser(zapEvent.pubKey),
                zapEvent.amount ?: BigDecimal(0),
            )

        return if (zapRequest.isPrivateZap()) {
            // if user is not the logged in, we cannot decrypt.
            if (user.pubkeyHex == account.pubKey) {
                val cachedPrivateRequest = account.privateZapsDecryptionCache.decryptPrivateZap(zapRequest)
                if (cachedPrivateRequest != null) {
                    ZapAmount(
                        LocalCache.getOrCreateUser(cachedPrivateRequest.pubKey),
                        zapEvent.amount ?: BigDecimal(0),
                    )
                } else {
                    ZapAmount(
                        LocalCache.getOrCreateUser(zapRequest.pubKey),
                        zapEvent.amount ?: BigDecimal(0),
                    )
                }
            } else {
                ZapAmount(
                    LocalCache.getOrCreateUser(zapRequest.pubKey),
                    zapEvent.amount ?: BigDecimal(0),
                )
            }
        } else {
            ZapAmount(
                LocalCache.getOrCreateUser(zapRequest.pubKey),
                zapEvent.amount ?: BigDecimal(0),
            )
        }
    }

    private fun mapOnchainZap(event: OnchainZapEvent): ZapAmount {
        val amountSats = event.claimedAmountInSats() ?: 0L
        return ZapAmount(
            LocalCache.getOrCreateUser(event.pubKey),
            BigDecimal(amountSats),
        )
    }

    private fun mapBolt12Zap(event: Bolt12ZapEvent): ZapAmount {
        // The payer is the `P` tag; anonymous zaps fall back to the event pubkey.
        // amount() is in millisats — divide to sats for the profile total.
        val amountSats = (event.amount() ?: 0L) / 1000
        return ZapAmount(
            LocalCache.getOrCreateUser(event.payer() ?: event.pubKey),
            BigDecimal(amountSats),
        )
    }

    suspend fun List<Event>.sumAmountsByUser(): List<ZapAmount> {
        val results = mutableMapOf<User, BigDecimal>()

        this.forEach { zapEvent ->
            val zapAmount =
                when (zapEvent) {
                    is ZapReceiptEvent -> mapRequest(zapEvent)
                    is OnchainZapEvent -> mapOnchainZap(zapEvent)
                    is Bolt12ZapEvent -> mapBolt12Zap(zapEvent)
                    else -> null
                }
            if (zapAmount != null) {
                val existingAmount = results[zapAmount.user] ?: BigDecimal(0)
                results[zapAmount.user] = existingAmount + zapAmount.amount
            }
        }

        return results.map { (user, amount) -> ZapAmount(user, amount) }.sortedWith(sortingModel)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val receivedZapAmountsByUser: StateFlow<List<ZapAmount>> =
        account.cache
            .observeEvents<Event>(zapsToUser)
            .sample(500)
            .map { zapEvents ->
                zapEvents.sumAmountsByUser()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily,
            )

    val totalReceivedZaps =
        receivedZapAmountsByUser
            .map { amounts -> amounts.fold(BigDecimal(0)) { total, zap -> total + zap.amount } }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = BigDecimal(0),
                started = SharingStarted.Lazily,
            )

    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = UserProfileZapsViewModel(user, account) as T
    }
}

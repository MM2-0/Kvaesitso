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
package com.vitorpamplona.amethyst.commons.polls

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.plus
import com.vitorpamplona.quartz.utils.toDoubleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private val ZERO = BigDecimal(0)

@Stable
data class PollOption(
    val option: Int,
    val descriptor: String,
    var zappedValue: MutableState<BigDecimal> = mutableStateOf(ZERO),
    var tally: MutableState<Float> = mutableFloatStateOf(0f),
    var consensusThreadhold: MutableState<Boolean> = mutableStateOf(false),
    var zappedByLoggedIn: MutableState<Boolean> = mutableStateOf(false),
)

@Stable
class PollNoteViewModel : ViewModel() {
    private lateinit var account: Account
    private var pollNote: Note? = null

    private var pollEvent: ZapPollEvent? = null
    private var pollOptions: Map<Int, String>? = null
    private var valueMaximum: Long? = null
    private var valueMinimum: Long? = null

    private var closedAt: Long? = null
    private var consensusThreshold: Double? = null

    private var totalZapped: BigDecimal = ZERO
    private var wasZappedByLoggedInAccount: Boolean = false

    var canZap = mutableStateOf(false)
    var tallies: List<PollOption> = emptyList()

    fun init(acc: Account) {
        account = acc
    }

    fun load(note: Note?) {
        if (pollNote != note) {
            pollNote = note
            pollEvent = pollNote?.event as ZapPollEvent
            pollOptions = pollEvent?.pollOptions()
            valueMaximum = pollEvent?.maxAmount()
            valueMinimum = pollEvent?.minAmount()
            consensusThreshold = pollEvent?.consensusThreshold()
            closedAt = pollEvent?.closedAt()

            totalZapped = ZERO
            wasZappedByLoggedInAccount = false

            canZap.value = checkIfCanZap()

            tallies = pollOptions?.keys?.map { option ->
                PollOption(
                    option,
                    pollOptions?.get(option) ?: "",
                )
            } ?: emptyList()
        }
    }

    fun refreshTallies() {
        viewModelScope.launch(Dispatchers.IO) {
            totalZapped = totalZapped()
            wasZappedByLoggedInAccount = false
            wasZappedByLoggedInAccount = account.zaps.calculateIfNoteWasZappedByAccount(pollNote, 0)
            canZap.value = checkIfCanZap()

            tallies.forEach {
                val zappedValue = zappedPollOptionAmount(it.option)
                val total = totalZapped.toDoubleValue()
                // The share, rounded to two decimals.
                val tallyValue =
                    if (total > 0) {
                        (zappedValue.toDoubleValue() / total * 100).roundToLong() / 100.0
                    } else {
                        0.0
                    }

                it.zappedValue.value = zappedValue
                it.tally.value = tallyValue.toFloat()
                it.consensusThreadhold.value = consensusThreshold?.let { threshold -> tallyValue >= threshold } == true
                it.zappedByLoggedIn.value = account.userProfile().let { it1 -> cachedIsPollOptionZappedBy(it.option, it1) }
            }
        }
    }

    fun checkIfCanZap(): Boolean {
        val account = account
        val note = pollNote ?: return false
        return account.userProfile() != note.author && !wasZappedByLoggedInAccount
    }

    fun isVoteAmountAtomic() = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    fun isPollClosed(): Boolean =
        closedAt?.let {
            // allow 2 minute leeway for zap to propagate
            pollNote?.createdAt()?.plus(it * (86400 + 120))!! < TimeUtils.now()
        } == true

    fun voteAmountPlaceHolderText(sats: String): String =
        when {
            valueMinimum == null && valueMaximum == null -> sats
            valueMinimum == null -> "1—$valueMaximum $sats"
            valueMaximum == null -> ">$valueMinimum $sats"
            else -> "$valueMinimum—$valueMaximum $sats"
        }

    fun inputVoteAmountLong(textAmount: String) =
        if (textAmount.isEmpty()) {
            null
        } else {
            try {
                textAmount.toLong()
            } catch (e: Exception) {
                null
            }
        }

    fun isValidInputVoteAmount(amount: BigDecimal?): Boolean {
        if (amount == null) return false
        val sats = amount.toDoubleValue()
        val min = valueMinimum
        val max = valueMaximum
        return when {
            min == null && max == null -> sats > 0
            min == null -> sats > 0 && sats <= max!!
            max == null -> sats >= min
            else -> min <= sats && sats <= max
        }
    }

    fun isValidInputVoteAmount(amount: Long?): Boolean {
        when {
            amount == null -> {
                return false
            }

            valueMinimum == null && valueMaximum == null -> {
                if (amount > 0) {
                    return true
                }
            }

            valueMinimum == null -> {
                if (amount > 0 && amount <= valueMaximum!!) {
                    return true
                }
            }

            valueMaximum == null -> {
                if (amount >= valueMinimum!!) {
                    return true
                }
            }

            else -> {
                if ((valueMinimum!! <= amount) && (amount <= valueMaximum!!)) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun isPollOptionZappedBy(
        option: Int,
        user: User,
        afterTimeInSeconds: Long,
    ): Boolean = pollNote?.isZappedBy(option, user, afterTimeInSeconds, account) == true

    fun cachedIsPollOptionZappedBy(
        option: Int,
        user: User,
    ): Boolean =
        pollNote!!.zaps.any {
            val zapEvent = it.value?.event as? ZapReceiptEvent
            val privateZapAuthor =
                (it.key.event as? ZapRequestEvent)?.let {
                    account.privateZapsDecryptionCache.cachedPrivateZap(it)
                }
            zapEvent?.zappedPollOption() == option &&
                (it.key.author?.pubkeyHex == user.pubkeyHex || privateZapAuthor?.pubKey == user.pubkeyHex)
        }

    private fun zappedPollOptionAmount(option: Int): BigDecimal =
        pollNote?.zaps?.values?.fold(ZERO) { acc, it ->
            val event = it?.event as? ZapReceiptEvent
            val zapAmount = event?.amount ?: ZERO
            val isValidAmount = isValidInputVoteAmount(event?.amount)

            if (isValidAmount && event?.zappedPollOption() == option) {
                acc + zapAmount
            } else {
                acc
            }
        }
            ?: ZERO

    private fun totalZapped(): BigDecimal =
        pollNote?.zaps?.values?.fold(ZERO) { acc, it ->
            val zapEvent = (it?.event as? ZapReceiptEvent)
            val zapAmount = zapEvent?.amount ?: ZERO
            val isValidAmount = isValidInputVoteAmount(zapEvent?.amount)

            if (isValidAmount && zapEvent?.zappedPollOption() != null) {
                acc + zapAmount
            } else {
                acc
            }
        }
            ?: ZERO

    fun createZapOptionsThatMatchThePollingParameters(zapPaymentChoices: List<Long>): List<Long> {
        val options =
            zapPaymentChoices
                .filter { isValidInputVoteAmount(it) }
                .toMutableList()
        if (options.isEmpty()) {
            valueMinimum?.let { minimum ->
                valueMaximum?.let { maximum ->
                    if (minimum != maximum) {
                        options.add(((minimum + maximum) / 2))
                    }
                }
            }
        }
        valueMinimum?.let { options.add(it) }
        valueMaximum?.let { options.add(it) }

        return options.toSet().sorted()
    }
}

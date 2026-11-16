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
package com.vitorpamplona.amethyst.commons.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.search.calendar.EpochDayCounter
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.service.BundledInsert
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.util.showCount
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.plus
import com.vitorpamplona.quartz.utils.toDoubleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The last seven days of activity, oldest first: [days] are offsets from today (-6..0) and each
 * series has one value per day. Headless, so `commonsUI` builds the chart's own model from it.
 */
@Immutable
data class NotificationChart(
    val days: List<Int>,
    val replies: List<Float>,
    val boosts: List<Float>,
    val reactions: List<Float>,
    val zaps: List<Float>,
)

@Stable
class NotificationSummaryState(
    val account: Account,
) {
    val user: User = account.userProfile()

    private var reactions = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var boosts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var zaps = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    private var replies = MutableStateFlow<Map<String, Int>>(emptyMap())

    private var _chartData = MutableStateFlow<NotificationChart?>(null)
    val chartData = _chartData.asStateFlow()

    private var takenIntoAccount = setOf<HexKey>()

    val todaysReplyCount = replies.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysBoostCount = boosts.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysReactionCount = reactions.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysZapAmount = zaps.map { showAmountInteger(it[today()]) }.distinctUntilChanged()

    // Buckets are local calendar days, keyed yyyy-MM-dd. [days] is built once per pass: it reads
    // the zone rules once, and a pass buckets every notification in the cache.
    private fun formatDate(
        createAt: Long,
        days: EpochDayCounter,
    ): String = SearchDate.civilFromDays(days.epochDay(createAt)).ymd()

    fun today(): String = LocalClock.today().ymd()

    suspend fun initializeSuspend() {
        val days = LocalClock.epochDayCounter()

        val currentUser = user.pubkeyHex

        val reactions = mutableMapOf<String, Int>()
        val boosts = mutableMapOf<String, Int>()
        val zaps = mutableMapOf<String, BigDecimal>()
        val replies = mutableMapOf<String, Int>()
        val takenIntoAccount = mutableSetOf<HexKey>()

        LocalCache.notes.forEach { _, it ->
            val noteEvent = it.event
            if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id)) {
                when {
                    noteEvent is ReactionEvent -> {
                        if (noteEvent.originalAuthor().contains(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt, days)
                            reactions[netDate] = (reactions[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id)
                        }
                    }

                    noteEvent is RepostEvent -> {
                        if (noteEvent.originalAuthorKeys().contains(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt, days)
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id)
                        }
                    }

                    noteEvent is GenericRepostEvent -> {
                        if (noteEvent.originalAuthorKeys().contains(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt, days)
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id)
                        }
                    }

                    noteEvent is ZapReceiptEvent -> {
                        // the user might be sending his own receipts noteEvent.pubKey != currentUser
                        if (noteEvent.zappedAuthor().contains(currentUser)) {
                            val netDate = formatDate(noteEvent.createdAt, days)
                            zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + (noteEvent.amount ?: BigDecimal(0))
                            takenIntoAccount.add(noteEvent.id)
                        }
                    }

                    noteEvent is OnchainZapEvent -> {
                        if (noteEvent.recipient() == currentUser) {
                            val amount = noteEvent.claimedAmountInSats()
                            if (amount != null) {
                                val netDate = formatDate(noteEvent.createdAt, days)
                                zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + BigDecimal(amount)
                                takenIntoAccount.add(noteEvent.id)
                            }
                        }
                    }

                    noteEvent is Bolt12ZapEvent -> {
                        if (noteEvent.recipient() == currentUser) {
                            val amount = noteEvent.amount()
                            if (amount != null) {
                                val netDate = formatDate(noteEvent.createdAt, days)
                                zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + BigDecimal(amount / 1000)
                                takenIntoAccount.add(noteEvent.id)
                            }
                        }
                    }

                    noteEvent is BaseThreadedEvent &&
                        currentUser in noteEvent.mentionKeys() &&
                        noteEvent.pubKey != currentUser -> {
                        val isCitation =
                            noteEvent.findCitations().any {
                                LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == currentUser
                            }

                        val netDate = formatDate(noteEvent.createdAt, days)
                        if (isCitation) {
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        } else {
                            replies[netDate] = (replies[netDate] ?: 0) + 1
                        }
                        takenIntoAccount.add(noteEvent.id)
                    }
                }
            }
        }

        this.takenIntoAccount = takenIntoAccount
        this.reactions.emit(reactions)
        this.replies.emit(replies)
        this.zaps.emit(zaps)
        this.boosts.emit(boosts)

        refreshChartModel()
    }

    suspend fun addToStatsSuspend(newBlockNotes: Set<Set<Note>>) {
        val days = LocalClock.epochDayCounter()

        val currentUser = user.pubkeyHex

        val reactions = this.reactions.value.toMutableMap()
        val boosts = this.boosts.value.toMutableMap()
        val zaps = this.zaps.value.toMutableMap()
        val replies = this.replies.value.toMutableMap()
        val takenIntoAccount = this.takenIntoAccount.toMutableSet()
        var hasNewElements = false

        newBlockNotes.forEach { newNotes ->
            newNotes.forEach {
                val noteEvent = it.event
                if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id)) {
                    when {
                        noteEvent is ReactionEvent -> {
                            if (noteEvent.originalAuthor().contains(currentUser) && noteEvent.pubKey != currentUser) {
                                val netDate = formatDate(noteEvent.createdAt, days)
                                reactions[netDate] = (reactions[netDate] ?: 0) + 1
                                takenIntoAccount.add(noteEvent.id)
                                hasNewElements = true
                            }
                        }

                        noteEvent is RepostEvent -> {
                            if (noteEvent.originalAuthorKeys().contains(currentUser) && noteEvent.pubKey != currentUser) {
                                val netDate = formatDate(noteEvent.createdAt, days)
                                boosts[netDate] = (boosts[netDate] ?: 0) + 1
                                takenIntoAccount.add(noteEvent.id)
                                hasNewElements = true
                            }
                        }

                        noteEvent is GenericRepostEvent -> {
                            if (noteEvent.originalAuthorKeys().contains(currentUser) && noteEvent.pubKey != currentUser) {
                                val netDate = formatDate(noteEvent.createdAt, days)
                                boosts[netDate] = (boosts[netDate] ?: 0) + 1
                                takenIntoAccount.add(noteEvent.id)
                                hasNewElements = true
                            }
                        }

                        noteEvent is ZapReceiptEvent -> {
                            if (noteEvent.zappedAuthor().contains(currentUser)) {
                                //  && noteEvent.pubKey != currentUser User might be sending his own receipts
                                val netDate = formatDate(noteEvent.createdAt, days)
                                zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + (noteEvent.amount ?: BigDecimal(0))
                                takenIntoAccount.add(noteEvent.id)
                                hasNewElements = true
                            }
                        }

                        noteEvent is OnchainZapEvent -> {
                            if (noteEvent.recipient() == currentUser) {
                                val amount = noteEvent.claimedAmountInSats()
                                if (amount != null) {
                                    val netDate = formatDate(noteEvent.createdAt, days)
                                    zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + BigDecimal(amount)
                                    takenIntoAccount.add(noteEvent.id)
                                    hasNewElements = true
                                }
                            }
                        }

                        noteEvent is Bolt12ZapEvent -> {
                            if (noteEvent.recipient() == currentUser) {
                                val amount = noteEvent.amount()
                                if (amount != null) {
                                    val netDate = formatDate(noteEvent.createdAt, days)
                                    zaps[netDate] = (zaps[netDate] ?: BigDecimal(0)) + BigDecimal(amount / 1000)
                                    takenIntoAccount.add(noteEvent.id)
                                    hasNewElements = true
                                }
                            }
                        }

                        noteEvent is BaseThreadedEvent &&
                            currentUser in noteEvent.mentionKeys() &&
                            noteEvent.pubKey != currentUser -> {
                            val isCitation =
                                noteEvent.findCitations().any {
                                    LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == currentUser
                                }

                            val netDate = formatDate(noteEvent.createdAt, days)
                            if (isCitation) {
                                boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            } else {
                                replies[netDate] = (replies[netDate] ?: 0) + 1
                            }
                            takenIntoAccount.add(noteEvent.id)
                            hasNewElements = true
                        }
                    }
                }
            }
        }

        if (hasNewElements) {
            this.takenIntoAccount = takenIntoAccount
            this.reactions.emit(reactions)
            this.replies.emit(replies)
            this.zaps.emit(zaps)
            this.boosts.emit(boosts)

            refreshChartModel()
        }
    }

    private suspend fun refreshChartModel() {
        val today = LocalClock.today()

        val dataAxisLabelIndexes = listOf(-6, -5, -4, -3, -2, -1, 0)
        val dataAxisLabels = dataAxisLabelIndexes.map { today.plusDays(it).ymd() }

        this._chartData.emit(
            NotificationChart(
                days = dataAxisLabelIndexes,
                replies = dataAxisLabels.map { replies.value[it]?.toFloat() ?: 0f },
                boosts = dataAxisLabels.map { boosts.value[it]?.toFloat() ?: 0f },
                reactions = dataAxisLabels.map { reactions.value[it]?.toFloat() ?: 0f },
                zaps = dataAxisLabels.map { zaps.value[it]?.toDoubleValue()?.toFloat() ?: 0f },
            ),
        )
    }

    // determine if the min max are so close that they render to the same number.
    fun shouldShowDecimals(
        min: Double,
        max: Double,
    ): Boolean {
        val step = (max - min) / 8

        var previous = showAmountInteger(BigDecimal(min.toString()))
        for (i in 1..7) {
            val current = showAmountInteger(BigDecimal((min + (i * step)).toString()))
            if (previous == current) {
                return true
            }
            previous = current
        }

        return false
    }

    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) { addToStatsSuspend(it) }
    }

    fun destroy() {
        bundlerInsert.cancel()
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }
}

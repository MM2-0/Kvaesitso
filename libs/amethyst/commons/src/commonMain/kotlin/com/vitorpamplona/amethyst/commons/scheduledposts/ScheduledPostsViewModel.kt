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
package com.vitorpamplona.amethyst.commons.scheduledposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A day-bucket of posts for the scheduled-posts list screen. */
data class ScheduledPostDayGroup(
    val day: SearchDate,
    val posts: List<ScheduledPost>,
)

/**
 * Drives the "Scheduled posts" screen for a single account. Filters the global
 * ScheduledPostStore down to posts owned by [accountPubkey] that are still
 * "in progress" — PENDING, PUBLISHING, or FAILED. SENT and CANCELLED rows are
 * hidden from the list (they're "done").
 */
class ScheduledPostsViewModel(
    private val store: ScheduledPostStore,
    private val accountPubkey: String,
) : ViewModel() {
    private val activeStatuses =
        setOf(
            ScheduledPostStatus.PENDING,
            ScheduledPostStatus.PUBLISHING,
            ScheduledPostStatus.FAILED,
        )

    /** Posts for [accountPubkey] in active statuses, grouped by local-day, sorted ascending. */
    val groupedPosts: StateFlow<List<ScheduledPostDayGroup>> =
        store.flow
            .map { all ->
                val dayCounter = LocalClock.epochDayCounter()
                all
                    .filter { it.accountPubkey == accountPubkey && it.status in activeStatuses }
                    .sortedBy { it.publishAtSec }
                    .groupBy { SearchDate.civilFromDays(dayCounter.epochDay(it.publishAtSec)) }
                    .map { (day, list) -> ScheduledPostDayGroup(day, list) }
                    .sortedBy { it.day }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val totalActive: StateFlow<Int> =
        groupedPosts
            .map { groups -> groups.sumOf { it.posts.size } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    fun cancel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.cancel(id)
        }
    }

    fun publishNow(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.publishNow(id)
        }
    }

    companion object {
        fun create(
            store: ScheduledPostStore,
            accountPubkey: String,
        ): ScheduledPostsViewModel =
            ScheduledPostsViewModel(
                store = store,
                accountPubkey = accountPubkey,
            )
    }
}

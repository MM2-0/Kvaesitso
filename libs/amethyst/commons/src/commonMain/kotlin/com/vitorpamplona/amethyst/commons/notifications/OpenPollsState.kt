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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.hours

@Stable
class OpenPollsState(
    account: Account,
    scope: CoroutineScope,
) {
    val filter =
        Filter(
            kinds = listOf(PollEvent.KIND, ZapPollEvent.KIND),
            authors = listOf(account.pubKey),
        )

    // Periodic ticker to re-evaluate polls after their close date passes
    private val ticker =
        flow {
            while (true) {
                emit(Unit)
                delay(1.hours)
            }
        }

    val flow: StateFlow<List<Note>> =
        combine(
            account.cache
                .observeNotes(filter),
            account.settings.dismissedPollNoteIds,
            ticker,
        ) { notes, dismissed, _ ->
            filterOpenPolls(notes).filter { it.idHex !in dismissed }
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    private fun filterOpenPolls(notes: List<Note>): List<Note> {
        val now = TimeUtils.now()
        val oneDayInSeconds = TimeUtils.ONE_DAY

        return notes
            .filter { note ->
                val event = note.event ?: return@filter false

                when (event) {
                    is PollEvent -> {
                        val endsAt = event.endsAt()
                        if (endsAt != null) endsAt > now else event.createdAt + oneDayInSeconds > now
                    }

                    is ZapPollEvent -> {
                        val closedAt = event.closedAt()
                        if (closedAt != null) closedAt > now else event.createdAt + oneDayInSeconds > now
                    }

                    else -> {
                        false
                    }
                }
            }.sortedWith(compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex })
    }
}

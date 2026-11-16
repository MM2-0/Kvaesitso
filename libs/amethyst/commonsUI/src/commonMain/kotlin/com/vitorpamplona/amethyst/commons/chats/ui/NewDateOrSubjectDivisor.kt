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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_subject_changed_by
import com.vitorpamplona.amethyst.commons.resources.chat_subject_changed_by_you
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay
import com.vitorpamplona.amethyst.commons.ui.note.chatDayLabelWith
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The quiet rows between messages: a day divider when the date changes, and a one-line caption
 * when a message renames the conversation ("Alice renamed the chat to “Product”").
 *
 * The rename used to be a bold divider holding just the new name, which read like a section
 * heading and said nothing about what happened. Now it says who did what, in caption type.
 */
@Composable
fun NewDateOrSubjectDivisor(
    previous: Note?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    NewDateDivisor(previous, note)

    // The oldest loaded message has nothing to rename from.
    if (previous == null) return

    val newSubject = remember(previous, note) { subjectChangeOf(previous.event, note.event) }
    if (newSubject != null) {
        val author = note.author
        val text =
            when {
                author == null -> newSubject
                accountViewModel.isLoggedUser(author) -> stringRes(Res.string.chat_subject_changed_by_you, newSubject)
                else -> {
                    val name by observeUserName(author, accountViewModel)
                    stringRes(Res.string.chat_subject_changed_by, name, newSubject)
                }
            }
        ChatSystemCaption(text)
    }
}

/**
 * The day header alone: [note]'s day when it differs from [previous]'s, and always above the oldest
 * loaded message ([previous] null), so every message sits under a divider naming its day. The day
 * pill and docked day bar (ChatFeedDayIndicator) hide while that divider is in view. Days are
 * calendar days ("Today", "Yesterday", "Aug 20"): a message from 11 PM last night is not "Today"
 * at 10 AM. For a row that must not
 * show a subject change (it names the author), like a collapsed run of messages from outside the
 * network.
 */
@Composable
fun NewDateDivisor(
    previous: Note?,
    note: Note,
) {
    val today = stringRes(Res.string.today)
    val yesterday = stringRes(Res.string.yesterday)

    val prevDay = remember(previous) { previous?.event?.createdAt?.let { chatDayKey(it) } }
    val day = remember(note) { note.event?.createdAt?.let { chatDayKey(it) } } ?: return

    if (prevDay != day) {
        val label = remember(note, today, yesterday) { chatDayLabelWith(note.event?.createdAt ?: 0L, TimeUtils.nowMillis(), today, yesterday) }
        ChatDivisor(label)
    }
}

/** The calendar day of a Unix timestamp, comparable between messages: year * 1000 + day of year. */
fun chatDayKey(timeSeconds: Long): Int = calendarYearAndDay(timeSeconds * 1000)

/**
 * The subject [newer] sets, or null when it sets none or repeats the one [older] already carried.
 * Some clients tag every message of a named group with its subject; without the repeat check each
 * of those would announce a rename.
 */
fun subjectChangeOf(
    older: Event?,
    newer: Event?,
): String? {
    val subject = newer?.subject()?.trim()?.ifBlank { null } ?: return null
    return if (older?.subject()?.trim() == subject) null else subject
}

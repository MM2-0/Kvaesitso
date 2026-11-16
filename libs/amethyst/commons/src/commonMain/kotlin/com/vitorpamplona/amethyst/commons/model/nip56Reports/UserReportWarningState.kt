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
package com.vitorpamplona.amethyst.commons.model.nip56Reports

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * What the DM surfaces should say about the person on the other end of a 1:1 chat.
 *
 * [reports] holds one kind-1984 note per distinct reporter — the avatars the UI renders, and what
 * the headline counts.
 */
@Immutable
data class UserReportWarningState(
    val reports: ImmutableList<Note> = persistentListOf(),
    val types: ImmutableSet<ReportType> = persistentSetOf(),
) {
    val reporterCount: Int get() = reports.size

    val shouldWarn: Boolean get() = reports.isNotEmpty()

    companion object {
        val SILENT = UserReportWarningState()
    }
}

/**
 * Decides whether to warn about [counterpart] in a 1:1 DM, and with what.
 *
 * Warns on a single report from a follow. There is deliberately no threshold here: the hide
 * threshold governs individual messages, never the chat-list row, so suppressing above it would
 * leave the most-reported senders with the least signal on the surface where the user decides
 * whether to open an unsolicited DM at all.
 *
 * [followingKeySet] serves both jobs it serves in `Account.isAcceptable(user)`: the exemption for a
 * counterpart the user follows, and the filter deciding whose reports count.
 */
fun dmReportWarningFor(
    counterpart: User,
    loggedInPubKey: HexKey,
    followingKeySet: Set<HexKey>,
    warnAboutReports: Boolean,
): UserReportWarningState {
    if (!warnAboutReports) return UserReportWarningState.SILENT
    if (counterpart.pubkeyHex == loggedInPubKey) return UserReportWarningState.SILENT
    if (counterpart.pubkeyHex in followingKeySet) return UserReportWarningState.SILENT

    val reports = counterpart.reportsOrNull()?.reportsNaming(followingKeySet).orEmpty()
    if (reports.isEmpty()) return UserReportWarningState.SILENT

    val types = LinkedHashSet<ReportType>()
    val oneReportPerReporter = LinkedHashMap<HexKey, Note>()

    reports.forEach { report ->
        report.author?.let { oneReportPerReporter.getOrPut(it.pubkeyHex) { report } }

        (report.event as? ReportEvent)?.reportedAuthor()?.forEach {
            // A single report can name several people; only this counterpart's reason belongs here.
            if (it.pubKey == counterpart.pubkeyHex) it.type?.let(types::add)
        }
    }

    return UserReportWarningState(
        reports = oneReportPerReporter.values.toImmutableList(),
        types = types.toImmutableSet(),
    )
}

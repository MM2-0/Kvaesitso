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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone

actual suspend fun IRoleBasedHttpClientBuilder.listAgentModels(key: AgentKeyTag): List<AgentModel> = throw WriterException(WriterFailure.UNSUPPORTED)

actual suspend fun IRoleBasedHttpClientBuilder.chatComplete(
    key: AgentKeyTag,
    model: String,
    messages: List<ChatMessage>,
    maxTokens: Int?,
    effort: WritingEffort,
    onEvent: (WriterEvent) -> Unit,
): Written = throw WriterException(WriterFailure.UNSUPPORTED)

actual fun paperBoundary(): PaperBoundary? = null

/*
 * iOS prints no front page yet (there is no HTML boundary there), so its clock is UTC days:
 * enough for the shared code to compile and for a stored edition to have a day.
 */
actual object FrontPageClock {
    private const val DAY = 86_400L

    actual fun dayKey(epochSec: Long): String {
        val days = (epochSec).floorDiv(DAY)
        // Civil-from-days (Howard Hinnant), so no date library is needed for one string.
        val z = days + 719_468
        val era = (z).floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    actual fun nextRun(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long {
        val today = todayAt(nowEpochSec, hour, minute)
        return if (today > nowEpochSec) today else today + DAY
    }

    actual fun todayAt(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long = (nowEpochSec).floorDiv(DAY) * DAY + hour.coerceIn(0, 23) * 3_600L + minute.coerceIn(0, 59) * 60L

    /**
     * Not known here yet: this clock reads UTC days, and writing a schedule as UTC would move the
     * reader's mornings by their offset. So iOS shows the schedule and changes nothing that needs
     * a zone (its time, a new schedule).
     */
    actual fun localZone(): String? = null

    /** Only UTC is known without a zone database; any other zone's schedule is shown, never computed. */
    actual fun cronZone(zone: String?): CronZone? = if (zone == "UTC") CronZone.UTC else null
}

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
package com.vitorpamplona.amethyst.commons.service.resourceusage

/**
 * Pure threshold logic for the "this app is consuming too much" prompt.
 * Thresholds are deliberately conservative: the prompt should fire for the
 * pathological cases (a stuck reconnect loop, process-restart churn, runaway
 * background sync) — not for a heavy day of normal use. Tune them as real
 * reports come in.
 *
 * Never auto-sends anything: a positive evaluation only ASKS the user, at
 * most once every [MIN_DAYS_BETWEEN_PROMPTS] days, and respects a permanent
 * opt-out. Both are persisted in [ResourceUsageStore].
 */
object ResourceUsageAlerts {
    enum class Reason {
        BACKGROUND_MOBILE_DATA,
        BACKGROUND_MOBILE_CONNECTION_TIME,
        WAKELOCK_TIME,
        PROCESS_CHURN,
        RECONNECT_CHURN,
    }

    data class Alert(
        val reason: Reason,
        val day: Long,
        val value: Long,
    )

    /** > 500 MB of background traffic on cellular in one day. */
    const val BG_MOBILE_BYTES_PER_DAY = 500L * 1024L * 1024L

    /** > 24 relay-connection-hours while backgrounded on cellular in one day. */
    const val BG_MOBILE_RELAY_CONN_MS_PER_DAY = 24L * 60L * 60L * 1000L

    /** > 60 minutes of notification wakelock held in one day. */
    const val WAKELOCK_MS_PER_DAY = 60L * 60L * 1000L

    /** > 150 process starts in one day (WorkManager/restart churn). */
    const val APP_STARTS_PER_DAY = 150L

    /**
     * > 10000 completed relay (re)connections in one day. A healthy day is a
     * few hundred to ~2000 even with a large relay set; sustained thousands
     * means something is cycling (a stuck relay tier, a flapping network, a
     * Tor bootstrap loop) and every cycle pays a TLS handshake.
     */
    const val RELAY_CONNECTS_PER_DAY = 10_000L

    const val MIN_DAYS_BETWEEN_PROMPTS = 7L

    /**
     * Checks yesterday (the last complete day) first, then today (so a
     * runaway condition surfaces without waiting for midnight). Returns the
     * first threshold crossed or null.
     */
    fun evaluate(
        days: Map<Long, Map<String, Long>>,
        today: Long,
    ): Alert? {
        for (day in longArrayOf(today - 1, today)) {
            val counters = days[day] ?: continue
            val summary = UsageSummary.from(counters)

            if (summary.mobileBytesBg > BG_MOBILE_BYTES_PER_DAY) {
                return Alert(Reason.BACKGROUND_MOBILE_DATA, day, summary.mobileBytesBg)
            }
            if (summary.relayConnMsMobileBg > BG_MOBILE_RELAY_CONN_MS_PER_DAY) {
                return Alert(Reason.BACKGROUND_MOBILE_CONNECTION_TIME, day, summary.relayConnMsMobileBg)
            }
            if (summary.wakelockMs > WAKELOCK_MS_PER_DAY) {
                return Alert(Reason.WAKELOCK_TIME, day, summary.wakelockMs)
            }
            if (summary.appStarts > APP_STARTS_PER_DAY) {
                return Alert(Reason.PROCESS_CHURN, day, summary.appStarts)
            }
            if (summary.relayConnects > RELAY_CONNECTS_PER_DAY) {
                return Alert(Reason.RECONNECT_CHURN, day, summary.relayConnects)
            }
        }
        return null
    }

    fun shouldPrompt(
        lastAlertAtSec: Long,
        optOut: Boolean,
        nowSec: Long,
    ): Boolean = !optOut && nowSec - lastAlertAtSec >= MIN_DAYS_BETWEEN_PROMPTS * 24L * 60L * 60L
}

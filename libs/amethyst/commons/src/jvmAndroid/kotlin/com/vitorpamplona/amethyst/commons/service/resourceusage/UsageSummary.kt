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

import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys.sumMatching

/**
 * Headline metrics derived from one or more daily counter buckets. Shared by
 * the usage screen, the NIP-17 report, and the high-consumption alerts so
 * every surface agrees on the numbers.
 */
data class UsageSummary(
    val mobileBytesBg: Long,
    val mobileBytesFg: Long,
    val wifiBytesBg: Long,
    val wifiBytesFg: Long,
    val relayConnMsMobileBg: Long,
    val relayConnMsMobileFg: Long,
    val relayConnMsWifiBg: Long,
    val relayConnMsWifiFg: Long,
    val wakelockMs: Long,
    val wakelockCount: Long,
    val workerRuns: Long,
    val appStarts: Long,
    val relayConnects: Long,
    val relayConnectFails: Long,
    val cpuMs: Long,
    val foregroundMs: Long,
    val verifyCount: Long,
    val verifyUs: Long,
    val httpRequests: Long,
    val radioBursts: Long,
    val httpActiveMs: Long,
    val mediaPlayMs: Long,
    val powMs: Long,
    val torMs: Long,
    val torStarts: Long,
    val alwaysOnMs: Long,
    val alwaysOnStarts: Long,
    val callMs: Long,
    val nestsMs: Long,
    val locationMs: Long,
    val decryptCount: Long,
    val decryptUs: Long,
    val signNip46: Long,
    val signNip55: Long,
    val batteryDrainFg: Long,
    val batteryDrainBg: Long,
    /** total rx+tx bytes per subsystem (net roles + "relay"). */
    val bytesPerSubsystem: Map<String, Long>,
    /** cellular-only rx+tx bytes per subsystem — the scarce resource. */
    val mobileBytesPerSubsystem: Map<String, Long>,
    /** foreground time per screen NAME (arguments are never recorded). */
    val screenTimeMs: Map<String, Long>,
    /** how many day buckets this summary was built from (>= 1). */
    val dayCount: Int,
) {
    val totalBytes: Long get() = mobileBytesBg + mobileBytesFg + wifiBytesBg + wifiBytesFg
    val mobileBytes: Long get() = mobileBytesBg + mobileBytesFg
    val relayConnMs: Long get() = relayConnMsMobileBg + relayConnMsMobileFg + relayConnMsWifiBg + relayConnMsWifiFg

    companion object {
        fun from(
            counters: Map<String, Long>,
            dayCount: Int = 1,
        ): UsageSummary {
            fun traffic(
                net: String,
                vis: String,
            ) = counters.sumMatching(net, vis, UsageKeys.RX) + counters.sumMatching(net, vis, UsageKeys.TX)

            val subsystems = mutableMapOf<String, Long>()
            val mobileSubsystems = mutableMapOf<String, Long>()
            for (role in UsageKeys.HTTP_ROLES) {
                val bytes = counters.sumMatching(role, UsageKeys.RX) + counters.sumMatching(role, UsageKeys.TX)
                if (bytes > 0) subsystems[role] = bytes
                val mobileBytes =
                    counters.sumMatching(role, UsageKeys.MOBILE, UsageKeys.RX) +
                        counters.sumMatching(role, UsageKeys.MOBILE, UsageKeys.TX)
                if (mobileBytes > 0) mobileSubsystems[role] = mobileBytes
            }
            val relayBytes = counters.sumMatching("msg", UsageKeys.RX) + counters.sumMatching("msg", UsageKeys.TX)
            if (relayBytes > 0) subsystems["relay"] = relayBytes
            val mobileRelayBytes =
                counters.sumMatching("msg", UsageKeys.MOBILE, UsageKeys.RX) +
                    counters.sumMatching("msg", UsageKeys.MOBILE, UsageKeys.TX)
            if (mobileRelayBytes > 0) mobileSubsystems["relay"] = mobileRelayBytes

            val screens = mutableMapOf<String, Long>()
            for ((key, value) in counters) {
                if (key.startsWith(UsageKeys.SCREEN_PREFIX) && key.endsWith(".ms") && value > 0) {
                    val name = key.removePrefix(UsageKeys.SCREEN_PREFIX).removeSuffix(".ms")
                    if (name.isNotBlank()) screens[name] = (screens[name] ?: 0L) + value
                }
            }

            return UsageSummary(
                mobileBytesBg = traffic(UsageKeys.MOBILE, UsageKeys.BG),
                mobileBytesFg = traffic(UsageKeys.MOBILE, UsageKeys.FG),
                wifiBytesBg = traffic(UsageKeys.WIFI, UsageKeys.BG),
                wifiBytesFg = traffic(UsageKeys.WIFI, UsageKeys.FG),
                relayConnMsMobileBg = counters.sumMatching("connms", UsageKeys.MOBILE, UsageKeys.BG),
                relayConnMsMobileFg = counters.sumMatching("connms", UsageKeys.MOBILE, UsageKeys.FG),
                relayConnMsWifiBg = counters.sumMatching("connms", UsageKeys.WIFI, UsageKeys.BG),
                relayConnMsWifiFg = counters.sumMatching("connms", UsageKeys.WIFI, UsageKeys.FG),
                wakelockMs = counters[UsageKeys.WAKELOCK_NOTIF_MS] ?: 0L,
                wakelockCount = counters[UsageKeys.WAKELOCK_NOTIF_COUNT] ?: 0L,
                workerRuns = counters.sumMatching("worker", "runs"),
                appStarts = counters[UsageKeys.APP_STARTS] ?: 0L,
                relayConnects = counters.sumMatching("connects"),
                relayConnectFails = counters.sumMatching("connfails"),
                cpuMs = counters[UsageKeys.CPU_MS] ?: 0L,
                foregroundMs = counters[UsageKeys.APP_FG_MS] ?: 0L,
                verifyCount = counters[UsageKeys.VERIFY_COUNT] ?: 0L,
                verifyUs = counters[UsageKeys.VERIFY_US] ?: 0L,
                httpRequests = counters.sumMatching("reqs"),
                radioBursts = counters.sumMatching("bursts"),
                httpActiveMs = counters.sumMatching("activems"),
                mediaPlayMs = counters[UsageKeys.MEDIA_PLAY_MS] ?: 0L,
                powMs = counters[UsageKeys.POW_MS] ?: 0L,
                torMs = counters[UsageKeys.TOR_MS] ?: 0L,
                torStarts = counters[UsageKeys.TOR_STARTS] ?: 0L,
                alwaysOnMs = counters[UsageKeys.ALWAYS_ON_MS] ?: 0L,
                alwaysOnStarts = counters[UsageKeys.ALWAYS_ON_STARTS] ?: 0L,
                callMs = counters[UsageKeys.CALL_MS] ?: 0L,
                nestsMs = counters[UsageKeys.NESTS_MS] ?: 0L,
                locationMs = counters[UsageKeys.LOCATION_MS] ?: 0L,
                decryptCount = counters[UsageKeys.DECRYPT_COUNT] ?: 0L,
                decryptUs = counters[UsageKeys.DECRYPT_US] ?: 0L,
                signNip46 = counters[UsageKeys.signs(UsageKeys.SIGNER_NIP46)] ?: 0L,
                signNip55 = counters[UsageKeys.signs(UsageKeys.SIGNER_NIP55)] ?: 0L,
                batteryDrainFg = counters[UsageKeys.BATTERY_DRAIN_FG] ?: 0L,
                batteryDrainBg = counters[UsageKeys.BATTERY_DRAIN_BG] ?: 0L,
                bytesPerSubsystem = subsystems,
                mobileBytesPerSubsystem = mobileSubsystems,
                screenTimeMs = screens,
                dayCount = dayCount.coerceAtLeast(1),
            )
        }

        /** Merges several day buckets and summarizes the total. */
        fun fromDays(days: Collection<Map<String, Long>>): UsageSummary {
            val merged = mutableMapOf<String, Long>()
            days.forEach { day -> day.forEach { (k, v) -> merged[k] = (merged[k] ?: 0L) + v } }
            return from(merged, dayCount = days.size)
        }
    }
}

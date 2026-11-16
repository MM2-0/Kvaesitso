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

/**
 * Samples the device battery level at each ledger flush and accumulates the
 * drops that happen while discharging into [UsageKeys.BATTERY_DRAIN_FG] /
 * [UsageKeys.BATTERY_DRAIN_BG] (percent points, attributed by visibility at
 * sample time). This is deliberately NOT app-isolated — it's the measured
 * ground truth that lets the developers correlate the app's own counters
 * against real drain across many reports, which is how the alert thresholds
 * get tuned.
 *
 * Intervals touching a charging state (at either endpoint) are skipped, and
 * a level that went UP just resets the baseline. Piggybacks on the pre-flush
 * hook — one BatteryManager binder read per flush, nothing while idle.
 */
class BatteryDrainSampler(
    private val accountant: ResourceUsageAccountant,
    private val capacityPct: () -> Int?,
    private val isCharging: () -> Boolean,
    private val isForeground: () -> Boolean,
) {
    private var lastPct: Int? = null
    private var lastCharging = true

    fun register() {
        accountant.addPreFlushHook(::sample)
    }

    @Synchronized
    fun sample() {
        val pct = capacityPct() ?: return
        val charging = isCharging()
        val prevPct = lastPct
        val prevCharging = lastCharging
        lastPct = pct
        lastCharging = charging

        if (prevPct == null || prevCharging || charging) return
        val drop = prevPct - pct
        if (drop <= 0) return
        accountant.add(
            if (isForeground()) UsageKeys.BATTERY_DRAIN_FG else UsageKeys.BATTERY_DRAIN_BG,
            drop.toLong(),
        )
    }
}

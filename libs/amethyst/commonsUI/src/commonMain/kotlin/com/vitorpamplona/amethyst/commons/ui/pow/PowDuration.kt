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
package com.vitorpamplona.amethyst.commons.ui.pow

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pow_estimate_days
import com.vitorpamplona.amethyst.commons.resources.pow_estimate_hours
import com.vitorpamplona.amethyst.commons.resources.pow_estimate_instant
import com.vitorpamplona.amethyst.commons.resources.pow_estimate_minutes
import com.vitorpamplona.amethyst.commons.resources.pow_estimate_seconds
import com.vitorpamplona.amethyst.commons.resources.pow_time_left
import com.vitorpamplona.amethyst.commons.resources.pow_time_left_soon
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.service.pow.PoWRateEstimate
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.PluralStringResource
import kotlin.math.roundToLong

/**
 * This device's effective mining rate: while a post mines, the rate its miners
 * actually reach; otherwise the [PoWEstimator] benchmark run with the worker
 * count the mining queue uses ([platformMinerWorkers]: half the cores on a phone,
 * all but two on the desktop), cached after the first run (~250 ms). Every UI
 * estimate must use this rate, or it would describe a miner that does not run.
 */
suspend fun deviceRate(): PoWRateEstimate = PoWEstimator.currentRate(platformMinerWorkers())

/** [deviceRate] for a typical short note, where no event exists yet (settings, composer). */
suspend fun deviceHashesPerSecond(): Double = deviceRate().hashesPerSecond()

/**
 * "45 seconds" / "10 minutes" / "3 hours" — the one human-readable rendering
 * of a PoW duration estimate, shared by the settings picker, the composer
 * difficulty menu, the mining banner, and the mining notification.
 *
 * Estimates come from [com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator]
 * and are the statistical mean of a memoryless search — any single post can be
 * much luckier or unluckier, so always present these as approximations.
 */
@Composable
fun formatApproxDuration(seconds: Double): String {
    @Composable
    fun quantity(
        id: PluralStringResource,
        count: Long,
    ) = pluralStringRes(id, count.toInt(), count.toInt())

    return when {
        seconds < 1.0 -> stringRes(Res.string.pow_estimate_instant)
        seconds < 90.0 -> quantity(Res.plurals.pow_estimate_seconds, seconds.roundToLong())
        seconds < 90.0 * 60.0 -> quantity(Res.plurals.pow_estimate_minutes, (seconds / 60.0).roundToLong())
        seconds < 48.0 * 3600.0 -> quantity(Res.plurals.pow_estimate_hours, (seconds / 3600.0).roundToLong())
        else -> quantity(Res.plurals.pow_estimate_days, (seconds / 86400.0).roundToLong())
    }
}

/**
 * "≈ 10 minutes left" while [elapsedSec] is inside the [expectedSec] mean,
 * "any moment now" once past it — a memoryless search has no shrinking
 * remainder, so past the mean the only honest claim is "soon".
 */
@Composable
fun formatTimeLeft(
    expectedSec: Double,
    elapsedSec: Long,
): String {
    val remaining = expectedSec - elapsedSec
    return if (remaining > 1.0) {
        stringRes(Res.string.pow_time_left, formatApproxDuration(remaining))
    } else {
        stringRes(Res.string.pow_time_left_soon)
    }
}

/** Suspend twin of [formatApproxDuration] for the mining foreground service,
 *  which builds its notification text outside composition. */
suspend fun loadApproxDuration(seconds: Double): String {
    suspend fun quantity(
        id: PluralStringResource,
        count: Long,
    ) = loadPluralStringRes(id, count.toInt(), count.toInt())

    return when {
        seconds < 1.0 -> loadStringRes(Res.string.pow_estimate_instant)
        seconds < 90.0 -> quantity(Res.plurals.pow_estimate_seconds, seconds.roundToLong())
        seconds < 90.0 * 60.0 -> quantity(Res.plurals.pow_estimate_minutes, (seconds / 60.0).roundToLong())
        seconds < 48.0 * 3600.0 -> quantity(Res.plurals.pow_estimate_hours, (seconds / 3600.0).roundToLong())
        else -> quantity(Res.plurals.pow_estimate_days, (seconds / 86400.0).roundToLong())
    }
}

/** Suspend twin of [formatTimeLeft]; see [loadApproxDuration]. */
suspend fun loadTimeLeft(
    expectedSec: Double,
    elapsedSec: Long,
): String {
    val remaining = expectedSec - elapsedSec
    return if (remaining > 1.0) {
        loadStringRes(Res.string.pow_time_left, loadApproxDuration(remaining))
    } else {
        loadStringRes(Res.string.pow_time_left_soon)
    }
}

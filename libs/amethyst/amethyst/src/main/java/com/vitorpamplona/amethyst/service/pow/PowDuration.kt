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
package com.vitorpamplona.amethyst.service.pow

import android.content.Context
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes
import kotlin.math.roundToLong

/**
 * Android-resource twin of [formatApproxDuration]; see [powKindLabelResId] for why
 * the mining notification cannot read from the Compose catalog.
 */
fun formatApproxDuration(
    context: Context,
    seconds: Double,
): String {
    fun quantity(
        id: Int,
        count: Long,
    ) = pluralStringRes(context, id, count.toInt(), count.toInt())

    return when {
        seconds < 1.0 -> stringRes(context, R.string.pow_estimate_instant)
        seconds < 90.0 -> quantity(R.plurals.pow_estimate_seconds, seconds.roundToLong())
        seconds < 90.0 * 60.0 -> quantity(R.plurals.pow_estimate_minutes, (seconds / 60.0).roundToLong())
        seconds < 48.0 * 3600.0 -> quantity(R.plurals.pow_estimate_hours, (seconds / 3600.0).roundToLong())
        else -> quantity(R.plurals.pow_estimate_days, (seconds / 86400.0).roundToLong())
    }
}

/** Android-resource twin of [formatTimeLeft]. */
fun formatTimeLeft(
    context: Context,
    expectedSec: Double,
    elapsedSec: Long,
): String {
    val remaining = expectedSec - elapsedSec
    return if (remaining > 1.0) {
        stringRes(context, R.string.pow_time_left, formatApproxDuration(context, remaining))
    } else {
        stringRes(context, R.string.pow_time_left_soon)
    }
}

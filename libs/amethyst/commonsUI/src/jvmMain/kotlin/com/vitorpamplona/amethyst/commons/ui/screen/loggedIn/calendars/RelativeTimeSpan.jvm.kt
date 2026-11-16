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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.d
import com.vitorpamplona.amethyst.commons.resources.h
import com.vitorpamplona.amethyst.commons.resources.m
import com.vitorpamplona.amethyst.commons.resources.now
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.math.abs

/** The JDK has no relative-time formatter: a compact signed span ("+2h", "-5m"). */
actual suspend fun relativeTimeSpan(
    targetSeconds: Long,
    nowSeconds: Long,
    dayResolution: Boolean,
): String {
    val diff = targetSeconds - nowSeconds
    val span = abs(diff)
    val sign = if (diff < 0) "-" else "+"
    return when {
        span >= TimeUtils.ONE_DAY -> sign + (span / TimeUtils.ONE_DAY) + loadStringRes(Res.string.d)
        dayResolution -> loadStringRes(Res.string.now)
        span >= TimeUtils.ONE_HOUR -> sign + (span / TimeUtils.ONE_HOUR) + loadStringRes(Res.string.h)
        span >= TimeUtils.ONE_MINUTE -> sign + (span / TimeUtils.ONE_MINUTE) + loadStringRes(Res.string.m)
        else -> loadStringRes(Res.string.now)
    }
}

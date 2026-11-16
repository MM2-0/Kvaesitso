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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.quartz.utils.TimeUtils

fun timeDiffAgoLong(timeDifference: Int): String =
    when {
        timeDifference > TimeUtils.ONE_YEAR -> (timeDifference / TimeUtils.ONE_YEAR).toString() + " years"
        timeDifference > TimeUtils.ONE_MONTH -> (timeDifference / TimeUtils.ONE_MONTH).toString() + " months"
        timeDifference > TimeUtils.ONE_DAY -> (timeDifference / TimeUtils.ONE_DAY).toString() + " days"
        timeDifference > TimeUtils.ONE_HOUR -> (timeDifference / TimeUtils.ONE_HOUR).toString() + " hours"
        timeDifference > TimeUtils.ONE_MINUTE -> (timeDifference / TimeUtils.ONE_MINUTE).toString() + " minutes"
        else -> "now"
    }

fun timeDiffAgoShortish(timeDifference: Int): String =
    when {
        timeDifference > TimeUtils.ONE_YEAR -> (timeDifference / TimeUtils.ONE_YEAR).toString() + " yrs"
        timeDifference > TimeUtils.ONE_MONTH -> (timeDifference / TimeUtils.ONE_MONTH).toString() + " mos"
        timeDifference > TimeUtils.ONE_DAY -> (timeDifference / TimeUtils.ONE_DAY).toString() + " days"
        timeDifference > TimeUtils.ONE_HOUR -> (timeDifference / TimeUtils.ONE_HOUR).toString() + " hrs"
        timeDifference > TimeUtils.ONE_MINUTE -> (timeDifference / TimeUtils.ONE_MINUTE).toString() + " mins"
        else -> "now"
    }

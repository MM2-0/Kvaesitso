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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

// Format pattern matches the JVM actual ("uuuu-MM-dd-HH:mm:ss"). For the
// post-1970 timestamps Nostr events use, `yyyy` and `uuuu` are equivalent
// (proleptic Gregorian, year >= 1), so NSDateFormatter's `yyyy` is fine.
private val levelFormatter: NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd-HH:mm:ss"
        timeZone = NSTimeZone.localTimeZone
        locale = NSLocale("en_US_POSIX")
    }

// NSDateFormatter is not thread-safe for concurrent stringFromDate calls
// (Apple docs). ThreadLevelCalculator.replyLevelSignature runs under
// Dispatchers.IO via LevelFeedViewModel, so multiple threads can hit
// formattedDateTime simultaneously. Guard the shared formatter rather than
// allocating one per call (cheaper for the O(notes-per-thread-sort) call rate).
private val levelFormatterLock = KmpLock()

actual fun formattedDateTime(timestamp: Long): String =
    levelFormatterLock.withLock {
        levelFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble()))
    }

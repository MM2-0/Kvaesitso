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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.runtime.Immutable

/** When a poll being written stops taking votes. Polls and zap polls share it. */
@Immutable
sealed interface PollDeadline {
    /** Seconds since the epoch the poll closes at if it is sent [now]; null when it never closes. */
    fun closesAt(now: Long): Long?

    data object Never : PollDeadline {
        override fun closesAt(now: Long): Long? = null
    }

    /** Open for [seconds] counted from when the poll is sent, not from when the length was picked. */
    data class After(
        val seconds: Long,
    ) : PollDeadline {
        override fun closesAt(now: Long): Long = now + seconds
    }

    /** A date and time picked in the calendar, or read back from a draft. */
    data class At(
        val epochSeconds: Long,
    ) : PollDeadline {
        override fun closesAt(now: Long): Long = epochSeconds
    }
}

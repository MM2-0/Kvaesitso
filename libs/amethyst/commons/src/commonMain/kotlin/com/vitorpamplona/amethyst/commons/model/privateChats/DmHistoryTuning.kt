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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.Volatile

/**
 * One shared boundary for the DM history window, so the three things that must agree on "where the
 * live tail ends and paged history begins" actually read the same number:
 *  - the live-tail subscriptions' `since` floor (e.g. `AccountGiftWrapsEoseManager`),
 *  - the backward history pager's pinned floor (`BackwardRelayPager`),
 *  - the memory prune's recent/old split + retention cap (`Chatroom.pruneMessagesToTheLatestOnly`).
 *
 * Keeping them in one place avoids the live tail and history overlapping (double-loading) or leaving a
 * gap when the boundary is tuned. The knobs are plain `@Volatile` vars (overridable once at startup —
 * e.g. shrinking the window in a test to exercise pruning + the per-relay download-window realignment
 * without needing week-old threads); they are not meant to change mid-session.
 */
object DmHistoryTuning {
    /** Seconds below "now" where the live tail ends and paged history begins. Production: one week. */
    @Volatile
    var liveTailSeconds: Long = 7L * TimeUtils.ONE_DAY

    /** How many newest messages a still-recent conversation keeps on a prune. Production: 100. */
    @Volatile
    var recentKeepCount: Int = 100

    /** The epoch-seconds boundary `now − [liveTailSeconds]` (recomputed each call against the clock). */
    fun recentBoundary(): Long = TimeUtils.now() - liveTailSeconds
}

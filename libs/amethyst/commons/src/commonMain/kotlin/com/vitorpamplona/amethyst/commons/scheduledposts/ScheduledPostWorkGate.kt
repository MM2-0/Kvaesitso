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
package com.vitorpamplona.amethyst.commons.scheduledposts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps [ScheduledPostWorker]'s 15-minute periodic chain enqueued exactly while
 * the store holds a PENDING post. An unconditionally-scheduled periodic worker
 * wakes — and often cold-starts — the whole process every 15 minutes forever,
 * even for users who never schedule a post.
 *
 * The store is durable (JSON on disk) and the single source of truth, so
 * [onPendingWork] fires from any mutation that produces a PENDING row (add,
 * publishNow, releaseClaim) and [onNoPendingWork] when the last one drains
 * (markSent/markFailed/cancel/removeForAccount).
 *
 * PUBLISHING counts as pending work: claimDuePosts flips PENDING → PUBLISHING
 * (and emits) BEFORE the worker publishes, so gating on PENDING alone would
 * cancel the periodic worker mid-publish the moment it claims the last post —
 * stranding the post in PUBLISHING with nothing left to finish or retry it.
 *
 * [start] forces the store's initial disk load BEFORE collecting: the flow's
 * initial value is an empty list until the store is first touched, and acting
 * on that placeholder would cancel scheduled work that a pending post still
 * needs.
 */
class ScheduledPostWorkGate(
    private val store: ScheduledPostStore,
    private val scope: CoroutineScope,
    private val onPendingWork: () -> Unit,
    private val onNoPendingWork: () -> Unit,
) {
    fun start(): Job =
        scope.launch {
            store.list()
            store.flow
                .map { posts -> posts.any { it.status == ScheduledPostStatus.PENDING || it.status == ScheduledPostStatus.PUBLISHING } }
                .distinctUntilChanged()
                .collect { hasPending ->
                    if (hasPending) {
                        onPendingWork()
                    } else {
                        onNoPendingWork()
                    }
                }
        }
}

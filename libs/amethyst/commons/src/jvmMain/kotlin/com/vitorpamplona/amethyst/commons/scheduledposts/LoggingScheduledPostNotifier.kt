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

import com.vitorpamplona.quartz.utils.Log

/**
 * Desktop/JVM [ScheduledPostNotifier] that only logs. Desktop has no system
 * notification channel wired for scheduled posts yet; this keeps the publish
 * loop functional and observable until one is added.
 */
class LoggingScheduledPostNotifier : ScheduledPostNotifier {
    override suspend fun notifySent(post: ScheduledPost) {
        Log.i(TAG) { "Scheduled post ${post.id} sent" }
    }

    override suspend fun notifyFailed(
        post: ScheduledPost,
        error: String?,
    ) {
        Log.w(TAG) { "Scheduled post ${post.id} failed: ${error ?: "unknown error"}" }
    }

    companion object {
        private const val TAG = "ScheduledPostNotifier"
    }
}

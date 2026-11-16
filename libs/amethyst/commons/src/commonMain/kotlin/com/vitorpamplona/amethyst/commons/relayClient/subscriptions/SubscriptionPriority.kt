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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

/**
 * Priority levels for relay subscriptions.
 * Lower order = higher priority = processed first.
 *
 * Priority order ensures progressive rendering:
 * 1. METADATA - Display names, avatars load first
 * 2. REACTIONS - Like/zap counts load second
 * 3. REPLIES - Reply counts
 * 4. CONTENT - Additional content
 */
enum class SubscriptionPriority(
    val order: Int,
) {
    /** User metadata (Kind 0) - display names, avatars. Highest priority. */
    METADATA(1),

    /** Reactions (Kind 7) - likes, zaps, reposts */
    REACTIONS(2),

    /** Replies - reply counts and threads */
    REPLIES(3),

    /** Additional content - lower priority items */
    CONTENT(4),
    ;

    companion object {
        /**
         * Get priorities sorted by order (highest priority first).
         */
        fun sortedByPriority(): List<SubscriptionPriority> = entries.sortedBy { it.order }
    }
}

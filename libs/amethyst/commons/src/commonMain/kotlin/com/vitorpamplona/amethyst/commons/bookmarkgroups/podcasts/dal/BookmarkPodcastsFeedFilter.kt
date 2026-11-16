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
package com.vitorpamplona.amethyst.commons.bookmarkgroups.podcasts.dal

import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.isPodcastEvent

/**
 * The podcast subset of the user's NIP-51 bookmark list (kind 10003): podcasts (shows and episodes)
 * are bookmarked into the same general list as everything else, so this filter pulls just the
 * podcast-typed notes ([isPodcastEvent]) back out — both public and private — newest first.
 */
class BookmarkPodcastsFeedFilter(
    val account: Account,
) : FeedFilter<Note>() {
    override fun feedKey(): String =
        account.bookmarkState.bookmarks.value
            .hashCode()
            .toString()

    override fun feed(): List<Note> {
        val bookmarks = account.bookmarkState.bookmarks.value
        return (bookmarks.public + bookmarks.private)
            .filter { isPodcastEvent(it.event) }
            .distinct()
            .sortedByDescending { it.createdAt() ?: 0L }
    }
}

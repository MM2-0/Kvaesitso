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
package com.vitorpamplona.amethyst.commons.nipCCGeocaching.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent

/**
 * The addresses logged as found in a Finds feed ([feedState]), as `37516:pubkey:d` strings.
 * Empty until the feed has loaded.
 *
 * Deriving it from the feed rather than scanning the cache directly means it tracks a new log
 * the moment the feed does, which is what makes a hunt's progress bar move as you walk it.
 */
@Composable
fun rememberFoundCacheIds(feedState: FeedState): Set<String> =
    when (feedState) {
        is FeedState.Loaded -> rememberLoadedFoundCacheIds(feedState)
        else -> emptySet()
    }

/**
 * Collects the loaded feed's own flow. [FeedState.Loaded] is handed out once and then mutated
 * through its inner flow, so reading `.value` here would freeze the set at whatever the feed held
 * when the hunt screen first composed — the progress bar would never move.
 */
@Composable
private fun rememberLoadedFoundCacheIds(state: FeedState.Loaded): Set<String> {
    val loaded by state.feed.collectAsStateWithLifecycle()

    return remember(loaded) {
        loaded.list.mapNotNullTo(mutableSetOf()) { (it.event as? GeocacheFoundLogEvent)?.geocacheId() }
    }
}

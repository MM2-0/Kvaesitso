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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Per-subscription watchers for a subscription manager whose filters read some Settings › Messages
 * toggles ([types]): while a subscription is open, every flip of any of them — as counted by the account's
 * [com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles.flips], i.e. once the rooms have
 * caught up — calls [onToggle], including an off-and-on too quick for the enabled set to show.
 * [BaseEoseManager] owns one, so a manager only declares which toggles it reads (`watchedChatFeeds`)
 * and the base classes start and stop the watchers with each subscription.
 */
class ChatFeedWatchers(
    private val types: Set<ChatFeedType>,
    private val onToggle: () -> Unit,
) {
    // Filters are rebuilt from whichever thread invalidated them, so two builds of one subscription can
    // race here; unguarded, both would launch a watcher and the loser would never be cancelled.
    private val lock = KmpLock()
    private val jobs = mutableMapOf<Any, Job>()

    /**
     * Starts watching for [subKey] unless already watching. Call it *before* building that
     * subscription's filters: the baseline is read here, synchronously, so a toggle applied between the
     * filter build and the watcher's first collection is still seen as a change.
     */
    fun ensure(
        subKey: Any,
        account: IAccount,
    ) {
        if (types.isEmpty()) return
        // Only a full Account has toggles; other IAccount implementations (the legacy desktop one) don't.
        val owner = account as? Account ?: return
        val toggles = owner.chatFeedToggles
        lock.withLock {
            if (jobs[subKey]?.isActive == true) return
            // Flip counts, not the enabled set: an off-and-on that conflates to the same set still reset
            // the cursors this subscription pages with (see ChatFeedToggles.flips).
            val baseline = toggles.flips.value.filterKeys { it in types }
            jobs[subKey] =
                owner.scope.launch(Dispatchers.IO) {
                    toggles.flips
                        .map { counts -> counts.filterKeys { it in types } }
                        .distinctUntilChanged()
                        .dropWhile { it == baseline }
                        .collect { onToggle() }
                }
        }
    }

    fun stop(subKey: Any) {
        lock.withLock { jobs.remove(subKey) }?.cancel()
    }
}

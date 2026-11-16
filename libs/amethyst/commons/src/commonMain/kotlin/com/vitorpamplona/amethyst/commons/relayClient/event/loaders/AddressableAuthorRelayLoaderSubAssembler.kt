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
package com.vitorpamplona.amethyst.commons.relayClient.event.loaders

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.IEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Bridges missing-addressable-note authors into [UserFinderFilterAssembler].
 *
 * When an [AddressableNote] stub (event == null) references an author whose NIP-65 relay list
 * has not been loaded yet, [potentialRelaysToFindAddress] returns an empty set and the event
 * is never fetched. This manager detects those cases and injects a [UserFinderQueryState] for
 * the author into the existing [UserFinderFilterAssembler], which already knows how to fetch
 * kind-0 / kind-10002 and resolve outbox relays via [UserOutboxFinderSubAssembler]. Once the
 * relay list arrives, [EventFinderFilterAssembler] is invalidated and can query the correct relay.
 */
class AddressableAuthorRelayLoaderSubAssembler(
    val cache: ICacheProvider,
    val allKeys: () -> Set<EventFinderQueryState>,
    val userFinder: UserFinderFilterAssembler,
) : IEoseManager {
    // Private monitor: @Synchronized locks on `this`, which leaves the instance's monitor
    // reachable to anything holding a reference to this assembler. KmpLock (not `synchronized`)
    // because this file lives in commonMain and must compile for the iOS targets too.
    private val lock = KmpLock()

    // Only ever touched while holding [lock]. See commit() and destroy().
    private var activeSubscriptions: Set<UserFinderQueryState> = emptySet()
    private var destroyed = false

    // Keeps the scan off the caller's thread. invalidateFilters() is reached synchronously from
    // ComposeSubscriptionManager.subscribe/unsubscribe on every note composable mount/unmount,
    // and those are documented "called by main. Keep it really fast."
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing, ::forceInvalidate)
    }

    private fun forceInvalidate() {
        val needed = mutableSetOf<UserFinderQueryState>()

        allKeys().forEach { key ->
            val note = key.note
            if (note is AddressableNote && note.event == null) {
                val author = cache.getOrCreateUser(note.address.pubKeyHex)
                if (author != null && author.authorRelayList() == null) {
                    needed.add(UserFinderQueryState(author, key.account))
                }
            }
        }

        commit(needed)
    }

    /**
     * Serializes against [destroy] — the one caller the bundler cannot order, because
     * `bundler.cancel()` cannot stop a body that is already running (it has no suspension points).
     *
     * The scan in [forceInvalidate] stays outside [lock], so [destroy] never waits on a
     * [LocalCache] sweep. It can still wait on the two calls below, which are bounded: a pair of
     * map updates inside [UserFinderFilterAssembler] plus the coroutine launches its
     * `invalidateKeys()` fans out to.
     *
     * Calling [userFinder] while holding [lock] relies on subscribe/unsubscribe only taking
     * ComposeSubscriptionManager's own lock and deferring real work to bundled coroutines — they
     * never call back into this class. Revisit if that changes.
     */
    private fun commit(needed: Set<UserFinderQueryState>) {
        lock.withLock {
            if (destroyed) return

            userFinder.subscribe((needed - activeSubscriptions).toList())
            userFinder.unsubscribe((activeSubscriptions - needed).toList())

            activeSubscriptions = needed
        }
    }

    override fun destroy() {
        lock.withLock {
            destroyed = true
            bundler.cancel()
            userFinder.unsubscribe(activeSubscriptions.toList())
            activeSubscriptions = emptySet()
        }
    }
}

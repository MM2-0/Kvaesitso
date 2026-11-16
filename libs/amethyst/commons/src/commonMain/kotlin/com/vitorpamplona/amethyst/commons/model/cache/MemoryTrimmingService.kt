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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex

/**
 * Prunes [cache] when the app needs memory back. Android runs it on the OS trim callbacks (and
 * its own heap watchdog); the desktop, which gets no such callback, from a heap watchdog alone.
 */
class MemoryTrimmingService(
    val cache: LocalCache,
) {
    private val trimming = Mutex()

    /**
     * Two tiers.
     *
     * Tier 1 — always (on Android, every app switch):
     *   Sweep stale WeakRefs, drop expired and superseded-replaceable events.
     *   Safe to run frequently; no UI-visible side effects.
     *
     * Tier 2 — [underPressure], real reclaim pressure:
     *   Tier 1 + drop events from muted/blocked users + old chat messages +
     *   unobserved thread replies / reactions. May cause feeds to re-fetch content
     *   that was scrolled past; triggers recomposition wherever StateFlows cleared.
     */
    private fun doTrim(
        account: Collection<Account>,
        otherAccounts: List<AccountInfo>,
        underPressure: Boolean,
    ) {
        // Tier 1: always run — cheap housekeeping; cleanObservers only removes flows that are
        // not currently held by the UI, so it is safe and inexpensive at any pressure level.
        cache.pruner.cleanMemory()
        cache.pruner.cleanObservers()
        cache.pruner.pruneExpiredEvents()
        cache.pruner.prunePastVersionsOfReplaceables()

        if (underPressure) {
            // Tier 2: real reclaim pressure — drop events from muted/blocked users, old
            // messages, and unobserved reactions.
            account.forEach {
                cache.pruner.pruneHiddenEvents(it.hiddenUsers.flow.value)
                cache.pruner.pruneHiddenMessages(it)
            }
            val accounts = otherAccounts.mapNotNull { decodePublicKeyAsHexOrNull(it.npub) }.toSet()
            cache.pruner.pruneOldMessages()
            cache.pruner.pruneRepliesAndReactions(accounts)
        }
    }

    /** Prunes, unless a trim is already running. */
    suspend fun run(
        account: Collection<Account>,
        otherAccounts: List<AccountInfo>,
        underPressure: Boolean = true,
    ) {
        if (trimming.tryLock()) {
            Log.d("MemoryTrimmingService") { "Trimming memory (underPressure=$underPressure)" }
            try {
                doTrim(account, otherAccounts, underPressure)
            } finally {
                trimming.unlock()
            }
        }
    }
}

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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/** Result of attempting to remove a relay from every list it currently appears in. */
sealed interface RelayRemovalResult {
    /** All targeted lists were successfully edited + broadcast (or had no entry to remove). */
    data object Success : RelayRemovalResult

    /** Some lists succeeded, others failed (e.g. signer timeout). */
    data class Partial(
        val failedLists: Set<RelayListKind>,
    ) : RelayRemovalResult

    /** Nothing could be persisted (account locked, signer unavailable, etc.). */
    data class Failure(
        val message: String?,
    ) : RelayRemovalResult
}

/**
 * Platform-specific: edits the user's NIP-65-style relay lists and broadcasts the new versions.
 *
 * Android impl delegates to Account.send*RelayList methods.
 * Desktop impl delegates to commons *State.saveRelayList + relayManager.broadcastToAll.
 *
 * `removeFromAllUserLists` must issue sign requests in parallel (multi-list users on a slow
 * NIP-46 bunker otherwise wait N*RTT).
 */
interface RelayListMutator {
    suspend fun removeFromAllUserLists(url: NormalizedRelayUrl): RelayRemovalResult
}

/** No-op for previews/tests/headless hosts that don't actually mutate. */
class NoopRelayListMutator : RelayListMutator {
    override suspend fun removeFromAllUserLists(url: NormalizedRelayUrl): RelayRemovalResult = RelayRemovalResult.Success
}

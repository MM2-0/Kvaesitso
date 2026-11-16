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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-viewer set of **hidden** Buzz DM channels, fed by the relay-signed per-viewer DM Visibility
 * snapshot ([DmVisibilityEvent], `kind:30622`) as `LocalCache` consumes it.
 *
 * DM *discovery* itself doesn't live here: the deployed relay enumerates a member's channels via
 * kind-44100 member-added notifications and marks DMs with the `t` tag on their kind-39000
 * metadata (it does not emit a queryable kind-41001), so the DM inbox reads those directly. This
 * registry only tracks which of those a given viewer has hidden, so a hidden DM drops out until
 * it's re-opened.
 *
 * Hidden sets are kept per-viewer because the 30622 snapshot is `#p`-gated to its owner and the
 * process can switch accounts. Mutations are lock-guarded because consume runs across several relay
 * reader threads. Like [BuzzRelayDialect] / `BuzzWorkspaces`, a process-wide singleton.
 */
object BuzzDmRegistry {
    private val lock = KmpLock()
    private val hiddenByViewer = HashMap<HexKey, Set<String>>()
    private val mutableHidden = MutableStateFlow<Map<HexKey, Set<String>>>(emptyMap())

    /** Per-viewer hidden-DM channel ids; the DM inbox collects this to filter itself. */
    val hidden: StateFlow<Map<HexKey, Set<String>>> = mutableHidden

    /** Replaces [viewer]'s hidden-DM set with [channelIds] (the whole 30622 snapshot). */
    fun recordHidden(
        viewer: HexKey,
        channelIds: Set<String>,
    ) = lock.withLock {
        if (hiddenByViewer[viewer] == channelIds) return@withLock
        if (channelIds.isEmpty()) {
            hiddenByViewer.remove(viewer)
        } else {
            hiddenByViewer[viewer] = channelIds
        }
        mutableHidden.value = hiddenByViewer.toMap()
    }

    /** The channel ids [viewer] has hidden (possibly empty). */
    fun hiddenFor(viewer: HexKey): Set<String> = mutableHidden.value[viewer] ?: emptySet()

    /** Test-only: clears all registry state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            hiddenByViewer.clear()
            mutableHidden.value = emptyMap()
        }
}

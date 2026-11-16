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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * Buzz-only overlay state for one workspace channel: the newest kind-40100 canvas.
 * (Kind-40003 message edits are no longer tracked here — like every other edit kind
 * they are anchored on the message they edit via `Note.edits`.)
 *
 * This lives OUTSIDE the channel object on purpose. Screens, feed filters, and
 * composers capture their `RelayGroupChannel` instance once and hold it for the whole
 * session, so a channel can never be swapped for a "Buzz-typed" replacement when the
 * dialect is discovered mid-session — every live reference would keep rendering the
 * orphaned instance. Keeping the overlay in a registry keyed by the channel id makes
 * dialect discovery a non-event for object identity.
 *
 * The mutation is guarded by a per-state lock: consume runs on multiple relay dispatcher
 * threads, and unsynchronized check-then-act would let an older canvas overwrite a newer one.
 */
class BuzzWorkspaceState {
    private val lock = KmpLock()

    /**
     * The live canvas (kind 40100) note for this channel, or null when none seen — picked the way
     * the relay reads its head: `created_at DESC, id ASC` (see [CanvasEvent.isNewerHead]).
     */
    @Volatile
    var canvasNote: Note? = null
        private set

    private val canvasVersion = MutableStateFlow(0)

    /** Bumps when [canvasNote] is replaced by a newer revision, so a canvas view re-reads it. */
    val canvasUpdates: StateFlow<Int> = canvasVersion

    fun updateCanvas(note: Note) =
        lock.withLock {
            val current = canvasNote
            // A same-second tie goes to the smallest id, not the last arrival, so every client
            // (and the relay's own CAS check) agrees on which revision is the head.
            if (CanvasEvent.isNewerHead(note.createdAt() ?: 0L, note.idHex, current?.createdAt(), current?.idHex)) {
                canvasNote = note
                canvasVersion.value = canvasVersion.value + 1
            }
        }
}

/**
 * Registry of [BuzzWorkspaceState] keyed by the channel's `h` id. Buzz channel ids are
 * relay-generated UUIDs (NIP-11 `h_grammar: uuid-v4-lowercase`), so the id alone is a
 * sound key — no relay scoping needed, which also lets own-authored edits (consumed
 * with no provenance relay) land in the right state.
 *
 * Like `LocalCache`, a process-wide singleton.
 */
object BuzzWorkspaceStates {
    private val states = LargeCache<String, BuzzWorkspaceState>()

    fun getOrCreate(channelId: String): BuzzWorkspaceState = states.getOrCreate(channelId) { BuzzWorkspaceState() }

    fun getIfExists(channelId: String): BuzzWorkspaceState? = states.get(channelId)

    /** Test-only: clears all state so unit tests don't leak into each other. */
    fun clearForTesting() {
        states.clear()
    }
}

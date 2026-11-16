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
package com.vitorpamplona.amethyst.commons.model.backups

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Stands in front of an account's replaceable-event backups and decides, for each newer
 * version of a backed-up event, whether it may overwrite the saved copy or must be held back
 * and questioned as a [ReplaceableBackupConflict].
 *
 * Headless and platform-free so the rules can be unit-tested; the account's settings own one
 * and call [accept] from every backup update.
 *
 * [isLocallySigned] tells versions this app produced from ones that came from relays; it
 * defaults to [LocallySignedEvents]. [onChange] runs whenever the set of open conflicts
 * changes, so the owner can persist it ([open] / [restore]). [reapply] hands an event back to
 * the update that owns its slot: a conflict restored from storage has no retry closure until
 * its version arrives again, and keeping it must work regardless.
 */
class BackupConflictGuard(
    private val isLocallySigned: (HexKey) -> Boolean = LocallySignedEvents::contains,
    private val onChange: () -> Unit = {},
    private val reapply: (Event) -> Unit = {},
) {
    private val _conflicts = MutableStateFlow<Map<String, ReplaceableBackupConflict>>(emptyMap())

    /**
     * Newer versions of backed-up events that came from another client and dropped data the
     * backup still has, keyed by [ReplaceableBackupConflict.slot]. While a slot is in here its
     * backup is frozen on the saved version, so the user can still restore it. They outlive
     * the process through [open] / [restore]: an undecided question must still be there on the
     * next launch.
     */
    val conflicts: StateFlow<Map<String, ReplaceableBackupConflict>> = _conflicts

    // Everything below is only touched under the lock: the backup collectors run on IO
    // threads while the user resolves conflicts from the UI thread.
    private val lock = KmpLock()

    // Re-runs the update that raised each open conflict, once the user keeps the new version.
    private val retries = HashMap<String, () -> Unit>()

    // Versions whose conflict is being restored. They must not raise the same conflict again
    // while the saved version is re-signed.
    private val resolving = HashSet<HexKey>()

    // Externally signed versions the user explicitly chose to keep over the backup.
    private val accepted = HashSet<HexKey>()

    // Slots with a restore in flight, and those whose backup moved on meanwhile: a failed
    // restore must not reopen a conflict about a version that is no longer the newest.
    private val restoringSlots = HashSet<String>()
    private val movedOnWhileRestoring = HashSet<String>()

    /**
     * Decides whether [incoming] may overwrite the [saved] backup. Versions signed by this app
     * always may. A newer version signed elsewhere that removed items from the saved one is
     * held back and raised as a [ReplaceableBackupConflict]; [retry] re-runs the caller's
     * update once the user keeps it.
     *
     * An [isEmpty] version (a wiped list) always reaches the diff, so wiping everything is
     * questioned like any other loss; it only becomes the backup when this app signed it or
     * the user chose to keep it.
     */
    fun <T : Event> accept(
        saved: T?,
        incoming: T,
        isEmpty: Boolean = false,
        retry: () -> Unit,
    ): Boolean =
        lock.withLock {
            if (saved?.id == incoming.id) return@withLock false
            if (incoming.id in resolving) return@withLock false
            // Never roll the backup back, e.g. by the retry of a conflict whose version has
            // been overtaken since it was raised.
            if (saved != null && incoming.createdAt < saved.createdAt) return@withLock false

            val slot = backupSlot(incoming)
            val pending = _conflicts.value[slot]

            if (pending != null) {
                // The same version re-emitted by the cache: the open conflict already describes it.
                // Its retry is refreshed too: a conflict restored from storage carries none
                // until the version it is about comes round again.
                if (pending.incoming.id == incoming.id) {
                    retries[slot] = retry
                    return@withLock false
                }
                // An older version (e.g. the retry of a just-accepted conflict that a newer
                // external change overtook) must never replace or clear the newer question.
                if (incoming.createdAt < pending.incoming.createdAt) return@withLock false
            }

            val isLocal = isLocallySigned(incoming.id)
            val wasAccepted = incoming.id in accepted

            // While a conflict is open, even edits made here are built on top of the external
            // version, so they are still compared with the version the user may want to restore.
            val reference = pending?.saved ?: saved?.takeUnless { isLocal }

            if (reference != null && !wasAccepted) {
                val diff = ReplaceableBackupDiff.detectLoss(reference, incoming)
                if (diff != null) {
                    // A local edit keeps the external change as the conflict's cause.
                    val cause = if (isLocal && pending != null) pending.cause else incoming
                    retries[slot] = retry
                    _conflicts.update { it + (slot to ReplaceableBackupConflict(reference, incoming, cause, diff)) }
                    onChange()
                    return@withLock false
                }
            }

            dropLocked(slot)
            // A wipe from elsewhere that lost nothing (e.g. the backup was empty too) doesn't
            // become the backup; one signed here or explicitly kept does.
            val stored = !isEmpty || isLocal || wasAccepted
            if (stored) {
                accepted.remove(incoming.id)
                if (slot in restoringSlots) movedOnWhileRestoring.add(slot)
            }
            stored
        }

    /** Forgets the open conflict of [slot], e.g. because its event was deleted. */
    fun drop(slot: String) {
        lock.withLock { dropLocked(slot) }
    }

    private fun dropLocked(slot: String) {
        if (_conflicts.value.containsKey(slot)) {
            _conflicts.update { it - slot }
            onChange()
        }
        retries.remove(slot)
    }

    /**
     * Re-seeds the open conflicts read back from storage, as (saved, incoming, cause).
     *
     * Their diff is recomputed rather than stored, so a version that no longer removes
     * anything (the other app put it back) quietly stops being a conflict.
     */
    fun restore(saved: List<Triple<Event, Event, Event>>) {
        if (saved.isEmpty()) return
        val restored =
            saved.mapNotNull { (savedEvent, incoming, cause) ->
                val diff = ReplaceableBackupDiff.detectLoss(savedEvent, incoming) ?: return@mapNotNull null
                val conflict = ReplaceableBackupConflict(savedEvent, incoming, cause, diff)
                conflict.slot to conflict
            }
        if (restored.isEmpty()) return
        lock.withLock { _conflicts.update { it + restored } }
    }

    /** The open conflicts, as the three events storage needs to rebuild each one. */
    fun open(): List<Triple<Event, Event, Event>> = _conflicts.value.values.map { Triple(it.saved, it.incoming, it.cause) }

    /**
     * Removes [conflict] if it is still the open conflict of its slot and returns its retry.
     * Returns null when it was already resolved or replaced by a newer one, so callers never
     * act on a stale conflict.
     */
    private fun claimLocked(conflict: ReplaceableBackupConflict): (() -> Unit)? {
        if (_conflicts.value[conflict.slot] !== conflict) return null
        _conflicts.update { it - conflict.slot }
        onChange()
        // A conflict restored from storage has no closure to re-run: rebuild it from the event.
        // Falling back to {} instead would clear the card and quietly keep the old backup.
        return retries.remove(conflict.slot) ?: { reapply(conflict.incoming) }
    }

    /** Keeps the new version: the backup moves forward to [ReplaceableBackupConflict.incoming]. */
    fun keepIncoming(conflict: ReplaceableBackupConflict) {
        val retry =
            lock.withLock {
                val claimed = claimLocked(conflict) ?: return
                accepted.add(conflict.incoming.id)
                claimed
            }
        // Outside the lock: the retry re-enters accept.
        retry()
    }

    /**
     * Starts restoring the saved version: claims [conflict] and stops its incoming version
     * from raising it again while the saved one is re-signed. Returns null when the conflict
     * is stale (already resolved, replaced or dropped); otherwise a token to hand back to
     * [finishRestoring].
     */
    fun startRestoring(conflict: ReplaceableBackupConflict): (() -> Unit)? =
        lock.withLock {
            val retry = claimLocked(conflict) ?: return@withLock null
            resolving.add(conflict.incoming.id)
            restoringSlots.add(conflict.slot)
            retry
        }

    /**
     * Ends a restore. On failure the conflict is reopened so the user can try again, unless
     * something else (a newer change, a deletion) took the slot meanwhile.
     */
    fun finishRestoring(
        conflict: ReplaceableBackupConflict,
        token: () -> Unit,
        restored: Boolean,
    ) {
        lock.withLock {
            resolving.remove(conflict.incoming.id)
            restoringSlots.remove(conflict.slot)
            val movedOn = movedOnWhileRestoring.remove(conflict.slot)
            if (restored || movedOn || _conflicts.value.containsKey(conflict.slot)) return@withLock
            _conflicts.update { it + (conflict.slot to conflict) }
            retries[conflict.slot] = token
            onChange()
        }
    }
}

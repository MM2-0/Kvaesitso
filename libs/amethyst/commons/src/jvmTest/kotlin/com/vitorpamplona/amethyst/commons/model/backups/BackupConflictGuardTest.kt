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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rules that decide whether a newer version of a backed-up event may overwrite the
 * backup, and how the user's answers resolve the conflicts it raises.
 */
class BackupConflictGuardTest {
    private val signer = NostrSignerSync(KeyPair())
    private val local = HashSet<HexKey>()
    private var changes = 0
    private val reapplied = ArrayList<Event>()
    private val guard =
        BackupConflictGuard(
            isLocallySigned = { it in local },
            onChange = { changes++ },
            reapply = { reapplied.add(it) },
        )

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun follows(
        createdAt: Long,
        vararg pubKeys: String,
    ): Event = signer.sign(createdAt, 3, pubKeys.map { arrayOf("p", it) }.toTypedArray(), "")

    private fun signedHere(event: Event) = event.also { local.add(it.id) }

    private fun openConflict() =
        guard.conflicts.value.values
            .single()

    /** A backup slot the way AccountSettings drives it: the guard in front of a stored copy. */
    private inner class Backup(
        var saved: Event?,
    ) {
        var saves = 0

        fun update(incoming: Event) {
            if (guard.accept(saved, incoming, isEmpty = incoming.tags.isEmpty()) { update(incoming) }) {
                saved = incoming
                saves++
            }
        }
    }

    @Test
    fun versionsSignedHereAlwaysReplaceTheBackup() {
        val backup = Backup(follows(100, alice, bob))
        val edit = signedHere(follows(200, bob))
        backup.update(edit)
        assertSame(edit, backup.saved)
        assertTrue(guard.conflicts.value.isEmpty())
    }

    @Test
    fun externalVersionThatOnlyAddsReplacesTheBackup() {
        val backup = Backup(follows(100, alice))
        val external = follows(200, alice, bob)
        backup.update(external)
        assertSame(external, backup.saved)
        assertTrue(guard.conflicts.value.isEmpty())
    }

    @Test
    fun firstVersionWithNothingSavedIsAccepted() {
        val backup = Backup(null)
        val external = follows(200, alice)
        backup.update(external)
        assertSame(external, backup.saved)
    }

    @Test
    fun externalLossyVersionFreezesTheBackupAndRaisesAConflict() {
        val saved = follows(100, alice, bob)
        val backup = Backup(saved)
        val external = follows(200, bob)
        backup.update(external)

        assertSame(saved, backup.saved)
        val conflict = openConflict()
        assertSame(saved, conflict.saved)
        assertSame(external, conflict.incoming)
        assertSame(external, conflict.cause)
        assertEquals(backupSlot(external), conflict.slot)
    }

    @Test
    fun theSameVersionReEmittedKeepsTheSameConflict() {
        val backup = Backup(follows(100, alice, bob))
        val external = follows(200, bob)
        backup.update(external)
        val first = openConflict()
        backup.update(external)
        assertSame(first, openConflict())
    }

    @Test
    fun anOlderVersionNeverReplacesAnOpenConflict() {
        val backup = Backup(follows(100, alice, bob, carol))
        val newer = follows(300, carol)
        backup.update(newer)
        backup.update(follows(200, bob, carol))
        assertSame(newer, openConflict().incoming)
    }

    @Test
    fun aNewerExternalVersionIsStillComparedWithTheFrozenBackup() {
        val saved = follows(100, alice, bob)
        val backup = Backup(saved)
        backup.update(follows(200, bob))
        // Adds to the lossy version, but still lacks alice from the saved one.
        val newer = follows(300, bob, carol)
        backup.update(newer)

        val conflict = openConflict()
        assertSame(saved, conflict.saved)
        assertSame(newer, conflict.incoming)
        assertSame(saved, backup.saved)
    }

    @Test
    fun aLocalEditOnTopOfTheLossyVersionKeepsTheConflictAndItsCause() {
        val saved = follows(100, alice, bob)
        val backup = Backup(saved)
        val external = follows(200, bob)
        backup.update(external)

        val edit = signedHere(follows(300, bob, carol))
        backup.update(edit)

        val conflict = openConflict()
        assertSame(edit, conflict.incoming)
        assertSame(external, conflict.cause)
        assertSame(saved, backup.saved)
    }

    @Test
    fun aLocalEditThatBringsEverythingBackClearsTheConflict() {
        val backup = Backup(follows(100, alice, bob))
        backup.update(follows(200, bob))

        val edit = signedHere(follows(300, alice, bob))
        backup.update(edit)

        assertTrue(guard.conflicts.value.isEmpty())
        assertSame(edit, backup.saved)
    }

    @Test
    fun keepingTheNewVersionMovesTheBackupForward() {
        val backup = Backup(follows(100, alice, bob))
        val external = follows(200, bob)
        backup.update(external)

        guard.keepIncoming(openConflict())

        assertTrue(guard.conflicts.value.isEmpty())
        assertSame(external, backup.saved)
    }

    @Test
    fun aStaleOrRepeatedAnswerDoesNothing() {
        val backup = Backup(follows(100, alice, bob))
        backup.update(follows(200, bob))
        val stale = openConflict()
        val newer = follows(300)
        backup.update(newer)

        guard.keepIncoming(stale)
        assertSame(newer, openConflict().incoming)
        assertNull(guard.startRestoring(stale))

        val current = openConflict()
        guard.keepIncoming(current)
        guard.keepIncoming(current)
        assertEquals(1, backup.saves)
    }

    @Test
    fun theRetryOfAnOvertakenAcceptCannotClearTheNewerConflict() {
        val backup = Backup(follows(100, alice, bob, carol))
        val first = follows(200, bob, carol)
        backup.update(first)
        val claimed = openConflict()

        // A newer lossy change arrives, then the retry of the (stale) first one runs.
        val newer = follows(300, carol)
        backup.update(newer)
        guard.keepIncoming(claimed)
        backup.update(first)

        assertSame(newer, openConflict().incoming)
    }

    @Test
    fun whileRestoringTheLossyVersionCannotReRaiseTheConflict() {
        val backup = Backup(follows(100, alice, bob))
        val external = follows(200, bob)
        backup.update(external)
        val conflict = openConflict()

        val token = assertNotNull(guard.startRestoring(conflict))
        backup.update(external)
        assertTrue(guard.conflicts.value.isEmpty())

        guard.finishRestoring(conflict, token, restored = true)
        assertTrue(guard.conflicts.value.isEmpty())
    }

    @Test
    fun aFailedRestoreReopensTheConflict() {
        val backup = Backup(follows(100, alice, bob))
        val external = follows(200, bob)
        backup.update(external)
        val conflict = openConflict()

        val token = assertNotNull(guard.startRestoring(conflict))
        guard.finishRestoring(conflict, token, restored = false)
        assertSame(conflict, openConflict())

        // The reopened conflict can still be answered, and its retry still works.
        guard.keepIncoming(conflict)
        assertSame(external, backup.saved)
    }

    @Test
    fun aFailedRestoreDoesNotOverwriteANewerConflict() {
        val backup = Backup(follows(100, alice, bob, carol))
        backup.update(follows(200, bob, carol))
        val conflict = openConflict()

        val token = assertNotNull(guard.startRestoring(conflict))
        val newer = follows(300, carol)
        backup.update(newer)
        guard.finishRestoring(conflict, token, restored = false)

        assertSame(newer, openConflict().incoming)
    }

    @Test
    fun anExternalWipeIsQuestioned() {
        val saved = follows(100, alice)
        val backup = Backup(saved)
        backup.update(follows(200))
        assertSame(saved, backup.saved)
        assertNotNull(openConflict())
    }

    @Test
    fun anExternalWipeOfAnEmptyBackupIsNotStored() {
        val saved = follows(100)
        val backup = Backup(saved)
        backup.update(follows(200))
        assertSame(saved, backup.saved)
        assertTrue(guard.conflicts.value.isEmpty())
    }

    @Test
    fun aWipeSignedHereOrKeptByTheUserIsStored() {
        val backup = Backup(follows(100, alice))
        val wipe = signedHere(follows(200))
        backup.update(wipe)
        assertSame(wipe, backup.saved)

        val other = Backup(follows(100, alice))
        // A different timestamp, or it would be the very event signed above.
        val externalWipe = follows(201)
        other.update(externalWipe)
        guard.keepIncoming(openConflict())
        assertSame(externalWipe, other.saved)
    }

    @Test
    fun droppingASlotForgetsItsConflict() {
        val backup = Backup(follows(100, alice, bob))
        backup.update(follows(200, bob))
        val conflict = openConflict()

        guard.drop(conflict.slot)
        assertTrue(guard.conflicts.value.isEmpty())
        guard.keepIncoming(conflict)
        assertEquals(0, backup.saves)
    }

    @Test
    fun eachEventKindHasItsOwnSlot() {
        val followsBackup = Backup(follows(100, alice, bob))
        val mutes = Backup(signer.sign<Event>(100, 10000, arrayOf(arrayOf("p", alice)), ""))
        followsBackup.update(follows(200, bob))
        mutes.update(signer.sign<Event>(200, 10000, arrayOf(), ""))
        assertEquals(2, guard.conflicts.value.size)
    }

    @Test
    fun openConflictsRoundTripThroughStorage() {
        val saved = follows(100, alice, bob)
        val external = follows(200, bob)
        Backup(saved).update(external)

        val afterRestart = BackupConflictGuard(isLocallySigned = { false })
        afterRestart.restore(guard.open())

        val conflict =
            afterRestart.conflicts.value.values
                .single()
        assertEquals(saved.id, conflict.saved.id)
        assertEquals(external.id, conflict.incoming.id)
        assertEquals(external.id, conflict.cause.id)
    }

    @Test
    fun aStoredConflictThatNoLongerLosesDataIsNotRestored() {
        val saved = follows(100, alice)
        val putBack = follows(200, alice, bob)
        guard.restore(listOf(Triple(saved, putBack, putBack)))
        assertTrue(guard.conflicts.value.isEmpty())
    }

    @Test
    fun keepingARestoredConflictReappliesItsVersion() {
        val external = follows(200, bob)
        guard.restore(listOf(Triple(follows(100, alice, bob), external, external)))

        guard.keepIncoming(openConflict())

        assertEquals(listOf(external), reapplied)
        // The reapplied update is now accepted instead of raising the conflict again.
        val backup = Backup(follows(100, alice, bob))
        backup.update(external)
        assertSame(external, backup.saved)
    }

    @Test
    fun aReEmittedVersionGivesARestoredConflictItsRetryBack() {
        val saved = follows(100, alice, bob)
        val external = follows(200, bob)
        guard.restore(listOf(Triple(saved, external, external)))

        val backup = Backup(saved)
        backup.update(external)
        guard.keepIncoming(openConflict())

        assertTrue(reapplied.isEmpty())
        assertSame(external, backup.saved)
    }

    @Test
    fun everyChangeToTheOpenConflictsIsReported() {
        val backup = Backup(follows(100, alice, bob))
        val external = follows(200, bob)
        backup.update(external)
        assertEquals(1, changes)

        // A re-emit changes nothing to store.
        backup.update(external)
        assertEquals(1, changes)

        val conflict = openConflict()
        val token = assertNotNull(guard.startRestoring(conflict))
        assertEquals(2, changes)
        guard.finishRestoring(conflict, token, restored = false)
        assertEquals(3, changes)

        guard.drop(conflict.slot)
        assertEquals(4, changes)
        guard.drop(conflict.slot)
        assertEquals(4, changes)
    }

    @Test
    fun anOlderVersionNeverRollsTheBackupBack() {
        val saved = follows(200, alice)
        val backup = Backup(saved)
        backup.update(signedHere(follows(100, alice, bob)))
        assertSame(saved, backup.saved)
    }

    @Test
    fun aFailedRestoreDoesNotReopenAConflictTheBackupMovedPast() {
        val saved = follows(100, alice, bob)
        val backup = Backup(saved)
        backup.update(follows(200, bob))
        val conflict = openConflict()
        val token = assertNotNull(guard.startRestoring(conflict))

        // While the signer is still asking, another app puts everything back and more.
        val fixed = follows(300, alice, bob, carol)
        backup.update(fixed)
        guard.finishRestoring(conflict, token, restored = false)

        assertTrue(guard.conflicts.value.isEmpty())
        assertSame(fixed, backup.saved)
    }
}

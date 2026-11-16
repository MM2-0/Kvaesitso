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
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An undecided conflict has to survive the process that raised it. Before this, it lived only in
 * a `MutableStateFlow`, so a restart answered the user's question for them by dropping it.
 */
class BackupConflictStorageTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun sign(
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String = "",
    ): Event = signer.sign<Event>(createdAt, kind, tags, content)

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun savedAndIncoming(): Pair<Event, Event> =
        sign(3, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob))) to
            sign(3, 200, arrayOf(arrayOf("p", carol)))

    @Test
    fun survivesARoundTrip() {
        val (saved, incoming) = savedAndIncoming()

        val restored = BackupConflictStorage.decode(BackupConflictStorage.encode(listOf(Triple(saved, incoming, incoming))))

        assertEquals(1, restored.size)
        val (restoredSaved, restoredIncoming, restoredCause) = restored.first()
        assertEquals(saved.id, restoredSaved.id)
        assertEquals(incoming.id, restoredIncoming.id)
        assertEquals(incoming.id, restoredCause.id)
        // The events must come back whole, not as ids: the review screen diffs their contents.
        assertEquals(2, restoredSaved.tags.count { it.firstOrNull() == "p" })
    }

    @Test
    fun theRestoredPairStillDescribesTheSameLoss() {
        val (saved, incoming) = savedAndIncoming()

        val (restoredSaved, restoredIncoming, _) = BackupConflictStorage.decode(BackupConflictStorage.encode(listOf(Triple(saved, incoming, incoming)))).first()

        // The diff is recomputed rather than stored, so it has to still be derivable.
        val diff = ReplaceableBackupDiff.detectLoss(restoredSaved, restoredIncoming)
        assertNotNull(diff)
        assertTrue(diff.removesData())
    }

    @Test
    fun aVersionThatNoLongerRemovesAnythingStopsBeingAConflict() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice)))
        val putBack = sign(3, 200, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))

        val (restoredSaved, restoredIncoming, _) = BackupConflictStorage.decode(BackupConflictStorage.encode(listOf(Triple(saved, putBack, putBack)))).first()

        assertNull(ReplaceableBackupDiff.detectLoss(restoredSaved, restoredIncoming))
    }

    @Test
    fun severalSlotsAreKeptApart() {
        val (saved, incoming) = savedAndIncoming()
        val mutesSaved = sign(10000, 100, arrayOf(arrayOf("p", alice)))
        val mutesIncoming = sign(10000, 200, arrayOf())

        val restored =
            BackupConflictStorage.decode(
                BackupConflictStorage.encode(
                    listOf(Triple(saved, incoming, incoming), Triple(mutesSaved, mutesIncoming, mutesIncoming)),
                ),
            )

        assertEquals(2, restored.size)
        assertEquals(listOf(3, 10000), restored.map { it.second.kind })
    }

    @Test
    fun nothingStoredMeansNoConflicts() {
        assertTrue(BackupConflictStorage.decode(null).isEmpty())
        assertTrue(BackupConflictStorage.decode("").isEmpty())
        assertTrue(BackupConflictStorage.encode(emptyList()).let { BackupConflictStorage.decode(it) }.isEmpty())
    }

    @Test
    fun oneUnreadableEntryDoesNotTakeTheOthersWithIt() {
        val (saved, incoming) = savedAndIncoming()
        // A row whose events are not parseable, next to a good one.
        val mixed =
            JsonMapper.toJson(
                listOf(
                    listOf("not an event", "not an event", "not an event"),
                    listOf(
                        OptimizedJsonMapper.toJson(saved),
                        OptimizedJsonMapper.toJson(incoming),
                        OptimizedJsonMapper.toJson(incoming),
                    ),
                ),
            )

        val restored = BackupConflictStorage.decode(mixed)

        assertEquals(1, restored.size)
        assertEquals(incoming.id, restored.first().second.id)
    }

    @Test
    fun garbageIsNotAConflict() {
        assertTrue(BackupConflictStorage.decode("not json at all").isEmpty())
    }
}

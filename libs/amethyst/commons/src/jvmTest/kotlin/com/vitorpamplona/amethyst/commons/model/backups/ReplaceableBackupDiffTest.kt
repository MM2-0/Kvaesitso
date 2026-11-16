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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The backup guard's use of the events' own diffs; the diffs themselves are tested in quartz. */
class ReplaceableBackupDiffTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun sign(
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String = "",
    ): Event = signer.sign<Event>(createdAt, kind, tags, content)

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @Test
    fun lossyRewriteIsDetected() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", bob)))
        val diff = assertIs<ContactListDiff>(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(listOf(alice), diff.follows.removed.map { it.pubKey })
    }

    @Test
    fun additionsOnlyAreNotALoss() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice)))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun clearedPrivateItemsAreALoss() {
        val saved = sign(10000, 100, arrayOf(), "encrypted")
        val incoming = sign(10000, 200, arrayOf(), "")
        assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun olderVersionIsNeverALoss() {
        val saved = sign(3, 200, arrayOf(arrayOf("p", alice)))
        val incoming = sign(3, 100, arrayOf())
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun locallySignedEventsTrackOnlyReplaceables() {
        val list = sign(10000, 100, arrayOf())
        val note = sign(1, 100, arrayOf())
        LocallySignedEvents.mark(list)
        LocallySignedEvents.mark(note)
        assertTrue(LocallySignedEvents.contains(list.id))
        assertFalse(LocallySignedEvents.contains(note.id))
    }

    @Test
    fun locallySignedEventsForgetTheOldestBeyondCapacity() {
        val first = sign(10000, 1, arrayOf())
        LocallySignedEvents.mark(first)
        repeat(600) { LocallySignedEvents.mark(sign(10000, 2L + it, arrayOf())) }
        assertFalse(LocallySignedEvents.contains(first.id))
    }

    @Test
    fun restoreDropsClientPowAndPastExpiration() {
        val saved =
            sign(
                10000,
                100,
                arrayOf(
                    arrayOf("p", alice),
                    arrayOf("client", "OtherApp"),
                    arrayOf("nonce", "123", "20"),
                    arrayOf("expiration", "500"),
                    arrayOf("word", "spam"),
                ),
                "private",
            )
        val tags = BackupRestore.tagsToResign(saved, now = 1000)
        assertEquals(listOf(listOf("p", alice), listOf("word", "spam")), tags.map { it.toList() })
    }

    @Test
    fun restoreKeepsAFutureExpiration() {
        val saved = sign(10000, 100, arrayOf(arrayOf("p", alice), arrayOf("expiration", "5000")))
        val tags = BackupRestore.tagsToResign(saved, now = 1000)
        assertEquals(listOf(listOf("p", alice), listOf("expiration", "5000")), tags.map { it.toList() })
    }
}

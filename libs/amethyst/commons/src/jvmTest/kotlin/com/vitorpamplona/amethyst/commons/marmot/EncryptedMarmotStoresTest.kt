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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round trips for the file-backed Marmot stores, which had no coverage while they sat in `amethyst/`
 * behind an `Android` prefix they never earned.
 *
 * What matters here is that the bytes survive a restart: these hold MLS group state and the message
 * log, so a store that writes but cannot read back loses a group's history and its ratchet — and MLS
 * state that cannot be reloaded is not recoverable from the relays.
 */
class EncryptedMarmotStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    /** A fresh account directory plus its own key file, so tests cannot read each other's data. */
    private fun accountDir(): Pair<File, SecretEncryption> {
        val n = seq++
        val dir = folder.newFolder("account_$n")
        return dir to SecretEncryption(File(folder.root, "secret_$n.key"))
    }

    // Group ids must be hex: both stores validate, which is what stops a crafted id escaping the
    // account directory. Using a realistic 64-char id rather than a label keeps the tests honest.
    private val groupId = "a".repeat(63) + "1"
    private val otherGroupId = "b".repeat(63) + "2"

    @Test
    fun groupStateSurvivesANewStoreOverTheSameDirectory() =
        runTest {
            val (dir, encryption) = accountDir()
            val payload = byteArrayOf(1, 2, 3, 4, 5)

            EncryptedMlsGroupStateStore(dir, encryption).save(groupId, payload)

            val reopened = EncryptedMlsGroupStateStore(dir, encryption).load(groupId)
            assertEquals("the same bytes come back", payload.toList(), reopened?.toList())
        }

    @Test
    fun anUnknownGroupLoadsAsNull() =
        runTest {
            val (dir, encryption) = accountDir()

            assertNull(EncryptedMlsGroupStateStore(dir, encryption).load("c".repeat(63) + "3"))
        }

    @Test
    fun deletingAGroupRemovesItFromTheListing() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir, encryption)
            store.save(groupId, byteArrayOf(9))
            store.save(otherGroupId, byteArrayOf(8))

            store.delete(groupId)

            assertEquals("only the other group is left", listOf(otherGroupId), store.listGroups())
            assertNull("and its state is gone", store.load(groupId))
        }

    /** The sender ratchet is stored separately from the group blob; losing it breaks decryption. */
    @Test
    fun theSenderRatchetRoundTripsIndependentlyOfTheGroupState() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir, encryption)

            store.save(groupId, byteArrayOf(1))
            store.saveSenderRatchet(groupId, byteArrayOf(7, 7, 7))

            val reopened = EncryptedMlsGroupStateStore(dir, encryption)
            assertEquals("ratchet preserved", listOf<Byte>(7, 7, 7), reopened.loadSenderRatchet(groupId)?.toList())
            assertEquals("and the group blob is untouched", listOf<Byte>(1), reopened.load(groupId)?.toList())
        }

    @Test
    fun appendedMessagesComeBackInOrderAfterAReopen() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir, encryption)

            store.appendMessage(groupId, """{"id":"one"}""")
            store.appendMessage(groupId, """{"id":"two"}""")

            val reopened = EncryptedMarmotMessageStore(dir, encryption).loadMessages(groupId)
            assertEquals("both, in append order", listOf("""{"id":"one"}""", """{"id":"two"}"""), reopened)
        }

    /**
     * Media keys, epoch admins and moderation verdicts decide what a restart shows: an
     * attachment that opens, a deleted message that stays deleted. Each is first-write-wins.
     */
    @Test
    fun epochRecordsSurviveAReopenAndKeepTheirFirstValue() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir, encryption)
            val verdictId = "c".repeat(64)

            store.recordMediaSecret(groupId, 3, byteArrayOf(1, 2))
            store.recordMediaSecret(groupId, 3, byteArrayOf(9))
            store.recordMediaSecret(groupId, 4, byteArrayOf(3))
            store.recordEpochAdmins(groupId, 3, setOf("a", "b"))
            store.recordEpochAdmins(groupId, 3, setOf("z"))
            store.recordModerationVerdict(groupId, verdictId, true)
            store.recordModerationVerdict(groupId, verdictId, false)

            val reopened = EncryptedMarmotMessageStore(dir, encryption)
            val secrets = reopened.loadMediaSecrets(groupId)
            assertEquals(listOf<Byte>(1, 2), secrets[3]?.toList())
            assertEquals(listOf<Byte>(3), secrets[4]?.toList())
            assertEquals(mapOf(3L to setOf("a", "b")), reopened.loadEpochAdmins(groupId))
            assertEquals(mapOf(verdictId to true), reopened.loadModerationVerdicts(groupId))
        }

    @Test
    fun aGroupWithNoMessagesLoadsEmptyRatherThanFailing() =
        runTest {
            val (dir, encryption) = accountDir()

            assertTrue(EncryptedMarmotMessageStore(dir, encryption).loadMessages("d".repeat(63) + "4").isEmpty())
        }

    @Test
    fun deletingAGroupDropsItsMessageLog() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir, encryption)
            store.appendMessage(groupId, """{"id":"one"}""")

            store.delete(groupId)

            assertTrue(EncryptedMarmotMessageStore(dir, encryption).loadMessages(groupId).isEmpty())
        }

    /** The group snapshot is what a cold start restores from before replaying the log. */
    @Test
    fun theGroupSnapshotRoundTrips() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir, encryption)

            store.recordGroupSnapshot(groupId, """{"epoch":4}""")

            assertEquals("""{"epoch":4}""", EncryptedMarmotMessageStore(dir, encryption).loadGroupSnapshot(groupId))
        }

    /** Two accounts are two directories: one must never read the other's groups. */
    @Test
    fun twoAccountDirectoriesDoNotSeeEachOther() =
        runTest {
            val (dirA, encA) = accountDir()
            val (dirB, encB) = accountDir()

            EncryptedMlsGroupStateStore(dirA, encA).save(groupId, byteArrayOf(1))

            assertNull("B cannot see A's group", EncryptedMlsGroupStateStore(dirB, encB).load(groupId))
            assertTrue("nor list it", EncryptedMlsGroupStateStore(dirB, encB).listGroups().isEmpty())
        }

    /** The hex check is a path-traversal guard: a crafted id must not be able to leave the account dir. */
    @Test
    fun aNonHexGroupIdIsRejected() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir, encryption)

            listOf("../escape", "not hex", "abc/def", "").forEach { bad ->
                val thrown =
                    try {
                        store.load(bad)
                        false
                    } catch (e: IllegalArgumentException) {
                        true
                    }
                assertTrue("\"$bad\" must be rejected, not resolved to a path", thrown)
            }
        }
}

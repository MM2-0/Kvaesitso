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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.storage.EncryptedAppendLog
import com.vitorpamplona.cordn.appMultiDevice.CordnCarriedKeyPackage
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationGroup
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationSnapshot
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.client.CordnMessageSummary
import com.vitorpamplona.cordn.client.CordnRoomState
import com.vitorpamplona.cordn.client.CordnRoomStateCodec
import com.vitorpamplona.cordn.client.EchoStateCodec
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessageCodec
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.sync.EchoState
import com.vitorpamplona.cordn.sync.GroupCursor
import com.vitorpamplona.cordn.sync.PendingEpochOperation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The on-disk format of the cordn stores and of [EncryptedAppendLog], pinned
 * byte for byte.
 *
 * These files hold a user's encrypted MLS group state and message history, and
 * none of it can be re-derived from anywhere else on the device: a layout or
 * framing change that the next build cannot read is a group the user has lost.
 * So this drives every writer through a representative sequence — overwrites,
 * deletes, dedup, appends, a fold, a legacy blob, a torn tail, a migration —
 * and compares the resulting tree against constants captured from the
 * implementation that shipped. It then goes the other way, laying those exact
 * bytes down on a clean disk and reading them back, which is the direction an
 * upgrade actually travels.
 *
 * The cipher is a deterministic stand-in (a tag byte plus XOR) so the bytes are
 * stable; it also makes a segment's ciphertext one byte longer than its
 * plaintext, so a length prefix that recorded the wrong one would show.
 *
 * If this fails, the format changed. Do not regenerate the constants to make it
 * pass unless the change is deliberate and comes with a reader for the old one.
 */
class CordnStorageFormatGoldenTest {
    /** Deterministic, reversible, and length-changing. */
    private class TaggedXorCipher : CordnBlobCipher {
        override fun encrypt(bytes: ByteArray): ByteArray = byteArrayOf(TAG) + ByteArray(bytes.size) { (bytes[it].toInt() xor MASK).toByte() }

        override fun decrypt(bytes: ByteArray): ByteArray {
            require(bytes.isNotEmpty() && bytes[0] == TAG) { "not ours" }
            return ByteArray(bytes.size - 1) { (bytes[it + 1].toInt() xor MASK).toByte() }
        }

        companion object {
            const val TAG: Byte = 0x7C
            const val MASK = 0x5A
        }
    }

    private val rootFile: File = createTempDirectory("cordn-golden").toFile()
    private val cipher = TaggedXorCipher()

    private val account = "a".repeat(64)
    private val coordinatorA = "b".repeat(64)
    private val coordinatorB = "c".repeat(64)
    private val handedBackAccount = "d".repeat(64)
    private val migratedAccount = "e".repeat(64)

    // ---- Construction glue: the only part of this file that follows the stores' API. ----

    private val root = rootFile.toOkioPath()

    private fun groupStore(coordinator: HexKey) = FileCordnGroupStore(CordnStorageLayout.directoryFor(root, account, coordinator), cipher)

    private fun keyPackageStore(coordinator: HexKey) = FileCordnKeyPackageStore(CordnStorageLayout.directoryFor(root, account, coordinator), cipher)

    private fun coordinatorStore() = FileCordnCoordinatorStore(CordnStorageLayout.accountDirectoryFor(root, account), cipher)

    private fun handoffStore(owner: HexKey) = FileCordnHandoffStore(CordnStorageLayout.accountDirectoryFor(root, owner))

    private fun appendLog(compactAfterSegments: Int) =
        EncryptedAppendLog(
            encrypt = cipher::encrypt,
            decrypt = { runCatching { cipher.decrypt(it) }.getOrNull() },
            compactAfterSegments = compactAfterSegments,
        )

    private fun logFile(name: String) = root / "logs" / name

    private suspend fun migrationWrite(snapshot: CordnMigrationSnapshot) = CordnMigrationStores.write(root, migratedAccount, cipher, snapshot)

    private suspend fun migrationRead(configs: List<CoordinatorConfig>) = CordnMigrationStores.read(root, migratedAccount, cipher, configs)

    // ---- End of construction glue. ----

    /** The test's own byte surgery, beneath the API under test. */
    private fun raw(relative: String) = File(rootFile, relative)

    @AfterTest
    fun cleanUp() {
        rootFile.deleteRecursively()
    }

    private fun message(
        content: String,
        cursor: Long,
    ) = CordnDeliveredMessage(CordnEnvelope.build("aa".repeat(32), 1_757_000_000L + cursor, 9, content = content), cursor = cursor)

    private val m1 = message("first", 1)
    private val m2 = message("second ✓", 2)
    private val m3 = message("third", 3)

    private fun relay(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    private val coordinators =
        listOf(
            CoordinatorConfig(coordinatorA, listOf(relay("wss://one.example.com")), CoordinatorConfig.Origin.MANUAL, "Work"),
            CoordinatorConfig(coordinatorB, listOf(relay("wss://two.example.com"), relay("wss://three.example.com")), CoordinatorConfig.Origin.GROUP_REF),
        )

    private val room = CordnRoomState("héllo draft ✓", 42)
    private val echoes = EchoState(listOf(PendingEpochOperation("c2VhbGVk", localStateApplied = false)), listOf(3L, 5L))

    /** The pre-segment format: `uint32 count, (uint32 len, bytes)*`, encrypted as one blob. */
    private fun legacyPlain(vararg entries: String): ByteArray {
        val out = ByteArrayOutputStream()

        fun int(v: Int) = out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        int(entries.size)
        entries.forEach {
            val bytes = it.encodeToByteArray()
            int(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private val migrationSnapshot =
        CordnMigrationSnapshot(
            accountPubKey = migratedAccount,
            groups =
                listOf(
                    CordnMigrationGroup(
                        coordinatorPubKey = coordinatorA,
                        coordinatorRelays = listOf("wss://coord.example"),
                        gid = "mg-1",
                        clientStateBase64 = b64("mls-state-1".encodeToByteArray()),
                        cursor = 77,
                        roomStateBase64 = b64(CordnRoomStateCodec.encode(CordnRoomState("migrated draft", 70))),
                        echoStateBase64 = b64(EchoStateCodec.encode(EchoState(ownMessageCursors = listOf(76L)))),
                        joinedViaRequest = true,
                        messages = listOf(CordnDeliveredMessageCodec.encode(m1), CordnDeliveredMessageCodec.encode(m2)),
                    ),
                    CordnMigrationGroup(
                        coordinatorPubKey = coordinatorB,
                        coordinatorRelays = listOf("wss://other.example"),
                        gid = "mg-2",
                        clientStateBase64 = b64(byteArrayOf(0, 1, 2, -1, -2)),
                        cursor = 0,
                    ),
                ),
            keyPackages = listOf(CordnCarriedKeyPackage(coordinatorA, "kp-ref", b64("bundle!".encodeToByteArray()))),
        )

    /** Every writer, through the paths that shape the tree: overwrite, delete, dedup, fold, repair. */
    private suspend fun drive() {
        groupStore(coordinatorA).apply {
            saveGroup("g1", "state-v1".encodeToByteArray())
            saveGroup("g1", "state-v2-longer".encodeToByteArray())
            saveCursor("g1", GroupCursor(fetchCursor = 0x0102030405060708L, lastCursor = 0x1112131415161718L))
            saveJoinedViaRequest("g1")
            saveRoomState("g1", room)
            saveEchoState("g1", echoes)
            appendMessage("g1", m1)
            appendMessage("g1", m2)
            appendMessage("g1", m1)
            appendMessage("g1", m3)

            saveGroup("../odd/gid ✓", byteArrayOf(9, 8, 7))

            saveGroup("blanked", byteArrayOf(1))
            saveRoomState("blanked", room)
            saveRoomState("blanked", CordnRoomState())
            saveEchoState("blanked", echoes)
            saveEchoState("blanked", EchoState())

            saveGroup("gone", byteArrayOf(2))
            saveCursor("gone", GroupCursor(5, 5))
            appendMessage("gone", m1)
            deleteGroup("gone")
        }
        groupStore(coordinatorB).saveGroup("g1", "other-coordinator".encodeToByteArray())

        keyPackageStore(coordinatorA).apply {
            save("ref-1", "bundle-1".encodeToByteArray())
            save("ref/2", "bundle-2".encodeToByteArray())
            save("ref-3", "bundle-3".encodeToByteArray())
            delete("ref-3")
        }

        coordinatorStore().save(coordinators)

        handoffStore(account).save(true)
        handoffStore(handedBackAccount).save(true)
        handoffStore(handedBackAccount).save(false)

        // Folding: a rewrite, three loose segments and a fold, then a restart
        // that has to find the fold boundary in the header, not in memory.
        appendLog(3).apply { (1..5).forEach { append(logFile("folding"), "e$it") } }
        appendLog(3).apply { (6..7).forEach { append(logFile("folding"), "e$it") } }

        // Legacy: a headerless blob, rewritten into the segmented format by the next append.
        raw("logs").mkdirs()
        raw("logs/legacy").writeBytes(cipher.encrypt(legacyPlain("old-1", "old-2")))
        appendLog(200).append(logFile("legacy"), "new-1")

        // Torn tail: a half-written segment is cut off the file, and the next
        // append lands after the last good one.
        appendLog(200).apply {
            append(logFile("torn"), "t1")
            append(logFile("torn"), "t2")
        }
        raw("logs/torn").appendBytes(byteArrayOf(0, 0, 0, 0x40, 1, 2, 3))
        appendLog(200).apply {
            assertEquals(listOf("t1", "t2"), readAll(logFile("torn")))
            append(logFile("torn"), "t3")
        }

        appendLog(200).apply {
            append(logFile("rewritten"), "r1")
            append(logFile("rewritten"), "r2")
            rewrite(logFile("rewritten"), listOf("x", "y"))
            append(logFile("rewritten"), "z")
        }

        // Migration: replaces the account's tree wholesale, stale files included.
        raw("cordn/$migratedAccount/stale").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val started = migrationWrite(migrationSnapshot)
        assertEquals(
            listOf(
                CoordinatorConfig(coordinatorA, listOf(relay("wss://coord.example")), CoordinatorConfig.Origin.MANUAL),
                CoordinatorConfig(coordinatorB, listOf(relay("wss://other.example")), CoordinatorConfig.Origin.MANUAL),
            ),
            started,
        )
    }

    /** What every store reads back from the tree [drive] leaves — before an upgrade and after it. */
    private suspend fun assertReadsBack() {
        groupStore(coordinatorA).apply {
            assertEquals(listOf("../odd/gid ✓", "blanked", "g1"), listGroups().sorted())
            assertContentEquals("state-v2-longer".encodeToByteArray(), loadGroup("g1"))
            assertEquals(GroupCursor(0x0102030405060708L, 0x1112131415161718L), loadCursor("g1"))
            assertTrue(loadJoinedViaRequest("g1"))
            assertEquals(room, loadRoomState("g1"))
            assertEquals(echoes, loadEchoState("g1"))
            assertEquals(listOf(m1, m2, m3), loadMessages("g1"))
            assertEquals(CordnMessageSummary(m3, 3), loadMessageSummary("g1"))
            assertContentEquals(byteArrayOf(9, 8, 7), loadGroup("../odd/gid ✓"))
            assertFalse(loadJoinedViaRequest("blanked"))
            assertEquals(CordnRoomState(), loadRoomState("blanked"))
            assertEquals(EchoState(), loadEchoState("blanked"))
            assertNull(loadGroup("gone"))
            assertNull(loadCursor("gone"))
            assertEquals(emptyList(), loadMessages("gone"))
        }
        assertEquals(listOf("g1"), groupStore(coordinatorB).listGroups())
        assertContentEquals("other-coordinator".encodeToByteArray(), groupStore(coordinatorB).loadGroup("g1"))

        keyPackageStore(coordinatorA).apply {
            assertEquals(listOf("ref-1", "ref/2"), list().sorted())
            assertContentEquals("bundle-2".encodeToByteArray(), load("ref/2"))
            assertNull(load("ref-3"))
        }

        assertEquals(coordinators, coordinatorStore().load())
        assertTrue(handoffStore(account).load())
        assertFalse(handoffStore(handedBackAccount).load())

        assertEquals((1..7).map { "e$it" }, appendLog(3).readAll(logFile("folding")))
        assertEquals(listOf("old-1", "old-2", "new-1"), appendLog(200).readAll(logFile("legacy")))
        assertEquals(listOf("t1", "t2", "t3"), appendLog(200).readAll(logFile("torn")))
        assertEquals(listOf("x", "y", "z"), appendLog(200).readAll(logFile("rewritten")))

        // The migration document carries base64 of what the stores hold; the
        // strings are pinned too, since another device decodes them.
        val read = migrationRead(migrationSnapshot.groups.map { CoordinatorConfig(it.coordinatorPubKey, listOf(relay(it.coordinatorRelays.single()))) })
        assertEquals(migratedAccount, read.accountPubKey)
        assertEquals(
            listOf(
                CordnMigrationGroup(
                    coordinatorPubKey = coordinatorA,
                    coordinatorRelays = listOf("wss://coord.example/"),
                    gid = "mg-1",
                    clientStateBase64 = "bWxzLXN0YXRlLTE=",
                    cursor = 77,
                    roomStateBase64 = "AAEADm1pZ3JhdGVkIGRyYWZ0AAAAAAAAAEY=",
                    echoStateBase64 = "AAEAAAAIAAAAAAAAAEw=",
                    joinedViaRequest = true,
                    messages = listOf(CordnDeliveredMessageCodec.encode(m1), CordnDeliveredMessageCodec.encode(m2)),
                ),
                CordnMigrationGroup(
                    coordinatorPubKey = coordinatorB,
                    coordinatorRelays = listOf("wss://other.example/"),
                    gid = "mg-2",
                    clientStateBase64 = "AAEC//4=",
                    cursor = 0,
                    roomStateBase64 = "AAEAAAAAAAAAAAAA",
                    echoStateBase64 = "AAEAAAAA",
                    joinedViaRequest = false,
                    messages = emptyList(),
                ),
            ),
            read.groups.sortedBy { it.gid },
        )
        assertEquals(listOf(CordnCarriedKeyPackage(coordinatorA, "kp-ref", "YnVuZGxlIQ==")), read.keyPackages)
    }

    @Test
    fun `the stores write exactly the pinned files`() =
        runTest {
            drive()
            val actual = tree(rootFile)
            if (actual != EXPECTED) fail(mismatch(actual))
            assertReadsBack()
        }

    @Test
    fun `the pinned files read back as the values that wrote them`() =
        runTest {
            // The upgrade direction: files written by the build that shipped,
            // read by this one.
            EXPECTED.forEach { (path, hex) ->
                val target = File(rootFile, path.removeSuffix("/"))
                if (path.endsWith("/")) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.writeBytes(hex.hexToByteArray())
                }
            }
            assertReadsBack()
        }

    private fun tree(root: File): Map<String, String> =
        root
            .walkTopDown()
            .filter { it != root }
            .associate { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (file.isDirectory) "$relative/" to "" else relative to file.readBytes().toHexString()
            }.toSortedMap()

    /** The diff, plus the literal to paste if (and only if) the change is deliberate. */
    private fun mismatch(actual: Map<String, String>): String =
        buildString {
            appendLine("the on-disk format changed")
            (EXPECTED.keys - actual.keys).forEach { appendLine("  missing: $it") }
            (actual.keys - EXPECTED.keys).forEach { appendLine("  unexpected: $it") }
            actual.filter { (k, v) -> k in EXPECTED && EXPECTED[k] != v }.forEach { (k, _) -> appendLine("  bytes differ: $k") }
            appendLine("---- actual ----")
            actual.forEach { (path, hex) ->
                appendLine("                \"$path\" to")
                val chunks = hex.chunked(120).ifEmpty { listOf("") }
                appendLine(chunks.joinToString(" +\n                        ", prefix = "                    ", postfix = ",") { "\"$it\"" })
            }
        }

    companion object {
        private val EXPECTED: Map<String, String> =
            sortedMapOf(
                "cordn/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/Li4vb2RkL2dpZCDinJM/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/Li4vb2RkL2dpZCDinJM/state" to
                    "7c53525d",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/YmxhbmtlZA/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/YmxhbmtlZA/state" to
                    "7c5b",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/cursor" to
                    "7c5b58595e5f5c5d524b48494e4f4c4d42",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/echoes" to
                    "7c5a5b5a515a5239680c32381d0c315a5a4a5a5a5a5a5a5a5a595a5a5a5a5a5a5a5f",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/messages" to
                    "4d524d544c4f47330000000000000101000000ed7c5a5a5a5b5a5a5abe21782c78606b76783978606b76783f78602178333e7860783b6b6b6d6d6a6c" +
                    "696a6f6b3e3b6e686d6c393c3f6262396e696a6f3f623c3f3e38626c3f623e3f3e6f6863396d6b6f6c3f3b6f3e3b383c626b696a383e6e3e3b787678" +
                    "2a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6b76783133343e78606376782e3b3d2978600107767839" +
                    "35342e3f342e7860783c3328292e782727000000f27c5a5a5a5b5a5a5ab321782c78606b76783978606876783f78602178333e78607838696b6d6f3b" +
                    "636e3c3e6e6e3c6f69696c3f6e3f3e39393868396e3b6b6c6d636938696b3f38626e623f3e69686f69636d6c3b6e3e39683b636239396c6b3c3b7876" +
                    "782a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6876783133343e78606376782e3b3d29786001077678" +
                    "3935342e3f342e786078293f3935343e7ab8c6c9782727000000ed7c5a5a5a5b5a5a5abe21782c78606b76783978606976783f78602178333e786078" +
                    "6838636f3c3c3f3f6d633c3f6a6f6b3963396a6f6c6f6363393c396e6d6c393b3b3e3e696d3c6e3c6b6e3b3c396b626a6a636a6c6a38396d3b386e63" +
                    "396e6c697876782a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6976783133343e78606376782e3b3d29" +
                    "7860010776783935342e3f342e7860782e3233283e782727",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/newest" to
                    "7c5a5b5a5a5a595abe21782c78606b76783978606976783f78602178333e7860786838636f3c3c3f3f6d633c3f6a6f6b3963396a6f6c6f6363393c39" +
                    "6e6d6c393b3b3e3e696d3c6e3c6b6e3b3c396b626a6a636a6c6a38396d3b386e63396e6c697876782a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b" +
                    "2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6976783133343e78606376782e3b3d297860010776783935342e3f342e7860782e3233283e782727",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/room" to
                    "7c5a5b5a4a3299f33636357a3e283b3c2e7ab8c6c95a5a5a5a5a5a5a70",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/state" to
                    "7c292e3b2e3f772c68773635343d3f28",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/ZzE/via-request" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/keypackages/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/keypackages/cmVmLTE" to
                    "7c382f343e363f776b",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/keypackages/cmVmLzI" to
                    "7c382f343e363f7768",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/ZzE/" to
                    "",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/ZzE/state" to
                    "7c352e323f2877393535283e33343b2e3528",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/coordinators" to
                    "7c5a5b5a585a1a3838383838383838383838383838383838383838383838383838383838383838383838383838383838383838383838383838383838" +
                    "38383838383838383838385a5c171b140f1b165a5e0d3528315a5b5a4c2d292960757535343f743f223b372a363f74393537755a1a39393939393939" +
                    "3939393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939393939395a531d" +
                    "08150f0a05081f1c5a5a5a585a4c2d29296075752e2d35743f223b372a363f74393537755a422d29296075752e32283f3f743f223b372a363f743935" +
                    "3775",
                "cordn/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/handed-off" to
                    "",
                "cordn/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/cursor" to
                    "7c5a5a5a5a5a5a5a175a5a5a5a5a5a5a17",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/echoes" to
                    "7c5a5b5a5a5a525a5a5a5a5a5a5a16",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/messages" to
                    "4d524d544c4f47330000000000000101000000ed7c5a5a5a5b5a5a5abe21782c78606b76783978606b76783f78602178333e7860783b6b6b6d6d6a6c" +
                    "696a6f6b3e3b6e686d6c393c3f6262396e696a6f3f623c3f3e38626c3f623e3f3e6f6863396d6b6f6c3f3b6f3e3b383c626b696a383e6e3e3b787678" +
                    "2a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6b76783133343e78606376782e3b3d2978600107767839" +
                    "35342e3f342e7860783c3328292e782727000000f27c5a5a5a5b5a5a5ab321782c78606b76783978606876783f78602178333e78607838696b6d6f3b" +
                    "636e3c3e6e6e3c6f69696c3f6e3f3e39393868396e3b6b6c6d636938696b3f38626e623f3e69686f69636d6c3b6e3e39683b636239396c6b3c3b7876" +
                    "782a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6876783133343e78606376782e3b3d29786001077678" +
                    "3935342e3f342e786078293f3935343e7ab8c6c9782727",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/newest" to
                    "7c5a5b5a5a5a585ab321782c78606b76783978606876783f78602178333e78607838696b6d6f3b636e3c3e6e6e3c6f69696c3f6e3f3e39393868396e" +
                    "3b6b6c6d636938696b3f38626e623f3e69686f69636d6c3b6e3e39683b636239396c6b3c3b7876782a2f38313f237860783b3b3b3b3b3b3b3b3b3b3b" +
                    "3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b78767839283f3b" +
                    "2e3f3e053b2e78606b6d6f6d6a6a6a6a6a6876783133343e78606376782e3b3d297860010776783935342e3f342e786078293f3935343e7ab8c6c978" +
                    "2727",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/room" to
                    "7c5a5b5a5437333d283b2e3f3e7a3e283b3c2e5a5a5a5a5a5a5a1c",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/state" to
                    "7c37362977292e3b2e3f776b",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/groups/bWctMQ/via-request" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/keypackages/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/keypackages/a3AtcmVm" to
                    "7c382f343e363f7b",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/bWctMg/" to
                    "",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/bWctMg/cursor" to
                    "7c5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a",
                "cordn/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc/groups/bWctMg/state" to
                    "7c5a5b58a5a4",
                "logs/" to
                    "",
                "logs/folding" to
                    "4d524d544c4f473300000000000000550000000b7c5a5a5a5b5a5a5a583f6b000000177c5a5a5a595a5a5a583f685a5a5a583f695a5a5a583f6e0000" +
                    "00177c5a5a5a595a5a5a583f6f5a5a5a583f6c5a5a5a583f6d",
                "logs/legacy" to
                    "4d524d544c4f47330000000000000034000000207c5a5a5a595a5a5a5f35363e776b5a5a5a5f35363e77685a5a5a5f343f2d776b",
                "logs/rewritten" to
                    "4d524d544c4f473300000000000000230000000f7c5a5a5a585a5a5a5b225a5a5a5b230000000a7c5a5a5a5b5a5a5a5b20",
                "logs/torn" to
                    "4d524d544c4f4733000000000000001f0000000b7c5a5a5a5b5a5a5a582e6b0000000b7c5a5a5a5b5a5a5a582e680000000b7c5a5a5a5b5a5a5a582e" +
                    "69",
            )
    }
}

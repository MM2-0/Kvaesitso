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

import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.client.CordnRoomState
import com.vitorpamplona.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSource
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Source
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The on-disk cordn stores.
 *
 * Driven through a fake cipher rather than the Android KeyStore, which is the
 * point of the [CordnBlobCipher] seam: everything below the cipher — the
 * layout, the key encoding, the atomic write, the account/coordinator scoping
 * — is ordinary code that can fail in ordinary ways, and none of it is
 * testable on a device-only primitive. The fake really transforms the bytes,
 * so "the plaintext never reached the disk" is a claim these tests can make.
 */
class FileCordnStoresTest {
    /** XOR: reversible, and unmistakably not the input. */
    private class XorCipher : CordnBlobCipher {
        var encryptCalls = 0

        override fun encrypt(bytes: ByteArray): ByteArray {
            encryptCalls++
            return ByteArray(bytes.size) { (bytes[it].toInt() xor MASK).toByte() }
        }

        override fun decrypt(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor MASK).toByte() }

        companion object {
            const val MASK = 0x5A
        }
    }

    private val root =
        File.createTempFile("cordn-store", "").also {
            it.delete()
            it.mkdirs()
        }
    private val rootPath = root.toOkioPath()
    private val cipher = XorCipher()

    private val account = "a".repeat(64)
    private val coordinator = "b".repeat(64)

    private fun dir() = CordnStorageLayout.directoryFor(rootPath, account, coordinator)

    private fun groups() = FileCordnGroupStore(dir(), cipher)

    private fun coordinators() = FileCordnCoordinatorStore(CordnStorageLayout.accountDirectoryFor(rootPath, account), cipher)

    private fun relay(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    private fun keyPackages() = FileCordnKeyPackageStore(dir(), cipher)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a group round-trips through a restart`() =
        runTest {
            val state = byteArrayOf(1, 2, 3, 4, 5)
            groups().saveGroup("g1", state)

            // A new instance over the same directory: what a relaunch is.
            assertContentEquals(state, groups().loadGroup("g1"))
            assertEquals(listOf("g1"), groups().listGroups())
        }

    @Test
    fun `the plaintext never reaches the disk`() =
        runTest {
            // The one claim the whole class exists to make. An MlsGroupState is
            // the ratchet tree and every epoch secret; on disk in the clear it
            // is every message the group ever sent.
            val state = "ratchet-tree-and-epoch-secrets".encodeToByteArray()
            groups().saveGroup("g1", state)

            val onDisk =
                dir()
                    .toFile()
                    .walkTopDown()
                    .filter { it.isFile }
                    .toList()
            assertTrue(onDisk.isNotEmpty(), "nothing was written at all")
            onDisk.forEach {
                val raw = it.readBytes()
                assertTrue(!raw.contentEquals(state), "${it.name} holds the plaintext")
                assertTrue(
                    !raw.decodeToString().contains("ratchet-tree"),
                    "${it.name} holds recognisable plaintext",
                )
            }
        }

    @Test
    fun `a gid that is a path traversal is stored, not rejected`() =
        runTest {
            // A cordn gid is caller-chosen and the coordinator never interprets
            // it (spec/00.md §4), so "../../etc/passwd" is a gid the protocol
            // allows. Marmot's store validates hex because a Marmot group id is
            // a hash; doing that here would refuse legitimate groups. Encoding
            // accepts it AND keeps it inside our directory.
            val hostile = "../../../etc/passwd"
            groups().saveGroup(hostile, byteArrayOf(9))

            assertEquals(listOf(hostile), groups().listGroups(), "the gid must come back verbatim")
            assertContentEquals(byteArrayOf(9), groups().loadGroup(hostile))

            val escaped = File(root, "cordn/etc").exists() || File(root.parentFile, "etc").exists()
            assertTrue(!escaped, "the write escaped the store directory")
            assertTrue(dir().toFile().walkTopDown().any { it.isFile }, "it was not written anywhere at all")
        }

    @Test
    fun `gids that differ only in case are different groups`() =
        runTest {
            // Base64url is case-sensitive; a case-folding encoding would merge
            // these two and let one group's state answer for the other.
            groups().saveGroup("Group", byteArrayOf(1))
            groups().saveGroup("group", byteArrayOf(2))

            assertContentEquals(byteArrayOf(1), groups().loadGroup("Group"))
            assertContentEquals(byteArrayOf(2), groups().loadGroup("group"))
            assertEquals(2, groups().listGroups().size)
        }

    @Test
    fun `two coordinators serving the same gid do not collide`() =
        runTest {
            // The rule the layout exists for. Two coordinators can both serve
            // gid "shared" as unrelated groups with different ratchet trees.
            val other = CordnStorageLayout.directoryFor(rootPath, account, "c".repeat(64))
            FileCordnGroupStore(dir(), cipher).saveGroup("shared", byteArrayOf(1))
            FileCordnGroupStore(other, cipher).saveGroup("shared", byteArrayOf(2))

            assertContentEquals(byteArrayOf(1), groups().loadGroup("shared"))
            assertContentEquals(byteArrayOf(2), FileCordnGroupStore(other, cipher).loadGroup("shared"))
        }

    @Test
    fun `two accounts on one device do not see each other`() =
        runTest {
            val theirs = CordnStorageLayout.directoryFor(rootPath, "d".repeat(64), coordinator)
            groups().saveGroup("g1", byteArrayOf(1))

            assertTrue(FileCordnGroupStore(theirs, cipher).listGroups().isEmpty())
            assertNull(FileCordnGroupStore(theirs, cipher).loadGroup("g1"))
        }

    @Test
    fun `a non-hex pubkey never becomes a directory`() {
        assertFailsWith<IllegalArgumentException> { CordnStorageLayout.directoryFor(rootPath, "../escape", coordinator) }
        assertFailsWith<IllegalArgumentException> { CordnStorageLayout.directoryFor(rootPath, account, "../escape") }
    }

    @Test
    fun `deleting a group takes its cursor with it`() =
        runTest {
            // Otherwise a later re-join of the same gid resumes from a cursor
            // belonging to a group it is no longer in, and silently skips
            // everything before it.
            groups().saveGroup("g1", byteArrayOf(1))
            groups().saveCursor("g1", GroupCursor(fetchCursor = 42, lastCursor = 99))

            groups().deleteGroup("g1")

            assertNull(groups().loadGroup("g1"))
            assertNull(groups().loadCursor("g1"), "the cursor outlived the group")
            assertTrue(groups().listGroups().isEmpty())
        }

    @Test
    fun `a cursor round-trips both of its fields`() =
        runTest {
            // They are not the same number and conflating them is invisible
            // until a fetch re-reads or skips a stretch of the stream.
            groups().saveCursor("g1", GroupCursor(fetchCursor = 7, lastCursor = 1234567890123L))

            val restored = assertNotNull(groups().loadCursor("g1"))
            assertEquals(7L, restored.fetchCursor)
            assertEquals(1234567890123L, restored.lastCursor)
        }

    @Test
    fun `a group with no state yet is not listed`() =
        runTest {
            // A cursor alone means a directory exists with nothing restorable
            // in it; reporting it as a group would make restore() build an
            // empty MlsGroup for a gid we do not actually hold.
            groups().saveCursor("ghost", GroupCursor(fetchCursor = 1))

            assertTrue(groups().listGroups().isEmpty())
        }

    @Test
    fun `a key package bundle round-trips and can be withdrawn`() =
        runTest {
            val bundle = byteArrayOf(7, 7, 7)
            keyPackages().save("ref1", bundle)

            assertContentEquals(bundle, keyPackages().load("ref1"))
            assertEquals(listOf("ref1"), keyPackages().list())

            keyPackages().delete("ref1")
            assertNull(keyPackages().load("ref1"))
            assertTrue(keyPackages().list().isEmpty())
        }

    @Test
    fun `a half-written temp file is not served as a bundle`() =
        runTest {
            // atomicWrite leaves one behind if the process dies between write
            // and rename. Listing it would hand joinPendingWelcomes a ref that
            // decodes to nothing and burn the Welcome it was meant to open.
            //
            // What excludes it is the encoding, not a name check: '.' is not in
            // the base64url alphabet. So this test is really a guard on the
            // encoding — swap it for something that passes names through and
            // this fails, which is the regression worth catching.
            keyPackages().save("ref1", byteArrayOf(1))
            File(dir().toFile(), "keypackages/${CordnStorageLayout.encodeKey("ref2")}.tmp").writeBytes(byteArrayOf(2))

            assertEquals(listOf("ref1"), keyPackages().list())
        }

    @Test
    fun `a stray file in the directory does not break the listing`() =
        runTest {
            // Ignoring one unreadable name is better than failing the listing
            // and hiding every real group behind it.
            groups().saveGroup("g1", byteArrayOf(1))
            File(dir().toFile(), "groups/not-base64-@@@").mkdirs()
            File(dir().toFile(), "groups/not-base64-@@@/state").writeBytes(byteArrayOf(0))

            assertEquals(listOf("g1"), groups().listGroups())
        }

    @Test
    fun `an empty store lists nothing rather than failing`() =
        runTest {
            assertTrue(groups().listGroups().isEmpty())
            assertTrue(keyPackages().list().isEmpty())
            assertNull(groups().loadGroup("nope"))
            assertNull(groups().loadCursor("nope"))
            assertNull(keyPackages().load("nope"))
        }

    @Test
    fun `concurrent saves of different groups all survive`() =
        runTest {
            // Dispatchers.IO is a pool, and a sync loop saving several groups
            // at once is the ordinary case.
            val store = groups()
            withContext(Dispatchers.IO) {
                (1..24).map { async { store.saveGroup("g$it", byteArrayOf(it.toByte())) } }.awaitAll()
            }

            assertEquals(24, store.listGroups().size)
            (1..24).forEach { assertContentEquals(byteArrayOf(it.toByte()), store.loadGroup("g$it")) }
        }

    /**
     * The file-sharing rules Windows applies and POSIX does not: a file being
     * renamed over cannot be opened, and a file someone is reading cannot be
     * renamed over. Each window is widened so a race lands every run rather
     * than once a week on the Windows CI job.
     */
    private class WindowsSharingFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
        private val replacing = ConcurrentHashMap.newKeySet<Path>()
        private val reading = ConcurrentHashMap<Path, AtomicInteger>()
        val refusedMoves = AtomicInteger()

        override fun atomicMove(
            source: Path,
            target: Path,
        ) {
            if ((reading[target]?.get() ?: 0) > 0) {
                refusedMoves.incrementAndGet()
                throw IOException("$target: Access is denied")
            }
            replacing += target
            try {
                Thread.sleep(1)
                super.atomicMove(source, target)
            } finally {
                replacing -= target
            }
        }

        override fun source(file: Path): Source {
            if (file in replacing) {
                throw FileNotFoundException("$file (The process cannot access the file because it is being used by another process)")
            }
            val readers = reading.getOrPut(file) { AtomicInteger() }
            readers.incrementAndGet()
            Thread.sleep(1)
            return object : ForwardingSource(super.source(file)) {
                override fun close() {
                    readers.decrementAndGet()
                    super.close()
                }
            }
        }
    }

    @Test
    fun `a read racing a save of the same group neither fails nor forces a copy`() =
        runTest {
            // What the sync loop and a backup export do at once: one store
            // persisting the group, another, separate instance reading it off
            // disk. On Windows the read failed with a sharing violation, or the
            // rename was refused and the write fell back to a non-atomic copy.
            val fs = WindowsSharingFileSystem()
            val writer = FileCordnGroupStore(dir(), cipher, fs)
            val reader = FileCordnGroupStore(dir(), cipher, fs)
            writer.saveGroup("g1", byteArrayOf(0))

            withContext(Dispatchers.IO) {
                listOf(
                    async { repeat(100) { writer.saveGroup("g1", byteArrayOf(it.toByte())) } },
                    async { repeat(100) { assertNotNull(reader.loadGroup("g1")) } },
                ).awaitAll()
            }

            assertEquals(0, fs.refusedMoves.get(), "a rename was refused, so the write fell back to a copy")
        }

    @Test
    fun `a rewritten group leaves no temp file behind`() =
        runTest {
            val store = groups()
            store.saveGroup("g1", byteArrayOf(1))
            store.saveGroup("g1", byteArrayOf(2, 2))

            assertContentEquals(byteArrayOf(2, 2), store.loadGroup("g1"))
            assertTrue(
                dir().toFile().walkTopDown().none { it.name.endsWith(".tmp") },
                "a temp file survived the rename",
            )
        }

    @Test
    fun `the coordinator list survives a relaunch, encrypted`() =
        runTest {
            val configs =
                listOf(
                    CoordinatorConfig(coordinator, listOf(relay("wss://one.example.com")), CoordinatorConfig.Origin.MANUAL, "Work"),
                    CoordinatorConfig("c".repeat(64), listOf(relay("wss://two.example.com"), relay("wss://three.example.com")), CoordinatorConfig.Origin.GROUP_REF),
                )
            coordinators().save(configs)

            assertEquals(configs, coordinators().load())
            val onDisk = File(CordnStorageLayout.accountDirectoryFor(rootPath, account).toFile(), "coordinators").readBytes()
            assertFalse(onDisk.decodeToString().contains(coordinator), "the pubkey went to disk in the clear")
        }

    @Test
    fun `a label with a separator in it round-trips`() =
        runTest {
            // The reason the format is length-prefixed rather than delimited:
            // a label is free text a user typed, so any separator a line-based
            // format picked is one they can put in it.
            val messy = "tab\there\nnewline\u0000nul"
            coordinators().save(listOf(CoordinatorConfig(coordinator, listOf(relay("wss://one.example.com")), label = messy)))

            assertEquals(messy, coordinators().load().single().label)
        }

    @Test
    fun `an account with no stored list gets an empty one, not a failure`() =
        runTest {
            assertEquals(emptyList(), coordinators().load())
        }

    @Test
    fun `an unreadable list loses the coordinators rather than the login`() =
        runTest {
            coordinators().save(listOf(CoordinatorConfig(coordinator, listOf(relay("wss://one.example.com")))))
            File(CordnStorageLayout.accountDirectoryFor(rootPath, account).toFile(), "coordinators").writeBytes(byteArrayOf(9, 9, 9))

            assertEquals(emptyList(), coordinators().load())
        }

    @Test
    fun `the coordinator list sits above the per-coordinator directories, so forgetting one keeps the rest`() =
        runTest {
            // If it lived inside a coordinator's own directory, removing that
            // coordinator would delete the record of every other one with it.
            val listFile = File(CordnStorageLayout.accountDirectoryFor(rootPath, account).toFile(), "coordinators")
            assertEquals(dir().toFile().parentFile, listFile.parentFile)
        }

    @Test
    fun `purging one coordinator leaves another coordinator's groups untouched`() =
        runTest {
            // What CordnRuntime.purge deletes. It has to be the coordinator's
            // own directory and nothing above it: a gid is unique only within
            // one coordinator, so two of them routinely hold state for the
            // same gid and deleting a level too high takes both.
            val other = "e".repeat(64)
            groups().saveGroup("shared-gid", byteArrayOf(1, 2, 3))
            FileCordnGroupStore(CordnStorageLayout.directoryFor(rootPath, account, other), cipher)
                .saveGroup("shared-gid", byteArrayOf(4, 5, 6))

            CordnStorageLayout.directoryFor(rootPath, account, coordinator).toFile().deleteRecursively()

            assertNull(groups().loadGroup("shared-gid"))
            assertContentEquals(
                byteArrayOf(4, 5, 6),
                FileCordnGroupStore(CordnStorageLayout.directoryFor(rootPath, account, other), cipher).loadGroup("shared-gid"),
            )
        }

    @Test
    fun `purging a coordinator leaves the account's coordinator list alone`() =
        runTest {
            // The list names the coordinator being purged, so it has to be
            // rewritten by whoever purges -- but it must not be destroyed by
            // the delete itself, or purging one coordinator would forget every
            // other one with it.
            coordinators().save(listOf(CoordinatorConfig(coordinator, listOf(relay("wss://one.example.com")))))
            groups().saveGroup("gid", byteArrayOf(1))

            CordnStorageLayout.directoryFor(rootPath, account, coordinator).toFile().deleteRecursively()

            assertEquals(1, coordinators().load().size)
        }

    @Test
    fun `a join-request marker is written once and goes with its group`() =
        runTest {
            val store = groups()
            store.saveGroup("gid", byteArrayOf(1))
            assertFalse(store.loadJoinedViaRequest("gid"))

            store.saveJoinedViaRequest("gid")
            store.saveJoinedViaRequest("gid")
            assertTrue(store.loadJoinedViaRequest("gid"))

            // A later re-join of the same gid is a different admission, so the
            // old marker must not outlive the group it described.
            store.deleteGroup("gid")
            assertFalse(store.loadJoinedViaRequest("gid"))
        }

    @Test
    fun `a draft is encrypted at rest, like the messages it was going to become`() =
        runTest {
            val store = groups()
            store.saveGroup("gid", byteArrayOf(1))
            store.saveRoomState("gid", CordnRoomState(draft = "the secret plan", lastReadCursor = 7))

            assertEquals(CordnRoomState("the secret plan", 7), store.loadRoomState("gid"))
            val onDisk = File(dir().toFile(), "groups/${CordnStorageLayout.encodeKey("gid")}/room").readBytes()
            assertFalse(onDisk.decodeToString().contains("the secret plan"), "a draft went to disk in the clear")
        }

    @Test
    fun `clearing a draft removes the file rather than blanking it`() =
        runTest {
            // Overwriting in place would leave the old plaintext in whatever
            // the filesystem still holds; there is nothing to keep once both
            // fields are empty.
            val store = groups()
            store.saveGroup("gid", byteArrayOf(1))
            store.saveRoomState("gid", CordnRoomState(draft = "typed"))
            store.saveRoomState("gid", CordnRoomState())

            assertFalse(File(dir().toFile(), "groups/${CordnStorageLayout.encodeKey("gid")}/room").exists())
            assertEquals(CordnRoomState(), store.loadRoomState("gid"))
        }

    @Test
    fun `an unreadable room file costs the draft, never the room`() =
        runTest {
            val store = groups()
            store.saveGroup("gid", byteArrayOf(1))
            store.saveRoomState("gid", CordnRoomState(draft = "typed", lastReadCursor = 3))
            File(dir().toFile(), "groups/${CordnStorageLayout.encodeKey("gid")}/room").writeBytes(byteArrayOf(7, 7, 7))

            assertEquals(CordnRoomState(), store.loadRoomState("gid"))
            assertNotNull(store.loadGroup("gid"))
        }
}

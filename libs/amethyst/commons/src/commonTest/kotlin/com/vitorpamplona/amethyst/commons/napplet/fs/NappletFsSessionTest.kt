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
package com.vitorpamplona.amethyst.commons.napplet.fs

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest.FsPickKind
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletFsSessionTest {
    private var now = 1_000_000L
    private val port = FakeNappletFilePort()
    private val session = NappletFsSession(port) { now }

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    private fun b64(data: ByteArray) = Base64.Default.encode(data)

    private suspend fun pickOpen(
        name: String,
        data: ByteArray,
        kind: FsPickKind = FsPickKind.FILE,
    ): NappletResponse.FsEntry {
        val handle = "content://docs/$name/${port.disk.size}"
        port.disk[handle] = data
        port.opens.addLast(listOf(NappletPickedFile(handle, name, data.size.toLong())))
        return assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(kind))).entries.single()
    }

    private suspend fun pickSave(name: String): Pair<String, NappletResponse.FsEntry> {
        val handle = "/home/someone/Pictures/$name"
        port.saves.addLast(NappletPickedFile(handle, name, writable = true))
        return handle to assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(FsPickKind.SAVE_FILE))).entries.single()
    }

    private fun assertFails(
        code: String,
        response: NappletResponse,
    ) = assertEquals(NappletResponse.FsFailure(code), response)

    @Test
    fun nothingIsVisibleBeforeAPick() =
        runTest {
            val info = assertIs<NappletResponse.FsInfoResult>(session.execute(NappletRequest.FsInfo))
            assertTrue(info.roots.isEmpty())
            assertEquals(NappletFsSession.MAX_READ_BYTES, info.maxReadBytes)
            assertEquals(NappletFsSession.MAX_WRITE_BYTES, info.maxWriteBytes)
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsRead("/picked/anything.png")))
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsStat("/")))
        }

    @Test
    fun aPickedFileReadsInChunksUnderAVirtualPath() =
        runTest {
            val data = bytes(150_000)
            val entry = pickOpen("photo.png", data)
            assertEquals("/picked/photo.png", entry.path)
            assertEquals("photo.png", entry.name)
            assertEquals(listOf("read"), entry.permissions)
            assertEquals(150_000L, entry.size)

            val info = assertIs<NappletResponse.FsInfoResult>(session.execute(NappletRequest.FsInfo))
            assertEquals(listOf(NappletResponse.FsRoot("/picked", NappletFsSession.ROOT_NAME, listOf("read"))), info.roots)

            val first = assertIs<NappletResponse.FsReadResult>(session.execute(NappletRequest.FsRead(entry.path, 0, 65_536)))
            assertEquals(65_536, first.bytes.size)
            assertFalse(first.eof)
            assertEquals(150_000L, first.size)
            val last = assertIs<NappletResponse.FsReadResult>(session.execute(NappletRequest.FsRead(entry.path, 131_072, 65_536)))
            assertEquals(150_000 - 131_072, last.bytes.size)
            assertTrue(last.eof)
            assertContentEquals(data.copyOfRange(131_072, 150_000), last.bytes)

            val past = assertIs<NappletResponse.FsReadResult>(session.execute(NappletRequest.FsRead(entry.path, 200_000, 10)))
            assertEquals(0, past.bytes.size)
            assertTrue(past.eof)

            val meta = assertIs<NappletResponse.FsMetadata>(session.execute(NappletRequest.FsStat(entry.path)))
            assertEquals(150_000L, meta.entry.size)
            assertFails(NappletFsError.NOT_A_DIRECTORY, session.execute(NappletRequest.FsList(entry.path)))
        }

    @Test
    fun eachChunkOpensTheFileOnce() =
        runTest {
            val entry = pickOpen("big.psd", bytes(200_000))
            var offset = 0L
            var chunks = 0
            while (true) {
                val chunk = assertIs<NappletResponse.FsReadResult>(session.execute(NappletRequest.FsRead(entry.path, offset, 65_536)))
                chunks++
                offset += chunk.bytes.size
                if (chunk.eof) break
            }
            assertEquals(200_000L, offset)
            assertEquals(chunks, port.reads)
        }

    @Test
    fun aMultiSelectNeverPassesTheEntryCap() =
        runTest {
            port.opens.addLast(List(NappletFsSession.MAX_ENTRIES - 1) { NappletPickedFile("h$it", "f$it.txt", 0) })
            assertEquals(NappletFsSession.MAX_ENTRIES - 1, assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(FsPickKind.FILES))).entries.size)
            port.opens.addLast(List(5) { NappletPickedFile("g$it", "g$it.txt", 0) })
            assertEquals(1, assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(FsPickKind.FILES))).entries.size)
            assertFails(NappletFsError.QUOTA_EXCEEDED, session.execute(NappletRequest.FsPick(FsPickKind.FILES)))
            assertEquals(2, port.pickerShown)
        }

    @Test
    fun readsAreBoundedAndPathsChecked() =
        runTest {
            val entry = pickOpen("a.txt", bytes(10))
            assertFails(NappletFsError.TOO_LARGE, session.execute(NappletRequest.FsRead(entry.path, 0, NappletFsSession.MAX_READ_BYTES + 1L)))
            assertFails(NappletFsError.INVALID_PATH, session.execute(NappletRequest.FsRead("/picked/../a.txt")))
            assertFails(NappletFsError.INVALID_PATH, session.execute(NappletRequest.FsRead("picked/a.txt")))
            // The platform handle is never a path the napplet can name.
            assertFails(NappletFsError.INVALID_PATH, session.execute(NappletRequest.FsRead(port.disk.keys.single())))
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsRead("/picked/A.txt")))
        }

    @Test
    fun anOpenedFileIsReadOnly() =
        runTest {
            val entry = pickOpen("a.txt", bytes(10))
            assertFails(NappletFsError.PERMISSION_DENIED, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(3)))))
            assertEquals(
                10,
                port.disk.values
                    .single()
                    .size,
            )
        }

    @Test
    fun aSaveTargetIsWrittenInChunksFromNothing() =
        runTest {
            val (handle, entry) = pickSave("export.png")
            port.disk[handle] = bytes(999) // whatever was there before the user chose to overwrite it
            assertEquals("/picked/export.png", entry.path)
            assertEquals(listOf("write", "create"), entry.permissions)
            assertNull(entry.size)
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsStat(entry.path)))
            // Granted write, not read.
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsRead(entry.path)))

            val png = bytes(100_000)
            var offset = 0
            while (offset < png.size) {
                val chunk = png.copyOfRange(offset, minOf(png.size, offset + 49_152))
                val written = assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, b64(chunk), if (offset == 0) "replace" else "append")))
                assertEquals(chunk.size, written.bytesWritten)
                assertEquals((offset + chunk.size).toLong(), written.size)
                offset += chunk.size
            }
            assertContentEquals(png, port.disk[handle])
            assertEquals(100_000L, assertIs<NappletResponse.FsMetadata>(session.execute(NappletRequest.FsStat(entry.path))).entry.size)
            assertFails(NappletFsError.PERMISSION_DENIED, session.execute(NappletRequest.FsRead(entry.path)))
        }

    @Test
    fun theFirstWriteToASaveTargetStartsTheFileOver() =
        runTest {
            val (handle, entry) = pickSave("log.txt")
            port.disk[handle] = bytes(500)
            session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), "append"))
            assertEquals(4, port.disk[handle]!!.size)
            assertFails(NappletFsError.CONFLICT, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), ifAbsent = true)))
        }

    @Test
    fun writesFollowTheSpecPreconditions() =
        runTest {
            val (_, entry) = pickSave("x.bin")
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), "patch", offset = 8)))
            assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), ifAbsent = true)))
            assertFails(NappletFsError.INVALID_DATA, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), "replace", offset = 0)))
            assertFails(NappletFsError.INVALID_DATA, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), "patch")))
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), "truncate")))
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(4)), ifRevision = "r1")))
            assertFails(NappletFsError.TOO_LARGE, session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(NappletFsSession.MAX_WRITE_BYTES + 1)))))
            val patched = assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(2)), "patch", offset = 6)))
            assertEquals(8L, patched.size)
        }

    @Test
    fun onlyCanonicalBase64IsWritten() =
        runTest {
            val (handle, entry) = pickSave("x.txt")
            for (bad in listOf("SGVsbG8", "SGVsbG8=\n", "SGV sbG8=", "-_8=", "SGVsbG9=", "@@@@")) {
                assertFails(NappletFsError.INVALID_DATA, session.execute(NappletRequest.FsWrite(entry.path, bad)))
            }
            assertNull(port.disk[handle])
            val empty = assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, "")))
            assertEquals(0, empty.bytesWritten)
            assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, "SGVsbG8=", "append")))
            assertEquals("Hello", port.disk[handle]!!.decodeToString())
        }

    @Test
    fun aDismissedPickerIsCancelledAndRepeatedOnesBackOff() =
        runTest {
            port.opens.addLast(null)
            assertFails(NappletFsError.CANCELLED, session.execute(NappletRequest.FsPick(FsPickKind.FILE)))
            // One dismissal may be a slip: the retry still opens the picker.
            port.opens.addLast(null)
            assertFails(NappletFsError.CANCELLED, session.execute(NappletRequest.FsPick(FsPickKind.FILE)))
            assertEquals(2, port.pickerShown)
            // From the second in a row, the napplet waits before it can bring it back.
            assertFails(NappletFsError.POLICY_DENIED, session.execute(NappletRequest.FsPick(FsPickKind.FILE)))
            assertEquals(2, port.pickerShown)
            now += NappletFsSession.DISMISS_BACKOFF_MS
            pickOpen("a.txt", bytes(1))
            assertEquals(3, port.pickerShown)
        }

    @Test
    fun onePickerAtATime() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            port.gate = gate
            port.opens.addLast(listOf(NappletPickedFile("h", "a.txt", 0)))
            val first = async { session.execute(NappletRequest.FsPick(FsPickKind.FILE)) }
            yield()
            assertFails(NappletFsError.CONFLICT, session.execute(NappletRequest.FsPick(FsPickKind.SAVE_FILE)))
            gate.complete(Unit)
            assertIs<NappletResponse.FsPicked>(first.await())
        }

    @Test
    fun pickFileHoldsOneFileAndPickFilesAll() =
        runTest {
            port.opens.addLast(listOf(NappletPickedFile("h1", "a.png", 1), NappletPickedFile("h2", "a.png", 2)))
            assertEquals(1, assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(FsPickKind.FILE))).entries.size)
            port.opens.addLast(listOf(NappletPickedFile("h1", "a.png", 1), NappletPickedFile("h2", "a.png", 2)))
            val many = assertIs<NappletResponse.FsPicked>(session.execute(NappletRequest.FsPick(FsPickKind.FILES))).entries
            assertEquals(listOf("/picked/a (2).png", "/picked/a (3).png"), many.map { it.path })
        }

    @Test
    fun writeIsGrantedOnAnOpenOnlyWhenAskedAndPossible() =
        runTest {
            port.disk["h"] = bytes(4)
            port.opens.addLast(listOf(NappletPickedFile("h", "a.txt", 4, writable = true)))
            val asked = NappletRequest.FsPick(FsPickKind.FILE, NappletFsPickOptions(permissions = listOf("read", "write", "delete")))
            val entry = assertIs<NappletResponse.FsPicked>(session.execute(asked)).entries.single()
            assertEquals(listOf("read", "write"), entry.permissions)
            assertIs<NappletResponse.FsWritten>(session.execute(NappletRequest.FsWrite(entry.path, b64(bytes(2)), "append")))
            assertEquals(6, port.disk["h"]!!.size)

            port.opens.addLast(listOf(NappletPickedFile("g", "b.txt", 4, writable = false)))
            assertEquals(listOf("read"), assertIs<NappletResponse.FsPicked>(session.execute(asked)).entries.single().permissions)
        }

    @Test
    fun theRestOfTheApiIsHonestAboutWhatItLacks() =
        runTest {
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsPick(FsPickKind.DIRECTORY)))
            assertEquals(0, port.pickerShown)
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsMkdir("/picked/x")))
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsRemove("/picked/x")))
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsMove("/picked/x", "/picked/y")))
            assertFails(NappletFsError.UNSUPPORTED, session.execute(NappletRequest.FsWatch("/picked")))
            assertEquals(NappletResponse.Done, session.execute(NappletRequest.FsUnwatch("w1")))
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsList("/picked")))
        }

    @Test
    fun closingForgetsEveryPick() =
        runTest {
            val entry = pickOpen("a.txt", bytes(3))
            session.close()
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsRead(entry.path)))
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsPick(FsPickKind.FILE)))
        }

    @Test
    fun portFailuresBecomeCodesWithoutDetail() =
        runTest {
            val entry = pickOpen("a.txt", bytes(3))
            port.disk.clear()
            assertFails(NappletFsError.NOT_FOUND, session.execute(NappletRequest.FsRead(entry.path)))
            val broken =
                NappletFsSession(
                    object : NappletFilePort by port {
                        override suspend fun pickOpen(
                            options: NappletFsPickOptions,
                            multiple: Boolean,
                        ): List<NappletPickedFile>? = throw IllegalStateException("/home/someone/secret")
                    },
                )
            assertFails(NappletFsError.IO_ERROR, broken.execute(NappletRequest.FsPick(FsPickKind.FILE)))
        }
}

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
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * One running napplet's NAP-FS view: the files its user picked, and every `fs.*` operation on them.
 *
 * Amethyst offers NAP-FS as user-mediated picks only. The napplet sees nothing of the device until
 * the user picks a file for it; each pick appears under [NappletFsPaths.PICKED_ROOT] with the
 * permissions that pick grants, for as long as this object lives (the host makes one per running
 * surface and drops it when the surface closes, so picks never outlive a session):
 *
 * - `pickFile` / `pickFiles` grant `read` (and `write` when asked for and the file allows it).
 * - `pickSaveFile` grants `write` and `create`. Its entry is absent until the napplet writes it,
 *   whatever was at the chosen place before, so the first write always starts the file afresh.
 * - `info`, `stat`, `read`, `write` work on those entries. There are no directories: `list` says
 *   `not-a-directory` for a picked file and `not-found` otherwise, and `pickDirectory`, `mkdir`,
 *   `remove`, `move` and `watch` are `unsupported`; `unwatch` is the no-op the spec allows.
 *
 * Any path the session never handed out is `not-found`, whether or not something exists there. One
 * picker at a time ([NappletFsError.CONFLICT] for another), and a napplet that keeps reopening the
 * picker the user keeps dismissing waits longer each time ([NappletFsError.POLICY_DENIED]). Byte
 * operations run one at a time, so appends never interleave.
 *
 * Consent is the broker's: it only lets a pick reach here once the user allowed the app to ask.
 */
class NappletFsSession(
    private val port: NappletFilePort,
    private val nowMillis: () -> Long = { TimeUtils.nowMillis() },
) {
    private class Entry(
        val path: String,
        val handle: String,
        val permissions: Set<NappletFsPermission>,
        // A save target is absent until its first write; an opened file is present from the pick.
        var present: Boolean,
    )

    // Guards [entries] and serializes the byte operations.
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    // One picker at a time; the backoff after dismissals lives under it too.
    private val pickLock = Mutex()
    private var dismissals = 0
    private var quietUntil = 0L

    @Volatile
    private var closed = false

    suspend fun execute(request: NappletRequest.Fs): NappletResponse =
        try {
            if (closed) {
                fail(NappletFsError.NOT_FOUND)
            } else {
                when (request) {
                    is NappletRequest.FsInfo -> info()
                    is NappletRequest.FsPick -> pick(request)
                    is NappletRequest.FsStat -> stat(request.path)
                    is NappletRequest.FsList -> list(request.path)
                    is NappletRequest.FsRead -> read(request)
                    is NappletRequest.FsWrite -> write(request)
                    is NappletRequest.FsMkdir, is NappletRequest.FsRemove, is NappletRequest.FsMove, is NappletRequest.FsWatch -> fail(NappletFsError.UNSUPPORTED)
                    // NAP-FS: an unknown watch id MAY be a successful no-op, and this runtime has none.
                    is NappletRequest.FsUnwatch -> NappletResponse.Done
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NappletFsException) {
            fail(e.code)
        } catch (e: Exception) {
            fail(NappletFsError.IO_ERROR)
        }

    /**
     * Forgets every pick: the surface is gone, and what it was granted goes with it. Callable from any
     * thread; an operation still running finishes, and every later one finds nothing.
     */
    fun close() {
        closed = true
        if (lock.tryLock()) {
            try {
                entries.clear()
            } finally {
                lock.unlock()
            }
        }
    }

    private suspend fun info(): NappletResponse {
        val granted = lock.withLock { entries.values.flatMapTo(LinkedHashSet()) { it.permissions } }
        val roots =
            if (granted.isEmpty()) {
                emptyList()
            } else {
                listOf(NappletResponse.FsRoot(NappletFsPaths.PICKED_ROOT, ROOT_NAME, NappletFsPermission.entries.filter { it in granted }.map { it.wire }))
            }
        return NappletResponse.FsInfoResult(roots, MAX_READ_BYTES, MAX_WRITE_BYTES)
    }

    private suspend fun pick(request: NappletRequest.FsPick): NappletResponse {
        if (request.kind == NappletRequest.FsPickKind.DIRECTORY) return fail(NappletFsError.UNSUPPORTED)
        if (!pickLock.tryLock()) return fail(NappletFsError.CONFLICT)
        try {
            if (nowMillis() < quietUntil) return fail(NappletFsError.POLICY_DENIED)
            if (lock.withLock { entries.size } >= MAX_ENTRIES) return fail(NappletFsError.QUOTA_EXCEEDED)

            val asked =
                request.options.permissions
                    ?.mapNotNull(NappletFsPermission::fromWire)
                    ?.toSet()
            val save = request.kind == NappletRequest.FsPickKind.SAVE_FILE
            val picked =
                if (save) {
                    port.pickSave(request.options)?.let(::listOf)
                } else {
                    port.pickOpen(request.options, multiple = request.kind == NappletRequest.FsPickKind.FILES)
                }?.takeIf { it.isNotEmpty() }

            if (picked == null) {
                noteDismissed()
                return fail(NappletFsError.CANCELLED)
            }
            dismissals = 0
            quietUntil = 0L

            val shown =
                lock.withLock {
                    // A pickFile result holds exactly one file, whatever the platform handed back, and a
                    // multi-select only gets the room the session has left.
                    val room = MAX_ENTRIES - entries.size
                    if (room <= 0) return fail(NappletFsError.QUOTA_EXCEEDED)
                    val chosen = picked.take(if (request.kind == NappletRequest.FsPickKind.FILE) 1 else room)
                    chosen.map { file ->
                        val permissions =
                            if (save) {
                                setOf(NappletFsPermission.WRITE, NappletFsPermission.CREATE)
                            } else if (asked != null && NappletFsPermission.WRITE in asked && file.writable) {
                                setOf(NappletFsPermission.READ, NappletFsPermission.WRITE)
                            } else {
                                setOf(NappletFsPermission.READ)
                            }
                        val path = NappletFsPaths.pickedPath(file.name, entries.keys)
                        entries[path] = Entry(path, file.handle, permissions, present = !save)
                        NappletResponse.FsEntry(
                            path = path,
                            name = NappletFsPaths.nameOf(path),
                            permissions = permissions.wires(),
                            size = if (save) null else file.size,
                        )
                    }
                }
            return NappletResponse.FsPicked(shown)
        } finally {
            pickLock.unlock()
        }
    }

    private suspend fun stat(path: String): NappletResponse =
        lock.withLock {
            val entry = find(path)
            if (!entry.present) return@withLock fail(NappletFsError.NOT_FOUND)
            NappletResponse.FsMetadata(
                NappletResponse.FsEntry(entry.path, NappletFsPaths.nameOf(entry.path), entry.permissions.wires(), port.size(entry.handle)),
            )
        }

    private suspend fun list(path: String): NappletResponse =
        lock.withLock {
            val entry = find(path)
            fail(if (entry.present) NappletFsError.NOT_A_DIRECTORY else NappletFsError.NOT_FOUND)
        }

    private suspend fun read(request: NappletRequest.FsRead): NappletResponse {
        val offset = request.offset ?: 0L
        val length = request.length ?: MAX_READ_BYTES.toLong()
        if (offset < 0 || length < 0) return fail(NappletFsError.INVALID_DATA)
        if (length > MAX_READ_BYTES) return fail(NappletFsError.TOO_LARGE)
        return lock.withLock {
            val entry = find(request.path)
            if (!entry.present) return@withLock fail(NappletFsError.NOT_FOUND)
            if (NappletFsPermission.READ !in entry.permissions) return@withLock fail(NappletFsError.PERMISSION_DENIED)
            // One call opens the file once for both the bytes and its size.
            val chunk = port.read(entry.handle, offset, length.toInt())
            val bytes = chunk.bytes
            val size = chunk.size
            if (bytes.size > length) throw NappletFsException(NappletFsError.IO_ERROR)
            val eof =
                if (size != null) {
                    offset + bytes.size >= size
                } else {
                    bytes.size < length
                }
            NappletResponse.FsReadResult(bytes, offset, eof, size)
        }
    }

    private suspend fun write(request: NappletRequest.FsWrite): NappletResponse {
        val data = decodeCanonical(request.data) ?: return fail(NappletFsError.INVALID_DATA)
        if (data.size > MAX_WRITE_BYTES) return fail(NappletFsError.TOO_LARGE)
        val mode =
            when (request.mode ?: "replace") {
                "replace" -> if (request.offset != null) return fail(NappletFsError.INVALID_DATA) else NappletFsWriteMode.Replace
                "append" -> if (request.offset != null) return fail(NappletFsError.INVALID_DATA) else NappletFsWriteMode.Append
                "patch" -> {
                    val offset = request.offset ?: return fail(NappletFsError.INVALID_DATA)
                    if (offset < 0) return fail(NappletFsError.INVALID_DATA)
                    NappletFsWriteMode.Patch(offset)
                }
                else -> return fail(NappletFsError.UNSUPPORTED)
            }
        // No revisions are exposed, and NAP-FS says such a runtime rejects the precondition.
        if (request.ifRevision != null) return fail(NappletFsError.UNSUPPORTED)

        return lock.withLock {
            val entry = find(request.path)
            if (entry.present) {
                if (request.ifAbsent == true) return@withLock fail(NappletFsError.CONFLICT)
                if (NappletFsPermission.WRITE !in entry.permissions) return@withLock fail(NappletFsError.PERMISSION_DENIED)
                NappletResponse.FsWritten(data.size, port.write(entry.handle, data, mode))
            } else {
                if (NappletFsPermission.CREATE !in entry.permissions) return@withLock fail(NappletFsError.PERMISSION_DENIED)
                // Creating starts from nothing, so an append is the whole file; a patch would leave a
                // hole before its offset that nothing asked for.
                if (mode is NappletFsWriteMode.Patch && mode.offset > 0) return@withLock fail(NappletFsError.NOT_FOUND)
                val size = port.write(entry.handle, data, NappletFsWriteMode.Replace)
                entry.present = true
                NappletResponse.FsWritten(data.size, size)
            }
        }
    }

    /** The entry at [path], or the failure a napplet gets for a path it was never given. */
    private fun find(path: String): Entry {
        if (!NappletFsPaths.isValid(path)) throw NappletFsException(NappletFsError.INVALID_PATH)
        if (closed) throw NappletFsException(NappletFsError.NOT_FOUND)
        return entries[path] ?: throw NappletFsException(NappletFsError.NOT_FOUND)
    }

    private fun noteDismissed() {
        dismissals++
        // The first dismissal may be a slip the napplet retries at once; from the second in a row, it
        // waits, doubling each time.
        if (dismissals >= 2) {
            val wait = (DISMISS_BACKOFF_MS shl (dismissals - 2).coerceAtMost(6)).coerceAtMost(MAX_DISMISS_BACKOFF_MS)
            quietUntil = nowMillis() + wait
        }
    }

    private fun Set<NappletFsPermission>.wires() = NappletFsPermission.entries.filter { it in this }.map { it.wire }

    private fun fail(code: String) = NappletResponse.FsFailure(code)

    companion object {
        /** The most bytes one `fs.read` returns and one `fs.write` takes, decoded. */
        const val MAX_READ_BYTES = 128 * 1024
        const val MAX_WRITE_BYTES = 128 * 1024

        /** The most picked entries one session holds. */
        const val MAX_ENTRIES = 256

        const val DISMISS_BACKOFF_MS = 3_000L
        const val MAX_DISMISS_BACKOFF_MS = 60_000L

        /** The label `fs.info` gives the root picks appear under: says nothing about the device. */
        const val ROOT_NAME = "Picked files"

        /**
         * [text] as bytes when it is canonical padded base64 (RFC 4648's standard alphabet, no
         * whitespace), else null: NAP-FS rejects anything else with `invalid-data`.
         */
        fun decodeCanonical(text: String): ByteArray? {
            if (text.isEmpty()) return ByteArray(0)
            if (text.length % 4 != 0) return null
            val bytes = runCatching { Base64.Default.decode(text) }.getOrNull() ?: return null
            return bytes.takeIf { Base64.Default.encode(it) == text }
        }
    }
}

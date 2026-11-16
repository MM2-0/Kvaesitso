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
package com.vitorpamplona.amethyst.commons.storage

import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.sibling
import okio.FileSystem
import okio.Path
import okio.use

/**
 * An encrypted-at-rest log of UTF-8 entries that can be appended to in
 * constant time.
 *
 * ```
 * file    := MAGIC(8) foldedLength(uint64) segment*
 * segment := uint32 encLen, byte[encLen]        // whatever [encrypt] produces
 * plain   := uint32 count, (uint32 len, byte[len])*
 * ```
 *
 * Protocol-neutral on purpose: it takes [encrypt]/[decrypt] lambdas and stores
 * opaque strings, so both group-chat implementations keep their own ciphers and
 * their own formats on top of one file layout. It used to live in the `marmot`
 * package, which made it unreachable from cordn — the independence guard
 * forbids either feature importing the other by name — for no reason other
 * than where it happened to be written first.
 *
 * Segments are the whole point. The format this replaces was a single blob
 * covering the entire history, so recording one line meant pushing every line
 * ever written back through the cipher and out to disk again — work that grew
 * with the log and was paid on the send path. A
 * conversation a few thousand messages long was moving hundreds of KB through
 * a hardware-backed cipher to append a couple of hundred bytes.
 *
 * Appending writes one small segment. Every [compactAfterSegments] appends the
 * loose run — everything past `foldedLength` — is folded into a single segment,
 * which bounds how many cipher round trips a read costs. A fold re-encrypts
 * only the run it collapses; the already-folded prefix is copied across as
 * ciphertext, so no append ever pays for the whole history.
 *
 * `foldedLength` lives in the header rather than in memory so that bound
 * survives a restart. Held only in memory, every session would treat whatever
 * it found as already folded, leave its own run permanently unfoldable, and
 * segments would creep back towards one per message — which is the cost the
 * fold exists to prevent.
 *
 * A file written by the older format has no magic prefix and is read as a
 * single legacy blob; the next append rewrites it in this format.
 *
 * **Not thread-safe.** Entries are cached in memory so an append never has to
 * read the log back, and that cache assumes one owner. Callers hold their own
 * lock around every method (see `EncryptedMarmotMessageStore`), and one instance
 * must own any given file.
 *
 * @param encrypt must produce a self-describing blob — it carries its own IV /
 *   nonce, since every segment is encrypted separately.
 * @param decrypt MUST return null rather than throwing for a segment it cannot
 *   open. One unreadable segment then costs only its own entries; a throw would
 *   abort the whole read, and a caller that reads an empty log can overwrite a
 *   history that was merely unreadable.
 * @param fileSystem where the logs live. Durability rests on
 *   [okio.FileHandle.flush], which on the JVM and Android is an `fsync`.
 */
class EncryptedAppendLog(
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray?,
    private val compactAfterSegments: Int = COMPACT_AFTER_SEGMENTS,
    private val fileSystem: FileSystem = platformFileSystem,
) {
    /** Entries of one file, plus what it takes to append without re-reading it. */
    private class LogState(
        val entries: MutableList<String>,
        val seen: MutableSet<String>,
        /** False when the file has no header yet: it is new, or still in the old format. */
        var headed: Boolean,
        /** Byte offset where the loose run begins; everything before it is one folded prefix. */
        var foldedLength: Long,
        /** Segments sitting past [foldedLength]. */
        var looseSegments: Int,
        /** Entries carried by those segments. */
        var looseEntries: Int,
    )

    /**
     * Keyed by the normalized path. That folds `a/./b` and `a/../a/b` together but does not resolve
     * a relative path against the working directory, so callers must spell one file one way; every
     * store here builds its paths from one directory.
     */
    private val logs = mutableMapOf<Path, LogState>()

    private fun stateFor(file: Path): LogState =
        logs.getOrPut(file.normalized()) {
            val decoded = decodeFile(file)

            // A torn trailing segment is dropped from the FILE, not merely from
            // what we just read. Leaving it there puts the append position past
            // a record that parsing always stops at, so every later append is
            // written and then never read back — the log goes silently
            // write-only, for good.
            if (decoded.headed && decoded.validLength < fileLength(file)) {
                try {
                    fileSystem.openReadWrite(file).use { it.resize(decoded.validLength) }
                } catch (_: Exception) {
                    // Best effort: the next fold rewrites the file wholesale and
                    // resolves it anyway.
                }
            }

            LogState(
                entries = decoded.entries.toMutableList(),
                seen = decoded.entries.toMutableSet(),
                headed = decoded.headed,
                foldedLength = decoded.foldedLength,
                looseSegments = decoded.looseSegments,
                looseEntries = decoded.looseEntries,
            )
        }

    /** Every entry in [file], oldest first. */
    fun readAll(file: Path): List<String> = stateFor(file).entries.toList()

    /** Whether [entry] is already in [file], without reading it back from disk. */
    fun contains(
        file: Path,
        entry: String,
    ): Boolean = entry in stateFor(file).seen

    /** Append one entry. Constant time, apart from a periodic fold. */
    fun append(
        file: Path,
        entry: String,
    ) {
        val state = stateFor(file)

        // No header to append after — a file that does not exist yet, or one
        // still in the old format. A rewrite lays one down.
        if (!state.headed) {
            rewrite(file, state.entries + entry)
            return
        }

        // The disk write comes FIRST. Recording the entry in memory before it is
        // durable would let a failed write leave it marked as held: the caller's
        // duplicate check would skip it from then on, and for Marmot this store
        // holds the only copy of the plaintext.
        appendSegment(file, listOf(entry))

        state.entries.add(entry)
        state.seen.add(entry)
        state.looseSegments += 1
        state.looseEntries += 1

        if (state.looseSegments >= compactAfterSegments) foldLooseTail(file, state)
    }

    /**
     * Collapse the loose run into one segment, re-encrypting only that run.
     *
     * The prefix is copied across as ciphertext, byte for byte. That is the
     * point: folding exists to bound how many segments a read has to open, and
     * re-encrypting the whole log to achieve it would cost more than the
     * segments ever did — on a hardware-backed cipher, enough to stall a send
     * for seconds once a conversation is long enough.
     *
     * Written through a temp file and renamed, so a crash mid-fold leaves the
     * previous log intact. Truncating in place and appending afterwards would
     * be cheaper and would lose every loose entry if the process died in
     * between.
     */
    private fun foldLooseTail(
        file: Path,
        state: LogState,
    ) {
        if (state.looseEntries == 0) return

        // Exactly foldedLength bytes, or an EOFException — what readFully did.
        val prefix = fileSystem.read(file) { readByteArray(state.foldedLength) }

        val folded = encrypt(encodeEntries(state.entries.takeLast(state.looseEntries)))
        val out = ByteArray(prefix.size + 4 + folded.size)
        prefix.copyInto(out, 0)
        lengthPrefix(folded.size).copyInto(out, prefix.size)
        folded.copyInto(out, prefix.size + 4)
        // The copied prefix still carries the old boundary; the whole file is
        // folded now.
        writeLong(out, MAGIC.size, out.size.toLong())
        atomicWrite(file, out)

        state.foldedLength = out.size.toLong()
        state.looseSegments = 0
        state.looseEntries = 0
    }

    private fun appendSegment(
        file: Path,
        entries: List<String>,
    ) {
        file.parent?.let { fileSystem.createDirectories(it) }
        val segment = encrypt(encodeEntries(entries))
        // Opened without truncating and written at its current end: an append.
        fileSystem.openReadWrite(file).use { handle ->
            val end = handle.size()
            handle.write(end, lengthPrefix(segment.size), 0, 4)
            handle.write(end + 4, segment, 0, segment.size)
            // An append that survives only in the page cache would lose a
            // message the UI has already shown as sent.
            handle.flush()
        }
    }

    /** Replace the whole log with [entries], as a single folded segment. */
    fun rewrite(
        file: Path,
        entries: List<String>,
    ) {
        file.parent?.let { fileSystem.createDirectories(it) }

        val segment = encrypt(encodeEntries(entries))
        val out = ByteArray(HEADER_LENGTH + 4 + segment.size)
        MAGIC.copyInto(out, 0)
        writeLong(out, MAGIC.size, out.size.toLong())
        lengthPrefix(segment.size).copyInto(out, HEADER_LENGTH)
        segment.copyInto(out, HEADER_LENGTH + 4)
        atomicWrite(file, out)

        val state =
            logs.getOrPut(file.normalized()) {
                LogState(mutableListOf(), mutableSetOf(), headed = true, foldedLength = 0L, looseSegments = 0, looseEntries = 0)
            }
        state.entries.clear()
        state.entries.addAll(entries)
        state.seen.clear()
        state.seen.addAll(entries)
        state.headed = true
        state.foldedLength = out.size.toLong()
        state.looseSegments = 0
        state.looseEntries = 0
    }

    /** Drop the in-memory cache for [file]; call when the file is deleted. */
    fun forget(file: Path) {
        logs.remove(file.normalized())
    }

    private class Decoded(
        val entries: List<String>,
        val headed: Boolean,
        /** Bytes up to the end of the last segment that parsed cleanly. */
        val validLength: Long,
        val foldedLength: Long,
        val looseSegments: Int,
        val looseEntries: Int,
    )

    private fun decodeFile(file: Path): Decoded {
        // A file that does not exist yet has no header, so the first append has
        // to write one rather than tack a bare segment onto nothing.
        if (!fileSystem.exists(file)) return Decoded(emptyList(), false, 0L, 0L, 0, 0)
        val bytes = fileSystem.read(file) { readByteArray() }

        if (!bytes.startsWithMagic() || bytes.size < HEADER_LENGTH) {
            // The older format: the file is one encrypted blob and nothing else.
            val plain = decrypt(bytes) ?: return Decoded(emptyList(), false, 0L, 0L, 0, 0)
            return Decoded(decodeEntries(plain), false, 0L, 0L, 0, 0)
        }

        val foldedLength = readLong(bytes, MAGIC.size).coerceIn(HEADER_LENGTH.toLong(), bytes.size.toLong())

        val result = ArrayList<String>()
        var offset = HEADER_LENGTH
        var looseSegments = 0
        var looseEntries = 0
        while (offset + 4 <= bytes.size) {
            val segmentStart = offset
            val encLen = readInt(bytes, offset)
            offset += 4
            // A truncated tail is a half-finished append (process death between
            // the write and the sync). Everything before it is intact and is
            // what we keep; the torn record is dropped rather than failing the
            // whole log. Rewinding to the record's own start is what makes
            // validLength the place a later append may safely resume from.
            if (encLen <= 0 || offset + encLen > bytes.size) {
                offset = segmentStart
                break
            }
            val plain = decrypt(bytes.copyOfRange(offset, offset + encLen))
            offset += encLen

            val entries = if (plain != null) decodeEntries(plain) else emptyList()
            result.addAll(entries)
            if (segmentStart >= foldedLength) {
                looseSegments += 1
                looseEntries += entries.size
            }
        }

        return Decoded(
            entries = result,
            headed = true,
            validLength = offset.toLong(),
            foldedLength = foldedLength.coerceAtMost(offset.toLong()),
            looseSegments = looseSegments,
            looseEntries = looseEntries,
        )
    }

    private fun encodeEntries(entries: List<String>): ByteArray {
        val encoded = entries.map { it.encodeToByteArray() }
        val buffer = ByteArray(4 + encoded.sumOf { 4 + it.size })
        var offset = 0

        writeInt(buffer, offset, encoded.size)
        offset += 4

        for (entry in encoded) {
            writeInt(buffer, offset, entry.size)
            offset += 4
            entry.copyInto(buffer, offset)
            offset += entry.size
        }
        return buffer
    }

    private fun decodeEntries(plain: ByteArray): List<String> {
        if (plain.size < 4) return emptyList()
        var offset = 0
        val count = readInt(plain, offset)
        offset += 4

        val result = ArrayList<String>(count.coerceIn(0, MAX_ENTRIES))
        for (i in 0 until count) {
            if (offset + 4 > plain.size) break
            val len = readInt(plain, offset)
            offset += 4
            if (len < 0 || offset + len > plain.size) break
            result.add(plain.copyOfRange(offset, offset + len).decodeToString())
            offset += len
        }
        return result
    }

    /** Write via a temp file and rename, so a crash can't leave a half-written log. */
    private fun atomicWrite(
        target: Path,
        data: ByteArray,
    ) {
        val tempFile = target.sibling("${target.name}.tmp")
        fileSystem.openReadWrite(tempFile).use { handle ->
            // Truncated first, as opening a FileOutputStream did: a stale temp
            // longer than [data] must not leave its tail behind.
            handle.resize(0L)
            handle.write(0L, data, 0, data.size)
            handle.flush()
        }
        fileSystem.moveOrCopy(tempFile, target)
    }

    private fun fileLength(file: Path): Long = fileSystem.metadataOrNull(file)?.size ?: 0L

    private fun ByteArray.startsWithMagic(): Boolean {
        if (size < MAGIC.size) return false
        for (i in MAGIC.indices) if (this[i] != MAGIC[i]) return false
        return true
    }

    private fun lengthPrefix(value: Int): ByteArray = ByteArray(4).also { writeInt(it, 0, value) }

    private fun writeInt(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = (value shr 24).toByte()
        target[offset + 1] = (value shr 16).toByte()
        target[offset + 2] = (value shr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)

    private fun writeLong(
        target: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (i in 0 until 8) target[offset + i] = (value shr (56 - 8 * i)).toByte()
    }

    private fun readLong(
        source: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (source[offset + i].toLong() and 0xFF)
        return value
    }

    companion object {
        /** Marks the segmented format; a file without it predates it. */
        private val MAGIC = "MRMTLOG3".encodeToByteArray()

        /** MAGIC plus the uint64 fold boundary. */
        private const val HEADER_LENGTH = 16

        private const val COMPACT_AFTER_SEGMENTS = 200

        private const val MAX_ENTRIES = 1_000_000
    }
}

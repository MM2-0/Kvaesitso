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
package com.vitorpamplona.amethyst.napplet.gateways

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsChunk
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsException
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPaths
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPermission
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsWriteMode
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
import com.vitorpamplona.amethyst.napplet.NappletFsPickerCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * NAP-FS on Android: the Storage Access Framework's pickers ([NappletFsPickerCoordinator]) and the
 * [ContentResolver] for the bytes. A handle is the picked document's `content://` URI; it stays in
 * the main process and never reaches the napplet.
 *
 * Providers differ in the modes they open: a write tries the precise mode first (`wt`, `wa`, `rw`)
 * and falls back to one that gives the same result, and a provider that offers none answers
 * `unsupported`. Reads seek when the provider hands a seekable file and skip otherwise.
 */
class AndroidNappletFilePort(
    private val context: Context,
) : NappletFilePort {
    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun pickOpen(
        options: NappletFsPickOptions,
        multiple: Boolean,
    ): List<NappletPickedFile>? {
        val wantsWrite = options.permissions?.contains(NappletFsPermission.WRITE.wire) == true
        val kind = if (multiple) NappletFsPickerCoordinator.Kind.OPEN_MULTIPLE else NappletFsPickerCoordinator.Kind.OPEN
        val uris = NappletFsPickerCoordinator.pick(context, kind, options.accept.mapNotNull { it.token() }, null, wantsWrite) ?: return null
        return withContext(Dispatchers.IO) { uris.map { describe(it) } }
    }

    override suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile? {
        val title = options.suggestedName?.let(NappletFsPaths::sanitizeName)
        val uri = NappletFsPickerCoordinator.pick(context, NappletFsPickerCoordinator.Kind.SAVE, options.accept.mapNotNull { it.token() }, title, wantsWrite = true)?.firstOrNull() ?: return null
        return withContext(Dispatchers.IO) { describe(uri) }
    }

    override suspend fun size(handle: String): Long? =
        withContext(Dispatchers.IO) {
            val uri = handle.toUri()
            // The descriptor's size is the file's now; a provider's SIZE column may lag a write.
            guard { resolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { size -> size >= 0 } } } ?: queried(uri)?.size
        }

    override suspend fun read(
        handle: String,
        offset: Long,
        length: Int,
    ): NappletFsChunk =
        withContext(Dispatchers.IO) {
            val uri = handle.toUri()
            guard {
                val buffer = ByteArray(length)
                // One opening for the bytes and the size, when the provider hands a seekable file.
                val seeked =
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val size = pfd.statSize.takeIf { it >= 0 }
                        FileInputStream(pfd.fileDescriptor).use { input ->
                            val channel = input.channel
                            runCatching { channel.position(offset) }.getOrNull()?.let { NappletFsChunk(buffer.copyOf(readFully(channel, buffer)), size) }
                        }
                    }
                seeked ?: (resolver.openInputStream(uri) ?: throw FileNotFoundException()).use { input ->
                    skipFully(input, offset)
                    NappletFsChunk(buffer.copyOf(readFully(input, buffer)), queried(uri)?.size)
                }
            }
        }

    override suspend fun write(
        handle: String,
        data: ByteArray,
        mode: NappletFsWriteMode,
    ): Long? =
        withContext(Dispatchers.IO) {
            val uri = handle.toUri()
            guard {
                when (mode) {
                    NappletFsWriteMode.Replace -> replace(uri, data)
                    NappletFsWriteMode.Append -> append(uri, data)
                    is NappletFsWriteMode.Patch -> patch(uri, data, mode.offset)
                }
            }
        }

    private fun replace(
        uri: Uri,
        data: ByteArray,
    ): Long {
        // "wt" truncates; a provider without it may still offer "rw", truncated by hand. Never a bare
        // "w": some providers leave the old tail after a shorter write.
        val wrote =
            openOrNull(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(data) }
                true
            } ?: openOrNull(uri, "rw")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                    channel.truncate(0)
                    writeFully(channel, data, 0)
                }
                true
            }
        if (wrote != true) throw unopenable(uri)
        return data.size.toLong()
    }

    private fun append(
        uri: Uri,
        data: ByteArray,
    ): Long? {
        openOrNull(uri, "rw")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                val start = channel.size()
                try {
                    writeFully(channel, data, start)
                } catch (e: IOException) {
                    // All or nothing: take back what part of it landed.
                    runCatching { channel.truncate(start) }
                    throw e
                }
                return start + data.size
            }
        }
        openOrNull(uri, "wa")?.use { pfd ->
            // The size before, read while the descriptor is open: once the bytes are in, nothing may
            // fail the call, or a napplet retrying it would append them twice.
            val start = pfd.statSize
            FileOutputStream(pfd.fileDescriptor).use { it.write(data) }
            return if (start >= 0) start + data.size else null
        }
        throw unopenable(uri)
    }

    private fun patch(
        uri: Uri,
        data: ByteArray,
        offset: Long,
    ): Long {
        val pfd = openOrNull(uri, "rw") ?: throw unopenable(uri)
        pfd.use {
            FileOutputStream(it.fileDescriptor).channel.use { channel ->
                writeFully(channel, data, offset)
                return channel.size()
            }
        }
    }

    /**
     * Why no write mode opened [uri]: [openOrNull] can't tell a mode the provider lacks from a
     * document that is gone, so ask the provider whether it still has the document.
     */
    private fun unopenable(uri: Uri) = NappletFsException(if (queried(uri) == null) NappletFsError.NOT_FOUND else NappletFsError.UNSUPPORTED)

    /** [uri] opened in [mode], or null when the provider does not offer that mode (or the document is gone). */
    private fun openOrNull(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? =
        try {
            resolver.openFileDescriptor(uri, mode)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedOperationException) {
            null
        } catch (e: FileNotFoundException) {
            // Some providers report an unsupported mode this way; a gone file fails the next mode too.
            null
        }

    private class Described(
        val name: String?,
        val size: Long?,
        val writable: Boolean,
    )

    /** What the provider says about [uri], or null when it has no such document (any more). */
    private fun queried(uri: Uri): Described? =
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                Described(
                    name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null,
                    size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null,
                    writable = flagsIndex >= 0 && (cursor.getInt(flagsIndex) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0,
                )
            }
        }.getOrNull()

    private fun describe(uri: Uri): NappletPickedFile {
        val described = queried(uri)
        return NappletPickedFile(
            handle = uri.toString(),
            name = described?.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file",
            size = described?.size,
            writable = described?.writable == true,
        )
    }

    private inline fun <T> guard(block: () -> T): T =
        try {
            block()
        } catch (e: FileNotFoundException) {
            throw NappletFsException(NappletFsError.NOT_FOUND)
        } catch (e: SecurityException) {
            throw NappletFsException(NappletFsError.PERMISSION_DENIED)
        }

    private fun readFully(
        channel: FileChannel,
        buffer: ByteArray,
    ): Int {
        val target = ByteBuffer.wrap(buffer)
        while (target.hasRemaining()) {
            if (channel.read(target) < 0) break
        }
        return target.position()
    }

    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
    ): Int {
        var count = 0
        while (count < buffer.size) {
            val n = input.read(buffer, count, buffer.size - count)
            if (n < 0) break
            count += n
        }
        return count
    }

    private fun skipFully(
        input: InputStream,
        offset: Long,
    ) {
        var left = offset
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else if (input.read() < 0) {
                return
            } else {
                left--
            }
        }
    }

    private fun writeFully(
        channel: FileChannel,
        data: ByteArray,
        at: Long,
    ) {
        val source = ByteBuffer.wrap(data)
        var position = at
        while (source.hasRemaining()) position += channel.write(source, position)
    }
}

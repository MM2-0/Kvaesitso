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

import kotlinx.coroutines.CompletableDeferred

/**
 * An in-memory [NappletFilePort]: [disk] maps handles to bytes, and each picker returns the next
 * answer queued in [opens] / [saves] (null = the user cancelled). [gate], when set, holds a picker
 * open until completed.
 */
class FakeNappletFilePort : NappletFilePort {
    val disk = LinkedHashMap<String, ByteArray>()
    val opens = ArrayDeque<List<NappletPickedFile>?>()
    val saves = ArrayDeque<NappletPickedFile?>()
    var gate: CompletableDeferred<Unit>? = null
    var pickerShown = 0
        private set

    override suspend fun pickOpen(
        options: NappletFsPickOptions,
        multiple: Boolean,
    ): List<NappletPickedFile>? {
        pickerShown++
        gate?.await()
        return opens.removeFirst()
    }

    override suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile? {
        pickerShown++
        gate?.await()
        return saves.removeFirst()
    }

    override suspend fun size(handle: String): Long? = (disk[handle] ?: throw NappletFsException(NappletFsError.NOT_FOUND)).size.toLong()

    var reads = 0
        private set

    override suspend fun read(
        handle: String,
        offset: Long,
        length: Int,
    ): NappletFsChunk {
        reads++
        val bytes = disk[handle] ?: throw NappletFsException(NappletFsError.NOT_FOUND)
        if (offset >= bytes.size) return NappletFsChunk(ByteArray(0), bytes.size.toLong())
        return NappletFsChunk(bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + length)), bytes.size.toLong())
    }

    override suspend fun write(
        handle: String,
        data: ByteArray,
        mode: NappletFsWriteMode,
    ): Long? {
        val current = disk[handle] ?: ByteArray(0)
        val next =
            when (mode) {
                NappletFsWriteMode.Replace -> data
                NappletFsWriteMode.Append -> current + data
                is NappletFsWriteMode.Patch -> {
                    val end = maxOf(current.size, mode.offset.toInt() + data.size)
                    current.copyOf(end).also { data.copyInto(it, mode.offset.toInt()) }
                }
            }
        disk[handle] = next
        return next.size.toLong()
    }
}

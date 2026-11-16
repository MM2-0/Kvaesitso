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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser

/**
 * Reads [length] bytes of a remote file starting at [offset]. May return fewer bytes (end of file,
 * or a server that ignores ranges and stops early); null when the read failed.
 */
fun interface RangeReader {
    suspend fun read(
        offset: Long,
        length: Int,
    ): ByteArray?
}

/**
 * Whether an MP4-family file (MP4, M4A, M4V, MOV, 3GP) holds only audio: true when its tracks are all
 * sound, false when one is video, null when it isn't an MP4 file or its track list can't be read.
 *
 * The extension and even the server's `Content-Type` can't answer this: Blossom serves an AAC upload as
 * `.mp4` / `video/mp4`. The answer is in the `moov` box, whose `trak/mdia/hdlr` boxes name each track's
 * handler (`soun`, `vide`). `moov` sits either right after `ftyp` (a "fast start" file) or after the media
 * data at the end, so the walk reads box headers only, through [BlockReader]'s 64 KB blocks: a fast-start
 * file costs one request, a tail-`moov` file two or three.
 */
suspend fun probeMp4AudioOnly(reader: RangeReader): Boolean? {
    val file = BlockReader(reader)
    val ftyp = file.header(0) ?: return null
    if (ftyp.type != "ftyp") return null

    var offset = 0L
    repeat(MAX_TOP_LEVEL_BOXES) {
        val box = file.header(offset) ?: return null
        if (box.type == "moov") return handlersAudioOnly(trackHandlers(file, box))
        // A box that runs to the end of the file (size 0) is the last one: no moov after it.
        if (box.size == 0L) return null
        offset += box.size
    }
    return null
}

private suspend fun trackHandlers(
    file: BlockReader,
    moov: BoxHeader,
): List<String> {
    val handlers = mutableListOf<String>()
    for (trak in file.children(moov, "trak")) {
        val mdia = file.children(trak, "mdia").firstOrNull() ?: continue
        val hdlr = file.children(mdia, "hdlr").firstOrNull() ?: continue
        // hdlr is a full box: version+flags (4), pre_defined (4), then the 4-byte handler type.
        val type = file.bytes(hdlr.payloadOffset + 8, 4) ?: continue
        handlers += type.decodeToString()
    }
    return handlers
}

private fun handlersAudioOnly(handlers: List<String>): Boolean? =
    when {
        handlers.any { it == "vide" } -> false
        handlers.any { it == "soun" } -> true
        else -> null
    }

/**
 * True for the URLs and MIME types whose container is MP4-family, the ones [probeMp4AudioOnly] can
 * read and whose label doesn't say whether they hold video.
 */
fun isMp4FamilyMedia(
    mimeType: String?,
    url: String,
): Boolean {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    if (mime != null && mime in MP4_FAMILY_MIME_TYPES) return true
    return RichTextParser.hasExtensionIn(url, MP4_FAMILY_EXTENSIONS)
}

/**
 * An MP4-family file that doesn't declare itself audio, so its label can't settle whether it holds video:
 * `.mp4`, `video/mp4`, `.mov`. Such a file is what [probeMp4AudioOnly] is for. A declared `audio/` type or
 * an audio extension (`.m4a`) is taken at its word.
 */
fun mayHoldVideo(
    mimeType: String?,
    url: String,
): Boolean = isMp4FamilyMedia(mimeType, url) && declaredAsAudio(mimeType, url) != true

private val MP4_FAMILY_MIME_TYPES = setOf("video/mp4", "audio/mp4", "audio/x-m4a", "audio/m4a", "video/quicktime", "video/x-m4v", "video/3gpp", "audio/3gpp")
private val MP4_FAMILY_EXTENSIONS = listOf("mp4", "m4a", "m4v", "mov", "3gp")

private const val MAX_TOP_LEVEL_BOXES = 64
private const val MAX_CHILD_BOXES = 256

private class BoxHeader(
    val offset: Long,
    val headerSize: Int,
    /** Whole box size in bytes, header included; 0 means "to the end of the file". */
    val size: Long,
    val type: String,
) {
    val payloadOffset get() = offset + headerSize
    val end get() = offset + size
}

/** A [RangeReader] seen through fixed blocks, so the many small header reads share a few requests. */
private class BlockReader(
    private val reader: RangeReader,
) {
    private val blocks = HashMap<Long, ByteArray>()

    suspend fun bytes(
        offset: Long,
        length: Int,
    ): ByteArray? {
        val out = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val position = offset + filled
            val blockStart = position - position % BLOCK_SIZE
            val block = block(blockStart) ?: return null
            val inBlock = (position - blockStart).toInt()
            if (inBlock >= block.size) return null
            val count = minOf(length - filled, block.size - inBlock)
            block.copyInto(out, filled, inBlock, inBlock + count)
            filled += count
        }
        return out
    }

    private suspend fun block(start: Long): ByteArray? {
        blocks[start]?.let { return it }
        if (blocks.size >= MAX_BLOCKS) return null
        val data = reader.read(start, BLOCK_SIZE) ?: return null
        blocks[start] = data
        return data
    }

    suspend fun header(offset: Long): BoxHeader? {
        val head = bytes(offset, 8) ?: return null
        val size32 = head.uint32(0)
        val type = head.copyOfRange(4, 8).decodeToString()
        return when (size32) {
            1L -> {
                val large = bytes(offset + 8, 8) ?: return null
                val size = large.uint64(0)
                if (size < 16) null else BoxHeader(offset, 16, size, type)
            }
            0L -> BoxHeader(offset, 8, 0, type)
            else -> if (size32 < 8) null else BoxHeader(offset, 8, size32, type)
        }
    }

    suspend fun children(
        parent: BoxHeader,
        type: String,
    ): List<BoxHeader> {
        if (parent.size == 0L) return emptyList()
        val found = mutableListOf<BoxHeader>()
        var offset = parent.payloadOffset
        var count = 0
        while (offset + 8 <= parent.end && count++ < MAX_CHILD_BOXES) {
            val child = header(offset) ?: break
            if (child.size == 0L || child.end > parent.end) break
            if (child.type == type) found += child
            offset = child.end
        }
        return found
    }

    companion object {
        const val BLOCK_SIZE = 64 * 1024
        const val MAX_BLOCKS = 8
    }
}

private fun ByteArray.uint32(at: Int): Long =
    ((this[at].toLong() and 0xFF) shl 24) or
        ((this[at + 1].toLong() and 0xFF) shl 16) or
        ((this[at + 2].toLong() and 0xFF) shl 8) or
        (this[at + 3].toLong() and 0xFF)

private fun ByteArray.uint64(at: Int): Long = (uint32(at) shl 32) or uint32(at + 4)

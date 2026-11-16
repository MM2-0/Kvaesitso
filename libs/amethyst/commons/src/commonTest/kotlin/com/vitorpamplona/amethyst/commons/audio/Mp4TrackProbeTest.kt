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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Mp4TrackProbeTest {
    private fun box(
        type: String,
        vararg children: ByteArray,
    ): ByteArray = box(type, children.fold(ByteArray(0)) { acc, b -> acc + b })

    private fun box(
        type: String,
        payload: ByteArray,
    ): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) +
            type.encodeToByteArray() + payload
    }

    private fun hdlr(handler: String) = box("hdlr", ByteArray(8) + handler.encodeToByteArray() + ByteArray(12))

    private fun trak(handler: String) = box("trak", box("tkhd", ByteArray(84)), box("mdia", box("mdhd", ByteArray(24)), hdlr(handler), box("minf", ByteArray(40))))

    private fun moov(vararg handlers: String) = box("moov", box("mvhd", ByteArray(100)), *handlers.map { trak(it) }.toTypedArray())

    private val ftyp = box("ftyp", "mp42".encodeToByteArray() + ByteArray(4) + "isommp42".encodeToByteArray())

    private fun mdat(bytes: Int) = box("mdat", ByteArray(bytes))

    private class CountingReader(
        val file: ByteArray,
    ) : RangeReader {
        var reads = 0

        override suspend fun read(
            offset: Long,
            length: Int,
        ): ByteArray? {
            reads++
            if (offset >= file.size) return ByteArray(0)
            return file.copyOfRange(offset.toInt(), minOf(file.size, offset.toInt() + length))
        }
    }

    @Test
    fun fastStartAudioOnlyFileIsAudioInOneRead() =
        runTest {
            val reader = CountingReader(ftyp + moov("soun") + mdat(500_000))
            assertTrue(probeMp4AudioOnly(reader)!!)
            assertEquals(1, reader.reads)
        }

    @Test
    fun tailMoovAudioOnlyFileIsAudio() =
        runTest {
            // Blossom's AAC uploads: ftyp, free, a large mdat, then moov.
            val reader = CountingReader(ftyp + box("free", ByteArray(3184)) + mdat(2_443_118) + moov("soun"))
            assertTrue(probeMp4AudioOnly(reader)!!)
            assertTrue(reader.reads <= 3, "took ${reader.reads} reads")
        }

    @Test
    fun videoWithAudioIsNotAudioOnly() =
        runTest {
            assertFalse(probeMp4AudioOnly(CountingReader(ftyp + mdat(300_000) + moov("vide", "soun", "text")))!!)
            assertFalse(probeMp4AudioOnly(CountingReader(ftyp + moov("soun", "vide") + mdat(10)))!!)
        }

    @Test
    fun nonMp4FilesAreUnknown() =
        runTest {
            // An MP3 starts with an ID3 tag, a WebM with the EBML magic: neither has ftyp first.
            assertNull(probeMp4AudioOnly(CountingReader("ID3".encodeToByteArray() + ByteArray(5000))))
            assertNull(probeMp4AudioOnly(CountingReader(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(5000))))
        }

    @Test
    fun aFileWithoutMoovOrTracksIsUnknown() =
        runTest {
            assertNull(probeMp4AudioOnly(CountingReader(ftyp + mdat(1000))))
            assertNull(probeMp4AudioOnly(CountingReader(ftyp + box("moov", box("mvhd", ByteArray(100))))))
        }

    @Test
    fun aFailedReadIsUnknown() =
        runTest {
            assertNull(probeMp4AudioOnly { _, _ -> null })
        }

    @Test
    fun truncatedOrCorruptBoxesAreUnknown() =
        runTest {
            val file = ftyp + moov("soun")
            assertNull(probeMp4AudioOnly(CountingReader(file.copyOf(ftyp.size + 20))))
            // A box claiming a size smaller than its own header stops the walk instead of looping.
            assertNull(probeMp4AudioOnly(CountingReader(ftyp + byteArrayOf(0, 0, 0, 4) + "junk".encodeToByteArray())))
        }

    @Test
    fun onlyUndeclaredMp4FilesMayHoldVideo() {
        assertTrue(mayHoldVideo(null, "https://blossom.band/abc.mp4"))
        assertTrue(mayHoldVideo("video/mp4", "https://x.com/a"))
        assertTrue(mayHoldVideo(null, "https://x.com/a.mov"))
        // Declared audio is taken at its word.
        assertFalse(mayHoldVideo("audio/mp4", "https://video.nostr.build/abc.mp4"))
        assertFalse(mayHoldVideo(null, "https://x.com/a.m4a"))
        assertFalse(mayHoldVideo(null, "https://x.com/a.mp3"))
        assertFalse(mayHoldVideo("video/webm", "https://x.com/a.webm"))
    }

    @Test
    fun mp4FamilyIsRecognisedByMimeOrExtension() {
        assertTrue(isMp4FamilyMedia(null, "https://blossom.band/abc.mp4"))
        assertTrue(isMp4FamilyMedia(null, "https://x.com/a.M4A?dl=1"))
        assertTrue(isMp4FamilyMedia("video/mp4", "https://blossom.band/abc"))
        assertTrue(isMp4FamilyMedia("audio/mp4; codecs=mp4a.40.2", "https://x.com/a"))
        assertFalse(isMp4FamilyMedia(null, "https://x.com/a.mp3"))
        assertFalse(isMp4FamilyMedia("video/webm", "https://x.com/a.webm"))
        assertFalse(isMp4FamilyMedia(null, "https://x.com/live.m3u8"))
    }
}

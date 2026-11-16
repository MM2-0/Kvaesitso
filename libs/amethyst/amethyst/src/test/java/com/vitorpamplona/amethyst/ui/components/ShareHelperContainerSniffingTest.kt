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
package com.vitorpamplona.amethyst.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 * Container sniffing under adversarial bytes.
 *
 * Ported from the reference client's `ImageContainerBytesFuzzTest`. Their
 * walkers strip metadata from a `ByteArray`; ours identifies a container from a
 * file's first bytes, so the oracles carry over even though the code does not:
 * arbitrary input never throws, the answer is deterministic and always a
 * declared kind, a mismatched walker does not claim the container, and only the
 * header can decide — trailing bytes must be irrelevant.
 *
 * That last one is the load-bearing invariant here. A sniffer that read past
 * its header would let attacker-chosen bytes deep inside a file change how the
 * file is labelled, which is exactly what content-type confusion needs.
 */
class ShareHelperContainerSniffingTest {
    private val imageKinds = setOf("jpg", "png", "gif", "webp")
    private val videoKinds = setOf("mp4", "mov", "webm", "avi")

    private lateinit var dir: File

    private fun file(bytes: ByteArray): File {
        if (!::dir.isInitialized) dir = Files.createTempDirectory("sniffing").toFile()
        val f = File.createTempFile("probe", ".bin", dir)
        f.writeBytes(bytes)
        return f
    }

    private fun headers(): List<Pair<String, ByteArray>> =
        listOf(
            "jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01),
            "png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            "gif" to "GIF89a".encodeToByteArray(),
            "webp" to ("RIFF".encodeToByteArray() + ByteArray(4) + "WEBP".encodeToByteArray()),
            "webm" to byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()),
            "avi" to ("RIFF".encodeToByteArray() + ByteArray(4) + "AVI ".encodeToByteArray()),
            "mp4" to (ByteArray(4) + "ftyp".encodeToByteArray() + "isom".encodeToByteArray()),
            "mov" to (ByteArray(4) + "ftyp".encodeToByteArray() + "qt  ".encodeToByteArray()),
        )

    /** Random bytes, truncations, and real headers with random tails. */
    private fun corpus(seed: Int): List<ByteArray> {
        val rnd = Random(seed)
        return buildList {
            repeat(200) { add(ByteArray(rnd.nextInt(0, 64)) { rnd.nextInt(256).toByte() }) }
            headers().forEach { (_, header) ->
                add(header)
                repeat(8) { add(header + ByteArray(rnd.nextInt(0, 128)) { rnd.nextInt(256).toByte() }) }
                // Truncated to every prefix length: the sniffer must survive a
                // header that stops in the middle of a magic number.
                for (cut in 0 until header.size) add(header.copyOfRange(0, cut))
            }
            add(ByteArray(0))
        }
    }

    @Test
    fun sniffingNeverThrowsAndAlwaysNamesADeclaredKind() {
        val seed = 20260914
        corpus(seed).forEach { bytes ->
            val f = file(bytes)
            val image =
                try {
                    ShareHelper.getImageExtension(f)
                } catch (e: Throwable) {
                    throw AssertionError("image sniffing threw on " + bytes.size + " bytes (seed " + seed + ")", e)
                }
            val video =
                try {
                    ShareHelper.getVideoExtension(f)
                } catch (e: Throwable) {
                    throw AssertionError("video sniffing threw on " + bytes.size + " bytes (seed " + seed + ")", e)
                }
            assertTrue("unexpected image kind " + image, image in imageKinds)
            assertTrue("unexpected video kind " + video, video in videoKinds)
        }
    }

    @Test
    fun sniffingIsDeterministic() {
        corpus(20260915).forEach { bytes ->
            val f = file(bytes)
            assertEquals(ShareHelper.getImageExtension(f), ShareHelper.getImageExtension(f))
            assertEquals(ShareHelper.getVideoExtension(f), ShareHelper.getVideoExtension(f))
        }
    }

    @Test
    fun onlyTheHeaderDecides() {
        // Appending arbitrary bytes must not change the verdict. A sniffer that
        // read further would let bytes deep inside a file relabel it.
        val rnd = Random(20260916)
        headers().forEach { (_, header) ->
            val bare = file(header)
            val image = ShareHelper.getImageExtension(bare)
            val video = ShareHelper.getVideoExtension(bare)
            repeat(16) {
                val padded = file(header + ByteArray(rnd.nextInt(1, 512)) { rnd.nextInt(256).toByte() })
                assertEquals("a trailing byte changed the image verdict", image, ShareHelper.getImageExtension(padded))
                assertEquals("a trailing byte changed the video verdict", video, ShareHelper.getVideoExtension(padded))
            }
        }
    }

    @Test
    fun everyRealHeaderIsIdentifiedByItsOwnWalker() {
        headers().forEach { (kind, header) ->
            val f = file(header + ByteArray(32))
            val sniffed = if (kind in imageKinds) ShareHelper.getImageExtension(f) else ShareHelper.getVideoExtension(f)
            assertEquals("header for " + kind + " was not identified", kind, sniffed)
        }
    }

    @Test
    fun aMismatchedWalkerDoesNotClaimTheContainer() {
        // Their "a mismatched walker must reject the container", in the shape
        // our API allows: asking the video sniffer about a JPEG must fall back
        // to the video default rather than reporting an image kind.
        headers().forEach { (kind, header) ->
            val f = file(header + ByteArray(32))
            if (kind in imageKinds) {
                assertTrue("an image was reported as a video kind", ShareHelper.getVideoExtension(f) in videoKinds)
            } else {
                assertTrue("a video was reported as an image kind", ShareHelper.getImageExtension(f) in imageKinds)
            }
        }
    }

    @Test
    fun aTruncatedHeaderFallsBackRatherThanGuessing() {
        // Under four readable bytes there is nothing to decide on, and reading
        // past the end is how a sniffer turns a short file into a crash.
        listOf(ByteArray(0), byteArrayOf(0xFF.toByte()), byteArrayOf(0xFF.toByte(), 0xD8.toByte()), byteArrayOf(0x89.toByte(), 0x50, 0x4E))
            .forEach { bytes ->
                val f = file(bytes)
                assertEquals("jpg", ShareHelper.getImageExtension(f))
                assertEquals("mp4", ShareHelper.getVideoExtension(f))
            }
    }
}

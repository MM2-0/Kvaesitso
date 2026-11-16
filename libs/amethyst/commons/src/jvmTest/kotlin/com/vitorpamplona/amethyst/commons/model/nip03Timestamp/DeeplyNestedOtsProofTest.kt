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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip03Timestamp.ots.DetachedTimestampFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The 1.20.0 crash: a kind 1040 whose proof chains ~100k one-byte SHA-256 ops made
 * `Timestamp.deserialize` recurse until the stack ran out. `StackOverflowError` is not an
 * `Exception`, so it escaped `OtsResolver.verify`, crossed `LoadOts`'s `withContext(Dispatchers.IO)`
 * and killed the app from the main thread. Quartz now caps the nesting; this pins it on the
 * path the app takes, on a stack the size of an Android worker's.
 */
class DeeplyNestedOtsProofTest {
    private val targetId = "a8634f5368e17789e8fb3836cad0e4b9fe4f2288afafc28723c04bebc68a18d6"

    private fun chainedSha256Proof(opCount: Int): ByteArray {
        // Pending attestation to "https://a" after the chain.
        val attestation = byteArrayOf(0x00, 0x83.toByte(), 0xdf.toByte(), 0xe3.toByte(), 0x0d, 0x2e, 0xf9.toByte(), 0x0c, 0x8e.toByte(), 0x0a, 0x09) + "https://a".encodeToByteArray()
        return DetachedTimestampFile.HEADER_MAGIC + byteArrayOf(1, 0x08) + ByteArray(32) + ByteArray(opCount) { 0x08 } + attestation
    }

    private fun attestationNote(proof: ByteArray): Note {
        val event =
            OtsEvent(
                id = "2ad074ddb7724eb13b4244b49cf2321b1057f37fdf8ce102e6329b839cf763a9",
                pubKey = "82fbb08c8ef45c4d71c88368d0ae805bc62fb92f166ab04a0b7a0c83d8cbc29a",
                createdAt = 1706324334,
                tags = arrayOf(arrayOf("e", targetId)),
                content = OtsEvent.encodeOtsState(proof),
                sig = "",
            )
        return Note(event.id).apply { this.event = event }
    }

    /** Runs [block] on a thread with Android's default 1 MB worker stack and rethrows what it threw. */
    private fun <T> onWorkerSizedStack(block: suspend () -> T): T {
        var result: Result<T>? = null
        val thread = Thread(null, { result = runCatching { runBlocking { block() } } }, "ots-worker", 1L shl 20)
        thread.start()
        thread.join()
        return result!!.getOrThrow()
    }

    @Test
    fun deeplyNestedProofIsAnErrorVerdictNotAStackOverflow() {
        val proof = attestationNote(chainedSha256Proof(100_000))
        val target = Note(targetId).apply { addTimestamp(proof) }
        val resolver = EmptyOtsResolverBuilder.build()

        assertNull(onWorkerSizedStack { target.earliestOtsVerifiedTime(resolver) })
        assertIs<VerificationState.Error>(proof.justOtsVerification())
    }
}

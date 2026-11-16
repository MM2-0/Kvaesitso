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
package com.vitorpamplona.nestsclient.audio

import com.vitorpamplona.nestsclient.moq.MoqSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NestBroadcasterTest {
    @Test
    fun pcm_frames_flow_through_encoder_into_publisher_in_order() =
        runTest {
            val capture = ScriptedCapture(listOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)))
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xAA.toByte()))
            val publisher = RecordingPublisher("track")
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)

            broadcaster.start()
            // Capture exhausts after 3 frames (readFrame returns null) so the
            // loop terminates on its own; broadcaster.stop() cleans up the
            // resources but the wire results are observable now.
            capture.awaitDrained()

            assertEquals(
                listOf(byteArrayOf(0xAA.toByte(), 1).toList(), byteArrayOf(0xAA.toByte(), 2).toList(), byteArrayOf(0xAA.toByte(), 3).toList()),
                publisher.sent.map { it.toList() },
            )

            broadcaster.stop()
            assertTrue(capture.startedFlag, "capture should have started")
            assertTrue(capture.stopCount > 0, "capture should have stopped")
            assertTrue(encoder.released, "encoder should have been released")
            assertTrue(publisher.closed, "publisher should have been closed")
        }

    @Test
    fun setMuted_true_keeps_capture_running_but_no_frames_published() =
        runTest {
            val capture = ScriptedCapture(listOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)))
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xBB.toByte()))
            val publisher = RecordingPublisher("track")
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)

            broadcaster.setMuted(true)
            broadcaster.start()
            capture.awaitDrained()

            // Mic was open + encoder ran; publisher saw nothing.
            assertTrue(capture.startedFlag)
            assertEquals(3, encoder.encodeCalls, "encoder still ran while muted (state preserved)")
            assertTrue(publisher.sent.isEmpty(), "no frames should reach the wire while muted")

            broadcaster.stop()
        }

    @Test
    fun encoder_returning_empty_array_is_silently_skipped() =
        runTest {
            val capture = ScriptedCapture(listOf(shortArrayOf(1), shortArrayOf(2)))
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xCC.toByte()), warmupSkips = 1)
            val publisher = RecordingPublisher("track")
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)

            broadcaster.start()
            capture.awaitDrained()

            // First frame was the warmup → encoder returned empty → skipped.
            // Second frame should reach the publisher.
            assertEquals(1, publisher.sent.size)
            assertContentEquals(byteArrayOf(0xCC.toByte(), 2), publisher.sent.single())

            broadcaster.stop()
        }

    @Test
    fun encoder_failure_does_not_stop_the_loop() =
        runTest {
            val capture = ScriptedCapture(listOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)))
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xDD.toByte()), failOnNthCall = 1)
            val publisher = RecordingPublisher("track")
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)
            val errors = mutableListOf<AudioException>()

            broadcaster.start(onError = { errors.add(it) })
            capture.awaitDrained()

            assertEquals(1, errors.size, "exactly one encode error should have been reported")
            assertEquals(2, publisher.sent.size, "two non-failed frames should have reached the publisher")
            broadcaster.stop()
        }

    @Test
    fun onTerminalFailure_fires_once_after_consecutive_send_failures() =
        runTest {
            // Drive MAX_CONSECUTIVE_SEND_ERRORS + 1 frames through a
            // publisher that always throws. The broadcaster must:
            //   - keep going past one failure (we already cover that)
            //   - bail eventually
            //   - fire onTerminalFailure exactly once
            //   - stop pulling from capture
            val frameCount = NestBroadcaster.MAX_CONSECUTIVE_SEND_ERRORS + 50
            val frames = List(frameCount) { shortArrayOf(it.toShort()) }
            val capture = ScriptedCapture(frames)
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xFF.toByte()))
            val publisher = ThrowingPublisher()
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)
            val errors = mutableListOf<AudioException>()
            var terminalFailureCount = 0

            broadcaster.start(
                onTerminalFailure = { terminalFailureCount += 1 },
                onError = { errors.add(it) },
            )

            // Wait until the broadcaster has bailed. The bail closes the
            // launched job, but capture.awaitDrained only fires on EOF —
            // so poll on the terminal-failure counter instead.
            while (terminalFailureCount == 0) kotlinx.coroutines.yield()

            assertEquals(1, terminalFailureCount, "onTerminalFailure should fire exactly once")
            // We saw at least MAX_CONSECUTIVE_SEND_ERRORS failures before
            // the bail, plus one "gave up" message. ScriptedEncoder
            // doesn't fail, so all errors are publisher.send failures.
            assertTrue(
                errors.size >= NestBroadcaster.MAX_CONSECUTIVE_SEND_ERRORS,
                "should have seen ≥ ${NestBroadcaster.MAX_CONSECUTIVE_SEND_ERRORS} errors, got ${errors.size}",
            )
            broadcaster.stop()
        }

    @Test
    fun stop_is_idempotent() =
        runTest {
            val capture = ScriptedCapture(listOf(shortArrayOf(1)))
            val encoder = ScriptedEncoder(prefix = byteArrayOf(0xEE.toByte()))
            val publisher = RecordingPublisher("track")
            val broadcaster = NestBroadcaster(capture, encoder, publisher, backgroundScope)

            broadcaster.start()
            capture.awaitDrained()
            broadcaster.stop()
            broadcaster.stop()
            broadcaster.stop()

            assertEquals(1, capture.stopCount)
            assertTrue(encoder.released)
            assertTrue(publisher.closed)
        }

    // ---------- Fakes ----------

    private class ScriptedCapture(
        frames: List<ShortArray>,
    ) : AudioCapture {
        private val queue = Channel<ShortArray>(capacity = frames.size + 1)
        private val drainSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
        var startedFlag = false
            private set
        var stopCount = 0
            private set

        init {
            frames.forEach { queue.trySend(it) }
            queue.close()
        }

        override fun start() {
            startedFlag = true
        }

        override suspend fun readFrame(): ShortArray? {
            val next = queue.receiveCatching().getOrNull()
            if (next == null && !drainSignal.isCompleted) drainSignal.complete(Unit)
            return next
        }

        override fun stop() {
            stopCount++
        }

        suspend fun awaitDrained() {
            drainSignal.await()
        }
    }

    private class ScriptedEncoder(
        private val prefix: ByteArray,
        private val warmupSkips: Int = 0,
        private val failOnNthCall: Int = -1, // -1 = never fail
    ) : OpusEncoder {
        var encodeCalls: Int = 0
            private set
        var released: Boolean = false
            private set

        override fun encode(pcm: ShortArray): ByteArray {
            val callIndex = encodeCalls
            encodeCalls += 1
            if (callIndex == failOnNthCall) error("scripted encoder failure at call $callIndex")
            if (callIndex < warmupSkips) return ByteArray(0)
            // Append the first PCM sample byte so tests can verify ordering.
            return prefix + byteArrayOf(pcm.first().toByte())
        }

        override fun release() {
            released = true
        }
    }

    private class RecordingPublisher(
        nameStr: String,
    ) : MoqSession.TrackPublisher {
        override val name: ByteArray = nameStr.encodeToByteArray()
        val sent: MutableList<ByteArray> = mutableListOf()
        var closed: Boolean = false
            private set

        override suspend fun send(payload: ByteArray): Boolean {
            sent.add(payload)
            return true
        }

        override suspend fun close() {
            closed = true
        }
    }

    /** Publisher whose send() always throws — used to drive the bail path. */
    private class ThrowingPublisher : MoqSession.TrackPublisher {
        override val name: ByteArray = "throwing".encodeToByteArray()

        override suspend fun send(payload: ByteArray): Boolean = error("publisher.send failure")

        override suspend fun close() {}
    }
}

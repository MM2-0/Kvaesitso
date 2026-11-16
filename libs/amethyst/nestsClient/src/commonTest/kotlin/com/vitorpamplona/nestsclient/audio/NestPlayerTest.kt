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

import com.vitorpamplona.nestsclient.moq.MoqObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NestPlayerTest {
    @Test
    fun every_object_payload_is_decoded_and_enqueued_in_order() =
        runTest {
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01, 0x02)),
                    moqObject(byteArrayOf(0x03)),
                    moqObject(byteArrayOf(0x04, 0x05, 0x06)),
                )

            val sut = NestPlayer(decoder, player, this)
            sut.play(objects)
            testScheduler.advanceUntilIdle()

            assertTrue(player.started)
            assertContentEquals(
                expected =
                    listOf(
                        byteToShorts(byteArrayOf(0x01, 0x02)),
                        byteToShorts(byteArrayOf(0x03)),
                        byteToShorts(byteArrayOf(0x04, 0x05, 0x06)),
                    ).flatten(),
                actual = player.queued.flatten(),
            )
            sut.stop()
            assertTrue(player.stopped)
            assertTrue(decoder.released)
        }

    @Test
    fun decoder_failure_invokes_onError_but_loop_continues() =
        runTest {
            val errors = mutableListOf<AudioException>()
            val decoder =
                FakeOpusDecoder { bytes ->
                    if (bytes.contentEquals(byteArrayOf(0xFF.toByte()))) {
                        throw IllegalStateException("synthetic decoder error")
                    }
                    byteToShorts(bytes)
                }
            val player = FakeAudioPlayer()
            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01)),
                    moqObject(byteArrayOf(0xFF.toByte())),
                    moqObject(byteArrayOf(0x02)),
                )

            val sut = NestPlayer(decoder, player, this)
            sut.play(objects, onError = { errors.add(it) })
            testScheduler.advanceUntilIdle()

            assertEquals(1, errors.size)
            assertEquals(AudioException.Kind.DecoderError, errors.single().kind)
            // The good packets either side of the bad one still made it through.
            assertContentEquals(
                listOf(byteToShorts(byteArrayOf(0x01)), byteToShorts(byteArrayOf(0x02))).flatten(),
                player.queued.flatten(),
            )
            sut.stop()
        }

    @Test
    fun stop_is_idempotent_and_releases_decoder_only_once() =
        runTest {
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()
            val sut = NestPlayer(decoder, player, this)
            sut.play(flowOf())
            testScheduler.advanceUntilIdle()
            sut.stop()
            sut.stop() // second call must not double-release
            assertEquals(1, decoder.releaseCount)
            assertEquals(1, player.stopCount)
        }

    @Test
    fun play_cannot_be_called_twice_on_the_same_instance() =
        runTest {
            val sut =
                NestPlayer(
                    FakeOpusDecoder { byteToShorts(it) },
                    FakeAudioPlayer(),
                    this,
                )
            sut.play(flowOf())
            assertFailsWith<IllegalStateException> { sut.play(flowOf()) }
            sut.stop()
        }

    @Test
    fun play_after_stop_is_rejected() =
        runTest {
            val sut =
                NestPlayer(
                    FakeOpusDecoder { byteToShorts(it) },
                    FakeAudioPlayer(),
                    this,
                )
            sut.stop()
            assertFailsWith<IllegalStateException> { sut.play(flowOf()) }
        }

    @Test
    fun decoder_emitting_empty_pcm_does_not_call_player_enqueue() =
        runTest {
            val decoder = FakeOpusDecoder { ShortArray(0) }
            val player = FakeAudioPlayer()
            val sut = NestPlayer(decoder, player, this)
            sut.play(flowOf(moqObject(byteArrayOf(0x01))))
            testScheduler.advanceUntilIdle()
            assertEquals(0, player.queued.size)
            sut.stop()
        }

    @Test
    fun on_level_reports_normalized_peak_per_decoded_frame() =
        runTest {
            // Decode each input byte to a single 16-bit sample with a
            // known peak so we can assert the level math directly.
            // 0x01 -> 1/32768 (~0), 0x40 -> 0x4000 (0.5), 0x7F -> 0x7FFF (max).
            val decoder = FakeOpusDecoder { bytes -> ShortArray(1) { (bytes[0].toInt() shl 8).toShort() } }
            val player = FakeAudioPlayer()
            val levels = mutableListOf<Float>()

            val sut = NestPlayer(decoder, player, this)
            sut.play(
                objects =
                    flowOf(
                        moqObject(byteArrayOf(0x40)),
                        moqObject(byteArrayOf(0x7F)),
                    ),
                onLevel = { levels.add(it) },
            )
            testScheduler.advanceUntilIdle()

            assertEquals(2, levels.size)
            // 0x40 << 8 = 0x4000 = 16384 → 16384/32768 = 0.5
            assertTrue(levels[0] in 0.49f..0.51f, "expected ~0.5, got ${levels[0]}")
            // 0x7F << 8 = 0x7F00 → 0x7F00/32768 ≈ 0.992
            assertTrue(levels[1] > 0.98f, "expected near-max, got ${levels[1]}")
            sut.stop()
        }

    @Test
    fun on_level_is_skipped_when_decoder_returns_empty_pcm() =
        runTest {
            val decoder = FakeOpusDecoder { ShortArray(0) }
            val levels = mutableListOf<Float>()
            val sut = NestPlayer(decoder, FakeAudioPlayer(), this)
            sut.play(
                objects = flowOf(moqObject(byteArrayOf(0x01))),
                onLevel = { levels.add(it) },
            )
            testScheduler.advanceUntilIdle()
            assertEquals(0, levels.size)
            sut.stop()
        }

    @Test
    fun objects_arriving_after_play_are_streamed_through_the_pipeline() =
        runTest {
            val channel = Channel<MoqObject>(capacity = 8)
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val sut = NestPlayer(decoder, player, this)
            sut.play(channel.receiveAsFlow())
            testScheduler.runCurrent()

            channel.send(moqObject(byteArrayOf(0x10)))
            channel.send(moqObject(byteArrayOf(0x20)))
            testScheduler.advanceUntilIdle()

            assertEquals(2, player.queued.size)
            assertContentEquals(byteToShorts(byteArrayOf(0x10)), player.queued[0])
            assertContentEquals(byteToShorts(byteArrayOf(0x20)), player.queued[1])

            sut.stop()
        }

    /**
     * Pre-roll: with `prerollFrames=3`, [AudioPlayer.beginPlayback]
     * must NOT fire until the third decoded frame has arrived, and
     * once it fires the AudioPlayer's queue must already contain
     * the buffered pre-roll (i.e. flushed atomically with playback
     * start, not lazily on the next enqueue).
     */
    @Test
    fun preroll_defers_beginPlayback_until_threshold_is_met() =
        runTest {
            val channel = Channel<MoqObject>(capacity = 8)
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val sut =
                NestPlayer(
                    initialDecoder = decoder,
                    player = player,
                    scope = this,
                    prerollFrames = 3,
                )
            sut.play(channel.receiveAsFlow())
            testScheduler.runCurrent()

            // Frames 1 and 2: pre-roll buffer fills, beginPlayback NOT
            // called yet, AudioPlayer hasn't seen a single enqueue.
            channel.send(moqObject(byteArrayOf(0x01)))
            channel.send(moqObject(byteArrayOf(0x02)))
            testScheduler.advanceUntilIdle()
            assertEquals(0, player.beginPlaybackCount, "beginPlayback before threshold")
            assertEquals(0, player.queued.size, "no enqueue before threshold")

            // Frame 3 trips the threshold: beginPlayback fires exactly
            // once, and the pre-rolled frames must ALREADY be sitting
            // in the AudioPlayer queue at that moment (otherwise the
            // device starts playback against an empty buffer and
            // pre-roll's whole point is defeated). [NestPlayer.play]
            // implements this by flushing-then-beginPlayback —
            // AudioTrack MODE_STREAM explicitly supports write()
            // before play().
            channel.send(moqObject(byteArrayOf(0x03)))
            testScheduler.advanceUntilIdle()
            assertEquals(1, player.beginPlaybackCount)
            // The fake records the queue size at the moment
            // beginPlayback fires. With flush-then-begin ordering,
            // all 3 pre-rolled frames are already in the queue.
            assertEquals(3, player.queuedAtBeginPlayback, "pre-roll flushed before beginPlayback")
            assertEquals(3, player.queued.size, "buffer populated when device starts")

            // Subsequent frames bypass the buffer and go directly
            // through enqueue.
            channel.send(moqObject(byteArrayOf(0x04)))
            testScheduler.advanceUntilIdle()
            assertEquals(1, player.beginPlaybackCount, "beginPlayback only fires once")
            assertEquals(4, player.queued.size)

            sut.stop()
        }

    /**
     * Pre-roll: a flow that ends BEFORE the pre-roll threshold fires
     * must still flush its partial buffer to the AudioPlayer.
     * Otherwise a fast-cycling publisher could leave already-decoded
     * frames stranded forever.
     */
    @Test
    fun preroll_flushes_partial_buffer_when_flow_ends_early() =
        runTest {
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()

            val sut =
                NestPlayer(
                    initialDecoder = decoder,
                    player = player,
                    scope = this,
                    prerollFrames = 5,
                )
            // Only 2 frames — pre-roll never reaches its 5-frame floor,
            // but the upstream Flow ends so the loop's flush hook must
            // begin playback and drain whatever's queued.
            sut.play(
                flowOf(
                    moqObject(byteArrayOf(0x01)),
                    moqObject(byteArrayOf(0x02)),
                ),
            )
            testScheduler.advanceUntilIdle()

            assertEquals(1, player.beginPlaybackCount)
            assertEquals(2, player.queued.size)
            assertContentEquals(byteToShorts(byteArrayOf(0x01)), player.queued[0])
            assertContentEquals(byteToShorts(byteArrayOf(0x02)), player.queued[1])

            sut.stop()
        }

    /**
     * Pre-roll edge case: an empty flow shouldn't start playback at
     * all. The AudioTrack stays in its allocated-but-not-playing
     * state until [stop] tears it down.
     */
    @Test
    fun preroll_does_not_begin_playback_when_flow_emits_no_pcm() =
        runTest {
            val decoder = FakeOpusDecoder { ShortArray(0) }
            val player = FakeAudioPlayer()

            val sut =
                NestPlayer(
                    initialDecoder = decoder,
                    player = player,
                    scope = this,
                    prerollFrames = 3,
                )
            sut.play(flowOf(moqObject(byteArrayOf(0x01)), moqObject(byteArrayOf(0x02))))
            testScheduler.advanceUntilIdle()

            assertEquals(0, player.beginPlaybackCount, "no PCM → no playback")
            assertEquals(0, player.queued.size)

            sut.stop()
        }

    @Test
    fun publisher_boundary_rebuilds_decoder_when_factory_provided() =
        runTest {
            // Two distinct decoders so we can prove the factory was
            // invoked. After the trackAlias change, frames should
            // route through `decoderB`, NOT `decoderA`.
            val decoderA = FakeOpusDecoder { ShortArray(it.size) { _ -> 0xAA.toShort() } }
            val decoderB = FakeOpusDecoder { ShortArray(it.size) { _ -> 0xBB.toShort() } }
            val factoryCallCount = AtomicInteger(0)
            val factory: () -> OpusDecoder = {
                if (factoryCallCount.getAndIncrement() == 0) decoderA else decoderB
            }
            val player = FakeAudioPlayer()

            val objects =
                flowOf(
                    // First subscription cycle: trackAlias = 7
                    moqObject(byteArrayOf(0x01), trackAlias = 7L),
                    moqObject(byteArrayOf(0x02), trackAlias = 7L),
                    // Wrapper re-issued — new SUBSCRIBE produces a
                    // different trackAlias. Decoder MUST be rebuilt
                    // before the next decode runs.
                    moqObject(byteArrayOf(0x03), trackAlias = 8L),
                    moqObject(byteArrayOf(0x04), trackAlias = 8L),
                )

            val sut =
                NestPlayer(
                    initialDecoder = factory(),
                    player = player,
                    scope = this,
                    decoderFactory = factory,
                )
            sut.play(objects)
            testScheduler.advanceUntilIdle()

            assertEquals(2, factoryCallCount.get(), "factory invoked twice: initial + boundary")
            assertEquals(1, decoderA.releaseCount, "decoderA released on the boundary")
            assertEquals(0, decoderB.releaseCount, "decoderB still alive (released on stop)")
            sut.stop()
            assertEquals(1, decoderB.releaseCount, "decoderB released on stop")
        }

    @Test
    fun publisher_boundary_keeps_old_decoder_when_factory_throws() =
        runTest {
            // The decoder field MUST NOT be left referencing a released
            // decoder if the factory throws — every subsequent decode
            // would otherwise fail with `IllegalStateException` and the
            // subscription would be permanently dead.
            val decoderA = FakeOpusDecoder { ShortArray(it.size) { _ -> 0xAA.toShort() } }
            val factoryFailures = AtomicInteger(0)
            val factory: () -> OpusDecoder = {
                factoryFailures.incrementAndGet()
                throw IllegalStateException("synthetic factory failure")
            }
            val player = FakeAudioPlayer()

            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01), trackAlias = 7L),
                    // Boundary: factory throws → decoderA must stay alive
                    // and decode the next frame normally.
                    moqObject(byteArrayOf(0x02), trackAlias = 8L),
                )

            val sut =
                NestPlayer(
                    initialDecoder = decoderA,
                    player = player,
                    scope = this,
                    decoderFactory = factory,
                )
            sut.play(objects)
            testScheduler.advanceUntilIdle()

            assertEquals(1, factoryFailures.get(), "factory invoked once on the boundary")
            // decoderA still alive → both frames decoded through it.
            assertEquals(2, player.queued.size)
            assertEquals(0, decoderA.releaseCount, "decoderA NOT released on factory failure")
            sut.stop()
            assertEquals(1, decoderA.releaseCount, "decoderA released exactly once on stop")
        }

    @Test
    fun publisher_boundary_no_op_when_factory_is_null() =
        runTest {
            // Without a factory, NestPlayer keeps the same decoder
            // across trackAlias changes — backwards-compat path.
            val decoder = FakeOpusDecoder { byteToShorts(it) }
            val player = FakeAudioPlayer()
            val objects =
                flowOf(
                    moqObject(byteArrayOf(0x01), trackAlias = 7L),
                    moqObject(byteArrayOf(0x02), trackAlias = 8L),
                )

            val sut = NestPlayer(decoder, player, this)
            sut.play(objects)
            testScheduler.advanceUntilIdle()

            assertEquals(0, decoder.releaseCount, "no boundary-driven release without a factory")
            sut.stop()
            assertEquals(1, decoder.releaseCount, "released exactly once on stop")
        }

    // -- helpers -----------------------------------------------------------

    private fun moqObject(
        payload: ByteArray,
        trackAlias: Long = 1L,
    ): MoqObject =
        MoqObject(
            trackAlias = trackAlias,
            groupId = 0,
            objectId = 0,
            publisherPriority = 0x80,
            payload = payload,
        )

    private fun byteToShorts(b: ByteArray): ShortArray = ShortArray(b.size) { b[it].toShort() }

    private class FakeOpusDecoder(
        private val transform: (ByteArray) -> ShortArray,
    ) : OpusDecoder {
        var releaseCount = 0
            private set
        val released: Boolean get() = releaseCount > 0

        override fun decode(opusPacket: ByteArray): ShortArray = transform(opusPacket)

        override fun release() {
            releaseCount++
        }
    }

    private class FakeAudioPlayer : AudioPlayer {
        var started = false
            private set

        /**
         * Counts the [AudioPlayer.beginPlayback] invocations. Used by
         * the pre-roll regression tests to assert that playback only
         * begins AFTER `prerollFrames` decoded frames have arrived (or
         * after the upstream flow ends with a partial buffer). The
         * default no-op `beginPlayback` in the interface lets fakes
         * skip overriding when they don't care; we override here so
         * the tests can verify the pre-roll wiring.
         *
         * Also tracks the size of `queued` at the moment beginPlayback
         * fired — pre-roll's contract is that the buffer is flushed
         * IN A TIGHT LOOP after beginPlayback, so we can read the
         * snapshot to verify the flush ordering.
         */
        var beginPlaybackCount = 0
            private set
        var queuedAtBeginPlayback: Int = -1
            private set

        var stopCount = 0
            private set
        val stopped: Boolean get() = stopCount > 0
        val queued = mutableListOf<ShortArray>()
        var muted: Boolean = false
            private set

        override fun start() {
            started = true
        }

        override fun beginPlayback() {
            beginPlaybackCount++
            queuedAtBeginPlayback = queued.size
        }

        override suspend fun enqueue(pcm: ShortArray) {
            queued.add(pcm)
        }

        override fun setMuted(muted: Boolean) {
            this.muted = muted
        }

        override fun stop() {
            stopCount++
        }
    }
}

/** Small helper so test assertions can flatten lists of ShortArrays. */
private fun List<ShortArray>.flatten(): List<Short> = flatMap { sa -> sa.toList() }

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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster
import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle

/**
 * [BroadcastHandle] returned by [MoqLiteNestsSpeaker.startBroadcasting]
 * for the non-reconnecting code path. Wraps:
 *
 *   - the long-lived audio [broadcaster] (mic + encoder + send loop),
 *   - the audio-track [publisher] on the moq-lite session,
 *   - the [catalogPublisher] companion track that emits the
 *     `catalog.json` manifest on every new SUBSCRIBE.
 *
 * Mute is forwarded to the broadcaster (which also reports the user-
 * facing state back via [parent.reportMuteState]). [close] tears down
 * all three components in a fixed order — broadcaster → audio
 * publisher → catalog publisher — so a `CancellationException`
 * mid-shutdown still releases every resource before re-throwing.
 *
 * Reconnecting / hot-swap callers go through `ReissuingBroadcastHandle`
 * in `ReconnectingNestsSpeaker` instead, which manages the audio +
 * catalog publishers across session swaps.
 */
internal class MoqLiteBroadcastHandle(
    private val broadcaster: NestMoqLiteBroadcaster,
    private val publisher: MoqLitePublisherHandle,
    private val catalogPublisher: MoqLitePublisherHandle,
    private val parent: MoqLiteNestsSpeaker,
) : BroadcastHandle {
    @Volatile private var muted: Boolean = false

    @Volatile private var closed: Boolean = false

    override val isMuted: Boolean get() = muted

    override suspend fun setMuted(muted: Boolean) {
        if (closed) return
        this.muted = muted
        broadcaster.setMuted(muted)
        parent.reportMuteState(muted)
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        // Stop the broadcaster first so the audio capture + encoder
        // don't keep producing into a closing publisher.
        try {
            broadcaster.stop()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Even on cancel, run the rest of cleanup before rethrowing
            // — broadcaster.stop already cancels its own job, so the
            // mic + encoder + publisher are owed their close paths.
            runCatching { catalogPublisher.close() }
            runCatching { publisher.close() }
            parent.broadcastClosed(this)
            throw ce
        } catch (_: Throwable) {
            // Best-effort; fall through to the defensive publisher.close.
        }
        // broadcaster.stop() already calls publisher.close(); call again
        // defensively to make this method idempotent against partial
        // failures on the broadcaster.stop path.
        try {
            publisher.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            runCatching { catalogPublisher.close() }
            parent.broadcastClosed(this)
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
        try {
            catalogPublisher.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            parent.broadcastClosed(this)
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
        parent.broadcastClosed(this)
    }
}

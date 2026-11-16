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

import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle

/**
 * Internal hot-swap seam: speakers that expose this interface let the
 * reconnect wrapper retarget a long-lived
 * [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster] onto a
 * freshly-opened moq-lite session's publisher without restarting the
 * AudioRecord / Opus encoder pipeline. Implemented by
 * [MoqLiteNestsSpeaker]; not implemented by the IETF reference
 * [DefaultNestsSpeaker], which falls back to the close-then-restart path
 * inside [com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker].
 *
 * The wrapper uses an `as?` cast to detect support so this interface
 * can stay package-internal — protocol consumers never see it.
 */
internal interface HotSwappablePublisherSource {
    /**
     * Open a fresh [MoqLitePublisherHandle] on the underlying moq-lite
     * session. Caller owns the returned handle's lifetime (typically
     * via [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster.swapPublisher]'s
     * close-the-old contract).
     *
     * @param startSequence first group sequence the new publisher will
     *   assign. Used by the hot-swap path to seed the new session's
     *   audio track with the previous session's
     *   [MoqLitePublisherHandle.nextSequence] so kixelated/hang's
     *   `Container.Consumer.#run` doesn't drop every post-recycle
     *   group as `sequence < #active`. Pass `0L` for fresh (non-
     *   continuation) publishers — the catalog track is one such
     *   case, since its `#active` semantics are different from audio.
     */
    suspend fun openPublisherForHotSwap(
        track: String,
        startSequence: Long = 0L,
    ): MoqLitePublisherHandle

    /**
     * Flip the speaker's outward state to [NestsSpeakerState.Broadcasting].
     *
     * The hot-swap pump owns its own long-lived broadcaster and opens
     * publishers via [openPublisherForHotSwap] instead of going through
     * [MoqLiteNestsSpeaker.startBroadcasting], so the speaker's state
     * machine never sees the Connected → Broadcasting transition that
     * `startBroadcasting` performs. Without this call the reconnect
     * wrapper — which mirrors the underlying speaker's state — stays
     * stuck on Connected forever and callers waiting on Broadcasting
     * (the production VM, interop tests) time out.
     *
     * Called once per session iteration after the audio publisher is
     * installed, so a freshly-swapped session re-enters Broadcasting
     * too. No-op when the speaker is already terminal.
     *
     * @param isMuted the user's current mute intent, replayed onto the
     *   new state so the swap doesn't reset the mute indicator.
     */
    fun reportBroadcasting(isMuted: Boolean)

    /**
     * Surface a broadcast-pipeline terminal failure (e.g. sustained
     * `publisher.send` errors past
     * [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster.MAX_CONSECUTIVE_SEND_ERRORS])
     * by flipping the speaker's state to [NestsSpeakerState.Failed].
     * Called by the hot-swap pump when the long-lived broadcaster's
     * `onTerminalFailure` fires; lets the reconnect orchestrator
     * observe the terminal state and recycle the session, matching
     * the legacy
     * [MoqLiteNestsSpeaker.startBroadcasting] path's failure
     * propagation.
     */
    fun reportBroadcastTerminalFailure()
}

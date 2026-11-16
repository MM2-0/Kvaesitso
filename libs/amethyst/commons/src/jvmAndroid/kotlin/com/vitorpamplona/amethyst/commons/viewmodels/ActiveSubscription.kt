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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.NestPlayer
import com.vitorpamplona.nestsclient.moq.SubscribeHandle

/**
 * Per-pubkey state slot held in [NestViewModel.activeSubscriptions].
 *
 * Three lifecycle phases:
 *   - **Pending**: just-reserved by `reconcileSubscriptions` to dedupe
 *     concurrent reconciles. No handle / player attached yet. Constructor
 *     via [pending].
 *   - **Active**: [attach] wires the moq subscribe handle, the
 *     [NestPlayer] decode loop, and the device player. [isPlaying]
 *     flips true.
 *   - **Detached**: [detach] returns the handle + roomPlayer pair so the
 *     caller can run the suspending teardown (`NestPlayer.stop()` +
 *     `SubscribeHandle.unsubscribe()`) in its own coroutine scope —
 *     keeps the native MediaCodec / AudioTrack release ordered after
 *     the decode loop has unwound (audit MoQ #11/#12).
 *
 * `internal` because only [NestViewModel]'s subscription-lifecycle
 * paths (open / close / reconcile / mute / hush) construct or mutate
 * these. Lifted to a top-level type rather than a nested class so the
 * file split tracks concerns: this class is "subscription state
 * machine"; the rest of NestViewModel is "ViewModel public surface +
 * orchestration."
 */
internal class ActiveSubscription private constructor(
    val pubkey: String,
) {
    private var handle: SubscribeHandle? = null
    private var roomPlayer: NestPlayer? = null
    var player: AudioPlayer? = null
        private set
    var isPlaying: Boolean = false
        private set

    fun attach(
        handle: SubscribeHandle,
        roomPlayer: NestPlayer,
        player: AudioPlayer,
    ) {
        this.handle = handle
        this.roomPlayer = roomPlayer
        this.player = player
        this.isPlaying = true
    }

    /**
     * Hand the player + handle back to the caller's coroutine scope —
     * `NestPlayer.stop()` and `SubscribeHandle.unsubscribe()` are
     * both suspend, and the caller has the right scope to await them
     * (so native MediaCodec/AudioTrack release runs after the decode
     * loop has unwound, per audit MoQ #11/#12).
     */
    fun detach(): Pair<NestPlayer?, SubscribeHandle?> {
        isPlaying = false
        val p = roomPlayer
        val h = handle
        roomPlayer = null
        handle = null
        player = null
        return p to h
    }

    companion object {
        fun pending(pubkey: String) = ActiveSubscription(pubkey)
    }
}

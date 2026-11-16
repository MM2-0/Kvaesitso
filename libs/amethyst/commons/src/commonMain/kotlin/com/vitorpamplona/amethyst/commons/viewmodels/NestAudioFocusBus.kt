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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide audio-focus signal published by the platform-side audio
 * focus listener (Android: `NestForegroundService`'s
 * `OnAudioFocusChangeListener`) and consumed by every active
 * [NestViewModel].
 *
 * Decoupled from the platform audio APIs so commons can stay free of
 * `android.media.AudioManager` references — the platform layer
 * translates focus-change codes into the small enum below before
 * publishing.
 *
 * Why a singleton: there's at most one foreground audio-room session
 * per process (the foreground service is unique), and every ViewModel
 * wants to observe the same focus signal, so keying by some other
 * dimension would just complicate the wiring. The bus survives
 * activity / VM rotations naturally.
 */
object NestAudioFocusBus {
    private val _state = MutableStateFlow(NestAudioFocusState.Granted)

    /**
     * The latest audio-focus state. Defaults to [NestAudioFocusState.Granted]
     * — i.e. "we own playback" — so consumers don't have to special-case
     * the boot-up window before the foreground service has a chance to
     * register its listener.
     */
    val state: StateFlow<NestAudioFocusState> = _state.asStateFlow()

    /**
     * Publish a new focus state. Only the platform-side listener calls
     * this; consumers observe via [state].
     */
    fun publish(newState: NestAudioFocusState) {
        _state.value = newState
    }
}

/**
 * Audio-focus state translated from the platform's `AUDIOFOCUS_*` codes.
 * Maps them onto the three actions an audio-room app actually cares
 * about: keep playing, pause-because-something-else-is-playing, and
 * stop-because-something-else-is-now-the-primary-audio-source.
 */
enum class NestAudioFocusState {
    /** We own playback. Normal operation. */
    Granted,

    /**
     * We've lost focus temporarily — typical triggers are an inbound
     * phone call, a maps voice prompt, a system alarm. The
     * [NestViewModel] reacts by silencing playback + the broadcast
     * mic (the user-visible "muted" state stays unchanged so it
     * restores on regain). Audio pipeline keeps running so resume
     * is sample-accurate.
     */
    TransientLoss,

    /**
     * Permanent focus loss — another app has taken over as the
     * primary audio source for the foreseeable future. Same effective
     * action as [TransientLoss] in v1 (silence both directions), but
     * the distinction is preserved so future enhancements can tear
     * down the audio device entirely on long-form loss.
     */
    Loss,
}

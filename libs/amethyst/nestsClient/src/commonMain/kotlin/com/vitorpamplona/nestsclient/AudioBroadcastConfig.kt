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

/**
 * Per-broadcast audio shape negotiated between the speaker, the catalog
 * the relay forwards, and the listeners that subscribe to the audio
 * track.
 *
 * Threaded through [connectNestsSpeaker] / [connectReconnectingNestsSpeaker]
 * into [MoqLiteNestsSpeaker.startBroadcasting] so a stereo broadcaster
 * can pick a stereo Opus rendition without forcing every existing mono
 * call site to grow a parameter. The catalog factory in
 * [com.vitorpamplona.nestsclient.moq.lite.MoqLiteHangCatalog.opus48kJsonBytes]
 * keys on this shape so the JSON wire bytes stay byte-stable per shape.
 *
 * **Caller contract:** [channelCount] MUST match the channel count of
 * the [com.vitorpamplona.nestsclient.audio.OpusEncoder] the caller
 * provides via the speaker's `encoderFactory` AND the channel count of
 * the PCM frames the caller's [com.vitorpamplona.nestsclient.audio.AudioCapture]
 * produces. Mismatches surface as decoder errors on the listener side
 * (CSD-0 channel byte vs interleaved PCM layout disagree) and produce
 * either silence or downmix-with-clicks depending on the listener
 * implementation. There is no in-broadcast renegotiation — pick the
 * shape at speaker open time.
 *
 * Default is mono (the historical shape). Callers that don't pass a
 * config keep the prior behaviour.
 *
 * @property channelCount 1 (mono) or 2 (stereo, L/R interleaved).
 *   Drives the catalog's `numberOfChannels` field. Multi-rendition
 *   catalogs (e.g. one mono + one stereo on the same broadcast) are
 *   out of scope here — model that as two separate catalog factories
 *   if it becomes useful.
 */
data class AudioBroadcastConfig(
    val channelCount: Int = 1,
) {
    init {
        require(channelCount in 1..2) {
            "AudioBroadcastConfig supports mono (1) or stereo (2) only, got $channelCount"
        }
    }
}

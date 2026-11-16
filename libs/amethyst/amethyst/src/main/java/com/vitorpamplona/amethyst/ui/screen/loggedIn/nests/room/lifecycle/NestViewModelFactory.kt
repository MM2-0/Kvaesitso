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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.OkHttpNestsClient
import com.vitorpamplona.nestsclient.audio.AudioRecordCapture
import com.vitorpamplona.nestsclient.audio.AudioTrackPlayer
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusDecoder
import com.vitorpamplona.nestsclient.audio.MediaCodecOpusEncoder
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * Android-side Factory for [NestViewModel]. The ViewModel itself lives
 * in `commons/` so a future desktop port can reuse the orchestration once
 * Compose Desktop has WebTransport; this factory binds it to the Android
 * actuals (OkHttp HTTP, pure-Kotlin QUIC, MediaCodec Opus, AudioTrack +
 * AudioRecord on the speaker side).
 */
internal class NestViewModelFactory(
    private val signer: NostrSigner,
    private val room: NestsRoomConfig,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NestViewModel(
            // Named parameter — `OkHttpNestsClient`'s primary constructor
            // declares `callTimeoutMs` first with a default, so the
            // function reference must be passed explicitly to the
            // `httpClient` slot rather than as the first positional arg.
            httpClient = OkHttpNestsClient(httpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForVideo),
            transport = QuicWebTransportFactory(),
            decoderFactory = { channelCount, sampleRate ->
                MediaCodecOpusDecoder(channelCount = channelCount, sampleRate = sampleRate)
            },
            playerFactory = { channelCount, sampleRate ->
                AudioTrackPlayer(channelCount = channelCount, sampleRate = sampleRate)
            },
            signer = signer,
            room = room,
            captureFactory = { AudioRecordCapture() },
            encoderFactory = { MediaCodecOpusEncoder() },
            cleanupScope = Amethyst.instance.applicationIOScope,
        ) as T
}

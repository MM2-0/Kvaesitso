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
package com.vitorpamplona.nestsclient.moq.lite

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Publisher-side model for the kixelated/moq `hang` `catalog.json`
 * track. Kept distinct from
 * `com.vitorpamplona.amethyst.commons.viewmodels.RoomSpeakerCatalog`
 * (the parser in `:commons`) because `:nestsClient` does not depend on
 * `:commons` — both classes target the same wire shape independently.
 *
 * Wire shape (verbatim from `kixelated/moq/rs/hang/src/catalog/`):
 *
 *   {
 *     "audio": {
 *       "renditions": {
 *         "<trackName>": {
 *           "codec": "opus",
 *           "container": { "kind": "legacy" },
 *           "sampleRate": 48000,
 *           "numberOfChannels": 1,
 *           "jitter": 20,              // ms; matches one Opus frame
 *           "bitrate": 32000           // optional
 *         }
 *       }
 *     }
 *   }
 *
 * Field names are camelCase per the upstream serde
 * `rename_all = "camelCase"`. Every Amethyst-emitted field is required
 * here (no nullable defaults) so the encoded JSON is stable byte-for-
 * byte across runs and we don't accidentally publish `"bitrate": null`
 * if hang.js's parser turns out to reject it.
 */
@Serializable
internal data class MoqLiteHangCatalog(
    val audio: Audio,
) {
    @Serializable
    data class Audio(
        val renditions: Map<String, AudioRendition>,
    )

    @Serializable
    data class AudioRendition(
        val codec: String,
        val container: Container,
        val sampleRate: Int,
        val numberOfChannels: Int,
        /**
         * Publisher-side cadence hint (ms). hang.js's watcher uses
         * this to size its jitter buffer — emit a real value rather
         * than relying on the watcher's fallback default. For Opus
         * this is the codec frame duration (20 ms at 48 kHz / 960
         * samples) and matches what hang.js's encoder emits at
         * `js/publish/src/audio/encoder.ts:#runConfig`.
         */
        val jitter: Int,
    )

    @Serializable
    data class Container(
        val kind: String,
    )

    /** UTF-8 JSON bytes ready to push on the `catalog.json` moq-lite track. */
    fun encodeJsonBytes(): ByteArray = JSON.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        /**
         * Json instance dedicated to catalog encoding. We can't reuse
         * `:quartz`'s `JsonMapper` from common code without pulling
         * `:commons` (which already imports it) into `:nestsClient`'s
         * dependency closure — `:nestsClient` deliberately stays free
         * of the UI/state layer. A local instance keeps the wire-format
         * configuration (camelCase serde defaults, no pretty-printing,
         * deterministic field order) co-located with the model.
         */
        private val JSON =
            Json {
                // Default for kotlinx.serialization is `false`, but pin
                // explicitly so a future global default-flip doesn't
                // surface `"bitrate": null` on hang.js's parser.
                encodeDefaults = false
                explicitNulls = false
            }

        /**
         * Canonical Amethyst speaker catalog: a single `legacy`-container
         * Opus rendition under [audioTrackName] at 48 kHz with
         * [numberOfChannels] (1 = mono, 2 = stereo L/R interleaved),
         * matching the encoder config in
         * [com.vitorpamplona.nestsclient.audio.OpusEncoder].
         *
         * The rendition map is keyed by the moq-lite track name a
         * subscriber should subscribe to for this rendition's frames —
         * for nests audio rooms that's the same string the publisher
         * publishes audio frames on
         * (`MoqLiteNestsListener.AUDIO_TRACK`).
         */
        fun opus48k(
            audioTrackName: String,
            numberOfChannels: Int = 1,
        ): MoqLiteHangCatalog {
            require(numberOfChannels in 1..2) {
                "opus48k supports mono (1) or stereo (2) only, got $numberOfChannels"
            }
            return MoqLiteHangCatalog(
                audio =
                    Audio(
                        renditions =
                            mapOf(
                                audioTrackName to
                                    AudioRendition(
                                        codec = "opus",
                                        container = Container(kind = "legacy"),
                                        sampleRate = 48_000,
                                        numberOfChannels = numberOfChannels,
                                        jitter = OPUS_FRAME_DURATION_MS,
                                    ),
                            ),
                    ),
            )
        }

        /**
         * Memoised JSON bytes for [opus48k]. The catalog is a fixed
         * string for the whole publisher lifetime — caching avoids
         * re-running kotlinx.serialization on every
         * [com.vitorpamplona.nestsclient.MoqLiteNestsSpeaker.startBroadcasting]
         * call and every JWT-refresh hot-swap iteration in
         * [com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker].
         *
         * Keyed by `(trackName, numberOfChannels)` so adding a new shape
         * (stereo, future bitrate variants) doesn't multiply the
         * constant count. The cache is populated on first request per
         * shape and never evicted — at most a handful of entries per
         * process lifetime, all small.
         *
         * Thread-safety: the operation is idempotent — two threads
         * computing the same shape land identical bytes — so a
         * non-locking [HashMap] is acceptable in commonMain. Worst case
         * the second writer overwrites with an equal value; cache
         * readers always see at least one fully-published value
         * because the JVM's default visibility for non-volatile object
         * references is good enough for the "may compute twice"
         * tolerance we have here.
         */
        private val cachedJsonBytes: HashMap<Pair<String, Int>, ByteArray> = HashMap()

        fun opus48kJsonBytes(
            audioTrackName: String,
            numberOfChannels: Int = 1,
        ): ByteArray {
            val key = audioTrackName to numberOfChannels
            cachedJsonBytes[key]?.let { return it }
            val bytes = opus48k(audioTrackName, numberOfChannels).encodeJsonBytes()
            cachedJsonBytes[key] = bytes
            return bytes
        }

        /**
         * Opus frame duration in milliseconds — 960 samples / 48 kHz =
         * 20 ms. Matches
         * [com.vitorpamplona.nestsclient.audio.Audio.FRAME_SIZE_SAMPLES].
         * Published as the catalog `jitter` hint so hang.js's watcher
         * sizes its jitter buffer to one Opus frame, the natural
         * cadence of our encoder.
         */
        private const val OPUS_FRAME_DURATION_MS: Int = 20
    }
}

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

import kotlin.test.Test
import kotlin.test.assertEquals

class MoqLiteHangCatalogTest {
    @Test
    fun opus48kMonoEmitsCanonicalHangShape() {
        // Byte-exact assertion: `:commons`'s `RoomSpeakerCatalogTest`
        // round-trips this same string against the parser. If either
        // side drifts from the kixelated/hang wire shape, both tests
        // fail at once.
        val expected =
            "{\"audio\":{\"renditions\":{\"audio/data\":{" +
                "\"codec\":\"opus\",\"container\":{\"kind\":\"legacy\"}," +
                "\"sampleRate\":48000,\"numberOfChannels\":1,\"jitter\":20}}}}"
        val actual =
            MoqLiteHangCatalog.opus48k("audio/data").encodeJsonBytes().decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun opus48kStereoEmitsTwoChannelHangShape() {
        // Stereo broadcasts (kixelated/moq web publisher with a stereo
        // AudioContext) declare numberOfChannels = 2. Listeners pick
        // this up via the catalog and configure their decoder + sink
        // for L/R interleaved PCM.
        val expected =
            "{\"audio\":{\"renditions\":{\"audio/data\":{" +
                "\"codec\":\"opus\",\"container\":{\"kind\":\"legacy\"}," +
                "\"sampleRate\":48000,\"numberOfChannels\":2,\"jitter\":20}}}}"
        val actual =
            MoqLiteHangCatalog
                .opus48k("audio/data", numberOfChannels = 2)
                .encodeJsonBytes()
                .decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun renditionKeyMatchesCallerSuppliedTrackName() {
        val actual =
            MoqLiteHangCatalog.opus48k("custom/track").encodeJsonBytes().decodeToString()
        // The rendition map MUST be keyed on the caller-supplied track
        // name — the watcher uses this string verbatim as the
        // SUBSCRIBE.track on the audio subscription.
        assertEquals(true, actual.contains("\"custom/track\":{"))
    }

    @Test
    fun opus48kJsonBytesMemoisesPerShape() {
        // Same shape → same byte array reference (fast-path on every
        // subsequent broadcast / hot-swap iteration).
        val a = MoqLiteHangCatalog.opus48kJsonBytes("audio/data", 1)
        val b = MoqLiteHangCatalog.opus48kJsonBytes("audio/data", 1)
        assertEquals(true, a === b)

        // Different shape → different bytes; both stay cached.
        val stereo = MoqLiteHangCatalog.opus48kJsonBytes("audio/data", 2)
        assertEquals(true, a !== stereo)
        assertEquals(true, stereo === MoqLiteHangCatalog.opus48kJsonBytes("audio/data", 2))
    }
}

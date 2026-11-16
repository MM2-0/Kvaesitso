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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomSpeakerCatalogTest {
    @Test
    fun parsesKixelatedHangShape() {
        // Verbatim shape produced by the canonical kixelated/moq `hang`
        // crate (`rs/hang/src/catalog/`). Verifies that an Amethyst
        // listener picks up a standards-aligned publisher's catalog.
        val json =
            """
            {
              "audio": {
                "renditions": {
                  "audio/data": {
                    "codec": "opus",
                    "container": { "kind": "legacy" },
                    "sampleRate": 48000,
                    "numberOfChannels": 2,
                    "bitrate": 64000
                  }
                }
              }
            }
            """.trimIndent()
        val catalog = RoomSpeakerCatalog.parseOrNull(json.encodeToByteArray())
        assertNotNull(catalog)
        assertEquals(1, catalog.audio?.renditions?.size)
        val rendition = catalog.primaryAudio()
        assertNotNull(rendition)
        assertEquals("opus", rendition.codec)
        assertEquals("legacy", rendition.container?.kind)
        assertEquals(48_000, rendition.sampleRate)
        assertEquals(2, rendition.numberOfChannels)
        assertEquals(64_000, rendition.bitrate)
    }

    @Test
    fun describeFormatsHumanReadable() {
        val rendition =
            RoomSpeakerCatalog.AudioConfig(
                codec = "opus",
                sampleRate = 48_000,
                numberOfChannels = 2,
            )
        // Codec uppercased, kHz / channel count short-formed —
        // intended for a single-line tooltip in the participant
        // sheet, not a verbose codec dump.
        assertEquals("OPUS · 48kHz · 2ch", rendition.describe())
    }

    @Test
    fun describeMonoIsLabelled() {
        val rendition = RoomSpeakerCatalog.AudioConfig(codec = "opus", numberOfChannels = 1)
        assertEquals("OPUS · mono", rendition.describe())
    }

    @Test
    fun describeAllNullReturnsNull() {
        val rendition = RoomSpeakerCatalog.AudioConfig()
        // Empty catalog → caller doesn't render a "unknown · unknown"
        // line; describe() returns null so the UI can omit cleanly.
        assertNull(rendition.describe())
    }

    @Test
    fun toleratesUnknownKeys() {
        // Forward-compat: future hang catalog revisions can add
        // fields without breaking older clients. Container.kind is
        // declared as `legacy` because `primaryAudio()` filters to
        // that kind — the unknown-key tolerance we're testing here
        // is about unrecognized siblings, not about the container
        // requirement itself.
        val json =
            """{"audio":{"renditions":{"audio/data":{
            |  "codec":"opus","container":{"kind":"legacy"},
            |  "extra":"future-only"
            |}}},"video":{},"newTopLevel":true}
            """.trimMargin()
        val catalog = RoomSpeakerCatalog.parseOrNull(json.encodeToByteArray())
        assertNotNull(catalog)
        assertEquals("opus", catalog.primaryAudio()?.codec)
    }

    @Test
    fun garbageBytesReturnNull() {
        // An invalid UTF-8 sequence or non-JSON payload should yield
        // null rather than throw — the catalog channel is best-effort.
        val catalog = RoomSpeakerCatalog.parseOrNull(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))
        assertNull(catalog)
    }

    @Test
    fun emptyAudioRenditionsIsAllowed() {
        // A publisher might emit an "advertising" catalog with no
        // renditions yet (e.g. between codec switches). Accept the
        // shape and let primaryAudio() return null.
        val catalog = RoomSpeakerCatalog.parseOrNull("""{"audio":{"renditions":{}}}""".encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun primaryAudioPicksLegacyEvenWhenCmafComesFirst() {
        // A future publisher may emit CMAF-first then legacy in the
        // renditions map. We only know how to decode the legacy
        // container today (varint(ts)+opus); CMAF (MOOF/MDAT) would
        // be fed bytes-as-Opus and decode to garbage. The filter
        // skips non-legacy renditions so the chosen entry is one we
        // can actually play.
        val mixed =
            """{"audio":{"renditions":{
            |  "video/cmaf":{"codec":"opus","container":{"kind":"cmaf"},"sampleRate":48000,"numberOfChannels":1},
            |  "audio/data":{"codec":"opus","container":{"kind":"legacy"},"sampleRate":48000,"numberOfChannels":1}
            |}}}
            """.trimMargin()
        val catalog = RoomSpeakerCatalog.parseOrNull(mixed.encodeToByteArray())
        assertNotNull(catalog)
        val rendition = catalog.primaryAudio()
        assertNotNull(rendition, "expected the legacy rendition, not CMAF")
        assertEquals("legacy", rendition.container?.kind)
    }

    @Test
    fun primaryAudioReturnsNullForCmafOnlyPublisher() {
        // No legacy rendition → no decoder path. Returning null is
        // the right contract: the caller falls back to "unknown
        // codec / no audio" rather than feeding CMAF bytes to the
        // legacy decoder.
        val cmafOnly =
            """{"audio":{"renditions":{"audio/cmaf":{
            |  "codec":"opus","container":{"kind":"cmaf"},
            |  "sampleRate":48000,"numberOfChannels":1
            |}}}}
            """.trimMargin()
        val catalog = RoomSpeakerCatalog.parseOrNull(cmafOnly.encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun primaryAudioReturnsNullWhenContainerKindIsMissing() {
        // Defensive: a malformed publisher that omits `container`
        // entirely must NOT be treated as legacy by default — we'd
        // be guessing the wire shape. Same null fallback as the
        // CMAF-only case.
        val noContainer =
            """{"audio":{"renditions":{"audio/data":{
            |  "codec":"opus","sampleRate":48000,"numberOfChannels":1
            |}}}}
            """.trimMargin()
        val catalog = RoomSpeakerCatalog.parseOrNull(noContainer.encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun missingAudioReturnsNullPrimary() {
        // hang catalogs declaring only video MUST still parse without
        // throwing — primaryAudio() returns null.
        val catalog = RoomSpeakerCatalog.parseOrNull("""{}""".encodeToByteArray())
        assertNotNull(catalog)
        assertNull(catalog.primaryAudio())
    }

    @Test
    fun stripPrefixRoundTripsCanonicalCatalog() {
        // The catalog payload `MoqLiteHangCatalog.opus48k(...)` emits
        // (in `:nestsClient`) MUST round-trip through this parser — the
        // two classes target the same wire shape independently because
        // `:nestsClient` does not depend on `:commons` and vice versa.
        // `MoqLiteHangCatalog` isn't reachable from this module; keep an
        // inline literal that mirrors what the publisher emits verbatim
        // so a desync on either side trips this assertion.
        val emitted =
            "{\"audio\":{\"renditions\":{\"audio/data\":{" +
                "\"codec\":\"opus\",\"container\":{\"kind\":\"legacy\"}," +
                "\"sampleRate\":48000,\"numberOfChannels\":1,\"jitter\":20}}}}"
        val catalog = RoomSpeakerCatalog.parseOrNull(emitted.encodeToByteArray())
        assertNotNull(catalog)
        val rendition = catalog.primaryAudio()
        assertNotNull(rendition)
        assertEquals("opus", rendition.codec)
        assertEquals("legacy", rendition.container?.kind)
        assertEquals(48_000, rendition.sampleRate)
        assertEquals(1, rendition.numberOfChannels)
        // `jitter` is intentionally not asserted on `rendition` — the
        // commons-side parser drops unknown fields via the JsonMapper's
        // `ignoreUnknownKeys = true`. Adding it to `RoomSpeakerCatalog`
        // is forward work; the byte-exact match in the literal above
        // already pins the wire shape.
    }
}

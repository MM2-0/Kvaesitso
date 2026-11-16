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
package com.vitorpamplona.amethyst.service.playback.playerPool

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verbatim excerpt of a zap-stream-core LL-HLS media playlist (api-uk.zap.stream, 2026-07-26) — the
 * playlist that crashes media3 1.10.1 in `HlsMediaChunk.feedDataToExtractor`. The byte-range parts
 * are what produce the bounded chunk behind that crash.
 */
private val ZAP_STREAM_LL_PLAYLIST =
    """
    #EXTM3U
    #EXT-X-VERSION:6
    #EXT-X-PART-INF:PART-TARGET=0.49007290601730347
    #EXT-X-TARGETDURATION:2
    #EXT-X-MEDIA-SEQUENCE:191184
    #EXT-X-MAP:URI="init.mp4"
    #EXT-X-SERVER-CONTROL:PART-HOLD-BACK=1.715,CAN-BLOCK-RELOAD=YES
    #EXT-X-PROGRAM-DATE-TIME:2026-07-26T20:18:32.611Z
    #EXTINF:1.961,
    191198.m4s
    #EXT-X-PART:URI="191199.m4s",DURATION=0.5009999999892898,INDEPENDENT=YES,BYTERANGE="359712@0"
    #EXT-X-PART:URI="191199.m4s",DURATION=0.5,BYTERANGE="153946@359712"
    #EXT-X-PROGRAM-DATE-TIME:2026-07-26T20:18:34.572Z
    #EXTINF:1.96,
    191199.m4s
    """.trimIndent()

class LowLatencyHlsStripperTest {
    @Test
    fun removesEveryLowLatencyTagFromARealPlaylist() {
        val result = stripLowLatencyTags(ZAP_STREAM_LL_PLAYLIST)

        assertFalse("byte-range parts are the crash trigger", result.contains("#EXT-X-PART:"))
        assertFalse(result.contains("#EXT-X-PART-INF:"))
        assertFalse(result.contains("#EXT-X-SERVER-CONTROL:"))
        assertFalse("no BYTERANGE should survive", result.contains("BYTERANGE"))
    }

    @Test
    fun keepsSegmentAndTagOrdering() {
        val result = stripLowLatencyTags(ZAP_STREAM_LL_PLAYLIST)

        // Exact equality, so this also pins that everything a plain HLS player needs survives
        // untouched and in order: the header, MAP, PROGRAM-DATE-TIME, EXTINF and both segments.
        assertEquals(
            listOf(
                "#EXTM3U",
                "#EXT-X-VERSION:6",
                "#EXT-X-TARGETDURATION:2",
                "#EXT-X-MEDIA-SEQUENCE:191184",
                """#EXT-X-MAP:URI="init.mp4"""",
                "#EXT-X-PROGRAM-DATE-TIME:2026-07-26T20:18:32.611Z",
                "#EXTINF:1.961,",
                "191198.m4s",
                "#EXT-X-PROGRAM-DATE-TIME:2026-07-26T20:18:34.572Z",
                "#EXTINF:1.96,",
                "191199.m4s",
            ),
            result.split("\n"),
        )
    }

    @Test
    fun leavesAPlainPlaylistByteIdentical() {
        // The common case, on every playlist reload of every live stream: nothing to do.
        val plain =
            """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:4230
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:2,
            4230.m4s
            """.trimIndent()

        assertSame("unchanged playlists should not be rebuilt", plain, stripLowLatencyTags(plain))
    }

    @Test
    fun preservesTrailingNewlineAndBlankLines() {
        val input = "#EXTM3U\n#EXT-X-PART:URI=\"a.m4s\",BYTERANGE=\"1@0\"\n\n#EXTINF:2,\na.m4s\n"

        assertEquals("#EXTM3U\n\n#EXTINF:2,\na.m4s\n", stripLowLatencyTags(input))
    }

    @Test
    fun preservesCrLfLineEndings() {
        val input = "#EXTM3U\r\n#EXT-X-PART:URI=\"a.m4s\",BYTERANGE=\"1@0\"\r\n#EXTINF:2,\r\na.m4s\r\n"

        assertEquals("#EXTM3U\r\n#EXTINF:2,\r\na.m4s\r\n", stripLowLatencyTags(input))
    }

    @Test
    fun keepsDeltaPlaylistAndRenditionReportTags() {
        // EXT-X-SKIP marks legitimately omitted segments; removing it would corrupt the playlist.
        // EXT-X-RENDITION-REPORT is inert once the parts are gone.
        val input =
            """
            #EXTM3U
            #EXT-X-SKIP:SKIPPED-SEGMENTS=10
            #EXT-X-PART:URI="a.m4s",BYTERANGE="1@0"
            #EXT-X-RENDITION-REPORT:URI="../b/live.m3u8",LAST-MSN=42
            """.trimIndent()

        val result = stripLowLatencyTags(input)

        assertTrue(result.contains("#EXT-X-SKIP:SKIPPED-SEGMENTS=10"))
        assertTrue(result.contains("#EXT-X-RENDITION-REPORT:"))
        assertFalse(result.contains("#EXT-X-PART:"))
    }

    @Test
    fun stripsPreloadHint() {
        // The *other* tag that yields a byte-range chunk, so it matters as much as EXT-X-PART.
        // Synthetic rather than folded into the capture above, which is labelled verbatim and did
        // not carry a hint; shaped per RFC 8216 §4.4.5.3.
        val input =
            """
            #EXTM3U
            #EXTINF:1.96,
            191199.m4s
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="191200.m4s",BYTERANGE-START=402501
            """.trimIndent()

        val result = stripLowLatencyTags(input)

        assertFalse(result.contains("#EXT-X-PRELOAD-HINT"))
        assertFalse("no byte range should survive", result.contains("BYTERANGE-START"))
        assertEquals("#EXTM3U\n#EXTINF:1.96,\n191199.m4s", result)
    }

    @Test
    fun byteFormPreservesBomAndMultibyteWhenStripping() {
        // Written as raw bytes rather than a "﻿" literal so the fixture states the on-the-wire
        // encoding directly. 🎵 is a 4-byte sequence / surrogate pair, so it also covers the
        // decode-modify-re-encode round trip beyond the BMP.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val input =
            bom +
                (
                    "#EXTM3U\n" +
                        "#EXT-X-PART:URI=\"a.m4s\",BYTERANGE=\"1@0\"\n" +
                        "#EXTINF:2,caffè 🎵\n" +
                        "a.m4s\n"
                ).toByteArray(Charsets.UTF_8)

        val result = stripLowLatencyTags(input)

        assertArrayEquals(
            bom + "#EXTM3U\n#EXTINF:2,caffè 🎵\na.m4s\n".toByteArray(Charsets.UTF_8),
            result,
        )
    }

    @Test
    fun byteFormForwardsTheOriginalArrayWhenNothingToStrip() {
        val input = "#EXTM3U\n#EXTINF:2,\na.m4s\n".toByteArray(Charsets.UTF_8)

        // Same array, not an equal copy: the common path must not re-encode.
        assertSame(input, stripLowLatencyTags(input))
    }

    @Test
    fun doesNotMatchTagsBySubstring() {
        // EXT-X-PARTY-TIME is fictional, but the point is that the match must be anchored: a bare
        // `contains("#EXT-X-PART")` without the colon would eat unrelated tags.
        val input = "#EXTM3U\n#EXT-X-PARTY-TIME:1\n#EXT-X-PART:URI=\"a.m4s\""

        val result = stripLowLatencyTags(input)

        assertTrue(result.contains("#EXT-X-PARTY-TIME:1"))
        assertFalse(result.contains("#EXT-X-PART:"))
    }
}

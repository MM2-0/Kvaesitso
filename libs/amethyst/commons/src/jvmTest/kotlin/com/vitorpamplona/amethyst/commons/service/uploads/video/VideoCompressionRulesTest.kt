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
package com.vitorpamplona.amethyst.commons.service.uploads.video

import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VideoCompressionRulesTest {
    @Test
    fun standardIsKeyedOnShortSide() {
        assertEquals(VideoStandard.FHD_1080P, VideoResolution(1080, 1920).getStandard()) // portrait
        assertEquals(VideoStandard.HD_720P, VideoResolution(1280, 720).getStandard())
        assertEquals(VideoStandard.UNKNOWN, VideoResolution(200, 100).getStandard())
    }

    @Test
    fun tableMatchesAndroid() {
        assertEquals(CompressionRule(720, 2f, "1080p→720p, 2Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.MEDIUM, VideoStandard.FHD_1080P))
        assertEquals(CompressionRule(2160, 8f, "4K→4K, 8Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.HIGH, VideoStandard.UHD_4K))
        assertEquals(CompressionRule(480, 0.75f, "1080p→480p, 0.75Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.LOW, VideoStandard.FHD_1080P))
        assertEquals(CompressionRule(720, 2f, "Medium quality fallback, 2Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.MEDIUM, VideoStandard.UNKNOWN))
    }

    @Test
    fun everyRuledLevelCoversEveryStandard() {
        for (q in listOf(CompressorQuality.LOW, CompressorQuality.MEDIUM, CompressorQuality.HIGH)) {
            for (s in VideoStandard.entries) assertNotNull(VideoCompressionRules.ruleFor(q, s), "$q/$s")
        }
    }

    @Test
    fun videoLevelMapsImageOnlyLevels() {
        assertEquals(CompressorQuality.HIGH, CompressorQuality.VERY_HIGH.videoLevel)
        assertEquals(CompressorQuality.LOW, CompressorQuality.VERY_LOW.videoLevel)
        assertEquals(CompressorQuality.MEDIUM, CompressorQuality.MEDIUM.videoLevel)
        assertEquals(
            VideoCompressionRules.ruleFor(CompressorQuality.HIGH, VideoStandard.UHD_4K),
            VideoCompressionRules.ruleFor(CompressorQuality.VERY_HIGH, VideoStandard.UHD_4K),
        )
        assertNull(VideoCompressionRules.ruleFor(CompressorQuality.UNCOMPRESSED, VideoStandard.FHD_1080P))
    }

    @Test
    fun hasVideoRule() {
        assertEquals(listOf(false, true, true, true, false, true), CompressorQuality.entries.map { it.hasVideoRule })
    }

    @Test
    fun bitrateMultipliers() {
        val rule = CompressionRule(1080, 4f, "")
        assertEquals(4_000_000, rule.getBitrateBps(30f, useH265 = false))
        assertEquals(5_200_000, rule.getBitrateBps(60f, useH265 = false))
        assertEquals(3_000_000, rule.getBitrateBps(30f, useH265 = true))
        assertEquals(3_900_000, rule.getBitrateBps(60f, useH265 = true))
        // Broadcast 59.94 fps is 60 fps in all but name, so it earns the high-frame-rate bitrate; 50 fps does not.
        assertEquals(5_200_000, rule.getBitrateBps(59.94f, useH265 = false))
        assertEquals(4_000_000, rule.getBitrateBps(50f, useH265 = false))
    }

    @Test
    fun probeFailureConstant() {
        assertEquals(2_000_000, VideoCompressionRules.PROBE_FAILED_BITRATE_BPS)
    }
}

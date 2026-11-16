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
import com.vitorpamplona.quartz.utils.Log
import kotlin.math.roundToInt

/**
 * The resolution and bitrate rules every platform's video compressor follows, so
 * Android (MediaCodec) and desktop (ffmpeg) cannot drift apart.
 */
data class VideoInfo(
    val resolution: VideoResolution,
    val framerate: Float,
)

data class VideoResolution(
    val width: Int,
    val height: Int,
) {
    /** The source's standard, keyed on the short side so orientation does not matter. */
    fun getStandard(): VideoStandard {
        val shortSide = minOf(width, height)
        return when {
            shortSide >= 2160 -> VideoStandard.UHD_4K
            shortSide >= 1440 -> VideoStandard.QHD_1440P
            shortSide >= 1080 -> VideoStandard.FHD_1080P
            shortSide >= 720 -> VideoStandard.HD_720P
            shortSide >= 480 -> VideoStandard.SD_480P
            shortSide >= 360 -> VideoStandard.NHD_360P
            shortSide >= 240 -> VideoStandard.QVGA_240P
            else -> VideoStandard.UNKNOWN
        }
    }
}

enum class VideoStandard(
    val label: String,
) {
    UHD_4K("4K"),
    QHD_1440P("1440p"),
    FHD_1080P("1080p"),
    HD_720P("720p"),
    SD_480P("480p"),
    NHD_360P("360p"),
    QVGA_240P("240p"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = label
}

private const val MBPS_TO_BPS_MULTIPLIER = 1_000_000

/** Frame rates at or above this get the high-frame-rate bitrate; 59 admits broadcast 59.94 fps. */
private const val HIGH_FRAMERATE_FLOOR = 59f

/** The target for one (level, source standard) pair: the short side in pixels and the bitrate in Mbps. */
data class CompressionRule(
    val shortSide: Int,
    val bitrateMbps: Float,
    val description: String,
) {
    fun getBitrateBps(
        framerate: Float,
        useH265: Boolean,
    ): Int {
        // 1.3x for high-frame-rate video, 0.75x for H265. The frame-rate floor is 59 so that
        // broadcast 59.94 fps (60000/1001) counts as 60 while 50 fps does not.
        val framerateMultiplier = if (framerate >= HIGH_FRAMERATE_FLOOR) 1.3f else 1.0f
        val codecMultiplier = if (useH265) 0.75f else 1.0f
        val finalMultiplier = framerateMultiplier * codecMultiplier

        Log.d("VideoCompressionRules") { "framerate: $framerate, useH265: $useH265, Bitrate multiplier: $finalMultiplier" }

        // Rounded, not truncated: 4 Mbps × 1.3 × 0.75 is 3.9 Mbps, which float arithmetic lands 1 bps under.
        return (bitrateMbps * finalMultiplier * MBPS_TO_BPS_MULTIPLIER).roundToInt()
    }
}

object VideoCompressionRules {
    /** When the source cannot be probed at all: 2 Mbps and no resize. Not a table row. */
    const val PROBE_FAILED_BITRATE_BPS: Int = 2 * MBPS_TO_BPS_MULTIPLIER

    private val compressionRules =
        mapOf(
            CompressorQuality.LOW to
                mapOf(
                    VideoStandard.UHD_4K to CompressionRule(720, 1f, "4K→720p, 1Mbps"),
                    VideoStandard.QHD_1440P to CompressionRule(720, 1f, "1440p→720p, 1Mbps"),
                    VideoStandard.FHD_1080P to CompressionRule(480, 0.75f, "1080p→480p, 0.75Mbps"),
                    VideoStandard.HD_720P to CompressionRule(360, 0.5f, "720p→360p, 0.5Mbps"),
                    VideoStandard.SD_480P to CompressionRule(240, 0.5f, "480p→240p, 0.5Mbps"),
                    VideoStandard.NHD_360P to CompressionRule(240, 0.3f, "360p→240p, 0.3Mbps"),
                    VideoStandard.QVGA_240P to CompressionRule(180, 0.2f, "240p→180p, 0.2Mbps"),
                    VideoStandard.UNKNOWN to CompressionRule(480, 0.75f, "Low quality fallback, 0.75Mbps"),
                ),
            CompressorQuality.MEDIUM to
                mapOf(
                    VideoStandard.UHD_4K to CompressionRule(1080, 4f, "4K→1080p, 4Mbps"),
                    VideoStandard.QHD_1440P to CompressionRule(1080, 4f, "1440p→1080p, 4Mbps"),
                    VideoStandard.FHD_1080P to CompressionRule(720, 2f, "1080p→720p, 2Mbps"),
                    VideoStandard.HD_720P to CompressionRule(480, 1.5f, "720p→480p, 1.5Mbps"),
                    VideoStandard.SD_480P to CompressionRule(360, 1f, "480p→360p, 1Mbps"),
                    VideoStandard.NHD_360P to CompressionRule(240, 0.5f, "360p→240p, 0.5Mbps"),
                    VideoStandard.QVGA_240P to CompressionRule(180, 0.3f, "240p→180p, 0.3Mbps"),
                    VideoStandard.UNKNOWN to CompressionRule(720, 2f, "Medium quality fallback, 2Mbps"),
                ),
            CompressorQuality.HIGH to
                mapOf(
                    VideoStandard.UHD_4K to CompressionRule(2160, 8f, "4K→4K, 8Mbps"),
                    VideoStandard.QHD_1440P to CompressionRule(1080, 8f, "1440p→1080p, 8Mbps"),
                    VideoStandard.FHD_1080P to CompressionRule(1080, 4f, "1080p→1080p, 4Mbps"),
                    VideoStandard.HD_720P to CompressionRule(720, 2f, "720p→720p, 2Mbps"),
                    VideoStandard.SD_480P to CompressionRule(480, 1.5f, "480p→480p, 1.5Mbps"),
                    VideoStandard.NHD_360P to CompressionRule(360, 1f, "360p→360p, 1Mbps"),
                    VideoStandard.QVGA_240P to CompressionRule(240, 0.5f, "240p→240p, 0.5Mbps"),
                    VideoStandard.UNKNOWN to CompressionRule(1080, 4f, "High quality fallback, 4Mbps"),
                ),
        )

    /**
     * The rule for [quality] (through [CompressorQuality.videoLevel], so VERY_HIGH and VERY_LOW
     * land on HIGH and LOW) and [standard]. Null only for [CompressorQuality.UNCOMPRESSED].
     */
    fun ruleFor(
        quality: CompressorQuality,
        standard: VideoStandard,
    ): CompressionRule? = compressionRules[quality.videoLevel]?.get(standard)
}

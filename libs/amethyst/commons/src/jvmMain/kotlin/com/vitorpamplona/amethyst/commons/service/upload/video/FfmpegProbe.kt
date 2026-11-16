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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.arrayOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.intOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/** What `ffprobe` tells about a video: the table key, the duration for progress and the HDR flag. */
data class ProbeResult(
    val info: VideoInfo,
    val durationSeconds: Double?,
    val isHdr: Boolean,
)

/** Parses the JSON of the `ffprobe` call in [FfmpegProbe]. */
object FfprobeOutputParser {
    private val hdrTransfers = setOf("smpte2084", "arib-std-b67")

    /** Null when the JSON is not JSON or has no video stream. */
    fun parse(json: String): ProbeResult? {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
        val stream = root.arrayOrNull("streams")?.firstOrNull() as? JsonObject ?: return null
        // The display width: an anamorphic stream stores its frames narrower or wider than it shows.
        val width = displayWidth(stream.intOrNull("width") ?: 0, stream.stringOrNull("sample_aspect_ratio"))
        val height = stream.intOrNull("height") ?: 0
        if (width <= 0 || height <= 0) return null
        val framerate = parseFramerate(stream.stringOrNull("avg_frame_rate") ?: "")
        val duration = root.objOrNull("format")?.stringOrNull("duration")?.toDoubleOrNull()
        val isHdr = stream.stringOrNull("color_transfer") in hdrTransfers
        return ProbeResult(VideoInfo(VideoResolution(width, height), framerate), duration, isHdr)
    }

    /** [width] times the `num:den` [sampleAspectRatio]; as is when that is missing, `N/A` or `0:1` (unknown). */
    private fun displayWidth(
        width: Int,
        sampleAspectRatio: String?,
    ): Int {
        val (num, den) = sampleAspectRatio?.split(":")?.takeIf { it.size == 2 }?.map { it.toIntOrNull() ?: 0 } ?: return width
        if (num <= 0 || den <= 0) return width
        return (width.toLong() * num / den).toInt()
    }

    /** `num/den` → num / den; 0 when the denominator is 0 or the text is not a ratio. */
    private fun parseFramerate(text: String): Float {
        val (num, den) = text.split("/").takeIf { it.size == 2 } ?: return text.toFloatOrNull() ?: 0f
        val d = den.toFloatOrNull() ?: return 0f
        if (d == 0f) return 0f
        return (num.toFloatOrNull() ?: return 0f) / d
    }
}

/**
 * Runs `ffprobe` on a file through the shared process runner, so a hung probe is killed by the
 * stall watchdog, dies with the JVM and follows cancellation. Null when [ffprobe] is null, the
 * process fails or stalls, or the output has no video stream; the caller then uses the
 * probe-failure fallback (2 Mbps, no resize).
 */
class FfmpegProbe(
    private val ffprobe: String?,
    private val stallTimeoutMs: Long = 10_000,
) {
    suspend fun probe(file: File): ProbeResult? {
        val binary = ffprobe ?: return null
        val command =
            listOf(
                binary,
                "-v",
                "error",
                "-select_streams",
                "V:0",
                "-show_entries",
                "stream=width,height,sample_aspect_ratio,avg_frame_rate,color_transfer",
                "-show_entries",
                "format=duration",
                "-of",
                "json",
                file.absolutePath,
            )
        val json = FfmpegProcesses.captureStdout(command, stallTimeoutMs, TAG, "ffprobe on ${file.name}") ?: return null
        return FfprobeOutputParser.parse(json)
    }

    private companion object {
        const val TAG = "FfmpegProbe"
    }
}

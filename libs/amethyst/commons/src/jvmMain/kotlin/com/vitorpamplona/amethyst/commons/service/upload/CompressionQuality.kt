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
package com.vitorpamplona.amethyst.commons.service.upload

/** What [ImageReencoder] needs: the longer edge's limit in pixels and the JPEG quality in [0.0, 1.0]. */
interface ImageCompressionTarget {
    val maxDim: Int
    val jpegQuality: Float
}

/** A target given directly, for the shared composer's quality steps. */
data class ImageSizeTarget(
    override val maxDim: Int,
    override val jpegQuality: Float,
) : ImageCompressionTarget

/**
 * Image compression quality presets. JPEG quality values are tuned for
 * 2026 display densities — the 2014-era Android values (q=0.40/0.50/0.80)
 * produce visible blocking on modern displays.
 *
 * @property displayName Human-readable label for settings UI and the
 *   per-post override chip.
 * @property maxDim Maximum width OR height in pixels for the encoded
 *   output. The image keeps its aspect ratio; the longer edge clamps to
 *   this value. ImageReencoder enforces "never upscale": if both source
 *   dimensions are already within this box, the source dims are kept
 *   and only the JPEG re-encode runs.
 * @property jpegQuality JPEG quality factor in [0.0, 1.0] passed to
 *   `javax.imageio.ImageWriteParam.setCompressionQuality(...)`.
 */
enum class CompressionQuality(
    val displayName: String,
    override val maxDim: Int,
    override val jpegQuality: Float,
    /**
     * Short subtitle that explains what the preset gives you. Surfaced
     * under the segmented row in settings and under each row in the
     * per-post override dropdown.
     */
    val summary: String,
) : ImageCompressionTarget {
    LOW("Low", 640, 0.65f, "640 px · smallest files, best for slow uplinks"),
    MEDIUM("Medium", 640, 0.75f, "640 px · balanced size and quality"),
    HIGH("High", 640, 0.85f, "640 px · visually lossless on phones"),
    DESKTOP_HIGH("Desktop High", 1920, 0.90f, "1920 px · best for desktop displays"),
    ;

    /** Compact label suitable for a chip: "Medium (640 px)". */
    val chipLabel: String get() = "$displayName ($maxDim px)"

    companion object {
        /** Default for first-install. */
        val DEFAULT: CompressionQuality = DESKTOP_HIGH

        /** Round-trip from a stored preference value. Falls back to [DEFAULT]. */
        fun fromName(name: String?): CompressionQuality = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

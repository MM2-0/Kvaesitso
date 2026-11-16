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
package com.vitorpamplona.amethyst.commons.service.uploads

/**
 * Which video controls an upload dialog shows for [media] on a platform whose uploader reports
 * [support], with the quality slider at [sliderPosition]. One home for the rule, so the post
 * composer, the media post and the chat file dialog cannot drift.
 */
class VideoUploadControls(
    media: MultiOrchestrator?,
    val support: VideoCompressionSupport,
    sliderPosition: Int,
) {
    val hasVideo: Boolean = media?.hasVideo() == true

    /** The platform will transcode the selected video: the slider is in video mode. */
    val compressesVideo: Boolean = hasVideo && support == VideoCompressionSupport.Supported

    /** A video is selected on a desktop without ffmpeg: show the install hint instead of the controls. */
    val needsFfmpeg: Boolean = hasVideo && support == VideoCompressionSupport.NeedsFfmpeg

    /**
     * The video will really be re-encoded, which strips its metadata: the H.265 switch applies. A dialog
     * may hide the strip toggle then, but the toggle is not moot: its value must still reach the
     * uploader, because an encode that fails or comes out larger uploads the original, which only the
     * stripper cleans.
     */
    val videoWillBeCompressed: Boolean = compressesVideo && sliderPosition != CompressorQuality.uncompressedSliderPosition

    /** Something the slider governs is selected: a compressible image, or a video the platform compresses. */
    val showsSlider: Boolean = media?.hasCompressibleImage() == true || compressesVideo

    val showsGifSwitch: Boolean = media?.hasGif() == true && support == VideoCompressionSupport.Supported
}

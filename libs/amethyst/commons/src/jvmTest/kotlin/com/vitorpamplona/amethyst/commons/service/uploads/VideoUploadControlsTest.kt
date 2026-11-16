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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoUploadControlsTest {
    private val video = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/a.mp4"), "video/mp4")))
    private val photoAndVideo = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/a.jpg"), "image/jpeg"), SelectedMedia(StringMediaUri("/tmp/a.mp4"), "video/mp4")))
    private val gif = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/a.gif"), "image/gif")))
    private val high = CompressorQuality.sliderSteps.indexOf(CompressorQuality.HIGH)

    @Test
    fun supportedVideoTurnsEverythingOn() {
        val c = VideoUploadControls(video, VideoCompressionSupport.Supported, high)
        assertTrue(c.compressesVideo)
        assertTrue(c.videoWillBeCompressed)
        assertTrue(c.showsSlider)
        assertFalse(c.needsFfmpeg)
    }

    @Test
    fun uncompressedKeepsTheStripToggle() {
        assertFalse(VideoUploadControls(video, VideoCompressionSupport.Supported, CompressorQuality.uncompressedSliderPosition).videoWillBeCompressed)
    }

    @Test
    fun needsFfmpegHidesTheVideoControlsAndShowsTheHint() {
        val c = VideoUploadControls(video, VideoCompressionSupport.NeedsFfmpeg, high)
        assertTrue(c.needsFfmpeg)
        assertFalse(c.compressesVideo)
        assertFalse(c.videoWillBeCompressed)
        assertFalse(c.showsSlider) // the video alone gives the slider nothing to govern
        assertTrue(VideoUploadControls(photoAndVideo, VideoCompressionSupport.NeedsFfmpeg, high).showsSlider) // the photo does
    }

    @Test
    fun unsupportedShowsNeitherControlsNorHint() {
        val c = VideoUploadControls(video, VideoCompressionSupport.Unsupported, high)
        assertFalse(c.needsFfmpeg)
        assertFalse(c.compressesVideo)
        assertFalse(c.showsSlider)
    }

    @Test
    fun gifSwitchOnlyWhereTheGifCanBeConverted() {
        assertTrue(VideoUploadControls(gif, VideoCompressionSupport.Supported, high).showsGifSwitch)
        assertFalse(VideoUploadControls(gif, VideoCompressionSupport.NeedsFfmpeg, high).showsGifSwitch)
        assertFalse(VideoUploadControls(video, VideoCompressionSupport.Supported, high).showsGifSwitch)
    }
}

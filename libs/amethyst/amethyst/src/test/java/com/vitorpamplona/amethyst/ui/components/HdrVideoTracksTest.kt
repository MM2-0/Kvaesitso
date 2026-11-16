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
package com.vitorpamplona.amethyst.ui.components

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrVideoTracksTest {
    private fun video(colorTransfer: Int) =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(colorTransfer).build())
            .build()

    private fun audio() = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()

    private fun group(
        format: Format,
        selected: Boolean,
    ) = Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))

    @Test
    fun aSelectedHlgTrackIsHdr() {
        val tracks = Tracks(listOf(group(video(C.COLOR_TRANSFER_HLG), selected = true), group(audio(), selected = true)))
        assertTrue(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun aSelectedPqTrackIsHdr() {
        val tracks = Tracks(listOf(group(video(C.COLOR_TRANSFER_ST2084), selected = true)))
        assertTrue(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun anSdrTrackIsNot() {
        val tracks = Tracks(listOf(group(video(C.COLOR_TRANSFER_SDR), selected = true)))
        assertFalse(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun aVideoWithoutColorInfoIsNot() {
        val tracks = Tracks(listOf(group(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build(), selected = true)))
        assertFalse(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun audioOnlyIsNot() {
        val tracks = Tracks(listOf(group(audio(), selected = true)))
        assertFalse(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun anUnselectedHdrTrackDoesNotCount() {
        // An adaptive stream can carry an HDR rendition the selector did not pick.
        val tracks = Tracks(listOf(group(video(C.COLOR_TRANSFER_HLG), selected = false), group(video(C.COLOR_TRANSFER_SDR), selected = true)))
        assertFalse(tracks.hasSelectedHdrVideo())
    }

    @Test
    fun noTracksIsNot() {
        assertFalse(Tracks.EMPTY.hasSelectedHdrVideo())
    }
}

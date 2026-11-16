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

import android.content.pm.ActivityInfo
import android.graphics.Gainmap
import android.os.Build
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HdrWindowModeTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun requireGainmaps() {
        assumeTrue("Gain maps need API 34+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    }

    private fun hdrImage() = createBitmap(8, 8).apply { gainmap = Gainmap(createBitmap(2, 2)) }.asImage()

    private fun sdrImage() = createBitmap(8, 8).asImage()

    private fun videoTracks(colorTransfer: Int): Tracks {
        val format =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setColorInfo(ColorInfo.Builder().setColorTransfer(colorTransfer).build())
                .build()
        return Tracks(listOf(Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
    }

    /** A real [Player] whose rendered tracks the test sets, as a quality switch or next item would. */
    private class TracksPlayer(
        private var tracks: Tracks,
    ) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State =
            State
                .Builder()
                .setAvailableCommands(
                    Player.Commands
                        .Builder()
                        .add(Player.COMMAND_GET_TRACKS)
                        .build(),
                ).setPlaylist(listOf(MediaItemData.Builder("item").setTracks(tracks).build()))
                .build()

        fun render(newTracks: Tracks) {
            tracks = newTracks
            invalidateState()
        }
    }

    @Test
    fun theWindowFollowsThePlayersVideoTrack() {
        var window: Window? = null
        val player = TracksPlayer(videoTracks(C.COLOR_TRANSFER_HLG))

        rule.setContent {
            window = getActivityWindow()
            RequestHdrFor(player)
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)

        rule.runOnUiThread { player.render(videoTracks(C.COLOR_TRANSFER_SDR)) }
        rule.waitForIdle()
        assertEquals("the stream switched to an SDR rendition", ActivityInfo.COLOR_MODE_DEFAULT, window!!.colorMode)

        rule.runOnUiThread { player.render(videoTracks(C.COLOR_TRANSFER_ST2084)) }
        rule.waitForIdle()
        assertEquals("and back to HDR", ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)
    }

    @Test
    fun anSdrPlayerLeavesTheWindowAlone() {
        var window: Window? = null
        val player = TracksPlayer(videoTracks(C.COLOR_TRANSFER_SDR))

        rule.setContent {
            window = getActivityWindow()
            RequestHdrFor(player)
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, window!!.colorMode)
    }

    @Test
    fun theWindowRevertsWhenTheHdrPlayerLeavesComposition() {
        var window: Window? = null
        var show by mutableStateOf(true)
        val player = TracksPlayer(videoTracks(C.COLOR_TRANSFER_HLG))

        rule.setContent {
            window = getActivityWindow()
            if (show) RequestHdrFor(player)
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)

        show = false
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, window!!.colorMode)
    }

    @Test
    fun theWindowIsHdrOnlyWhileAGainmapImageIsShown() {
        var window: Window? = null
        var showFirst by mutableStateOf(true)
        var showSecond by mutableStateOf(true)
        val image = hdrImage()

        rule.setContent {
            window = getActivityWindow()
            if (showFirst) RequestHdrFor(image, fullscreen = false)
            if (showSecond) RequestHdrFor(image, fullscreen = false)
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)

        showFirst = false
        rule.waitForIdle()
        assertEquals("one image still wants HDR", ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)

        showSecond = false
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, window!!.colorMode)
    }

    @Test
    fun anSdrImageLeavesTheWindowAlone() {
        var window: Window? = null
        rule.setContent {
            window = getActivityWindow()
            RequestHdrFor(sdrImage(), fullscreen = false)
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, window!!.colorMode)
    }

    @Test
    fun insideADialogTheDialogWindowGoesHdr() {
        var activityWindow: Window? = null
        var dialogWindow: Window? = null
        val image = hdrImage()

        rule.setContent {
            activityWindow = getActivityWindow()
            Dialog(onDismissRequest = {}) {
                dialogWindow = getDialogWindow()
                RequestHdrFor(image, fullscreen = true)
            }
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.COLOR_MODE_HDR, dialogWindow!!.colorMode)
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, activityWindow!!.colorMode)
    }

    @Test
    fun moreHdrImagesDoNotResendTheWindowAttributes() {
        var window: Window? = null
        var count by mutableStateOf(1)
        val image = hdrImage()
        var attributeChanges = 0

        rule.setContent {
            window = getActivityWindow()
            repeat(count) { RequestHdrFor(image, fullscreen = false) }
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            val original = window!!.callback
            window!!.callback =
                object : Window.Callback by original {
                    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
                        attributeChanges++
                        original.onWindowAttributesChanged(attrs)
                    }
                }
        }

        // Cards scrolling in and out while HDR is already on: nothing for the window to change.
        count = 4
        rule.waitForIdle()
        count = 2
        rule.waitForIdle()

        assertEquals(0, attributeChanges)
        assertEquals(ActivityInfo.COLOR_MODE_HDR, window!!.colorMode)
    }
}

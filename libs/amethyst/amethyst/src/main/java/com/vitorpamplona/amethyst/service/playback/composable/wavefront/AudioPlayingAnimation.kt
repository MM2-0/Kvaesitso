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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.amethyst.commons.audio.AudioVisualizer
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.delayedByFrames
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.rememberTracksState
import com.vitorpamplona.amethyst.service.playback.playerPool.PcmTapRegistry
import kotlinx.coroutines.flow.emptyFlow

fun Tracks.isAudio() = groups.isNotEmpty() && groups.none { it.type == C.TRACK_TYPE_VIDEO }

/**
 * Observes whether the controller's current tracks are audio-only, updating live via the player's
 * track-change listener. Shared by [AudioPlayingAnimation] (to decide what to draw) and the player
 * container (to decide whether to size the player as a square).
 */
@Composable
fun rememberIsAudioTrack(controller: Player): State<Boolean> = rememberTracksState(controller) { it.isAudio() }

// Output-latency compensation. The spectrum is tapped upstream of the AudioTrack output buffer (and,
// over Bluetooth, the codec/transmission lag downstream of it), so the visual leads the speaker and
// must be delayed to match. Each hop is one 1024-sample FFT frame (~23 ms at 44.1 kHz). Tuned on a
// Pixel 9a against the 120-BPM beat of the synthetic demo clip (1 beat = 500 ms): wired/speaker wants
// ~20 hops, Bluetooth ~27 hops (the extra ~7 hops / ~160 ms is the BT codec latency).
//
// DEVICE/CODEC-SPECIFIC tuned constants. Android exposes no reliable output latency, and runtime
// auto-detection from player.currentPosition was tried and rejected (~3.4x off — it measures decode
// buffer depth, not the perceptual sync point). [rememberIsBluetoothOutput] only switches between
// these two presets by route; the BT figure is a per-codec average, so it may be off on other gear.
private const val FEED_VISUALIZER_DELAY_FRAMES = 20
private const val BT_VISUALIZER_DELAY_FRAMES = 27

@Composable
fun AudioPlayingAnimation(
    controllerState: MediaControllerState,
    waveform: WaveformData?,
    mediaId: String,
    style: VisualizerStyle,
    isAudio: Boolean,
    modifier: Modifier = Modifier,
    hasBlurhash: Boolean = false,
) {
    if (!isAudio) return

    // Dim any blurhash/cover backdrop behind whatever we draw, for contrast and consistency across
    // every visible style. OFF draws nothing, so the cover still shows cleanly there.
    val drawModifier = if (hasBlurhash) modifier.background(Color.Black.copy(alpha = 0.45f)) else modifier

    if (waveform != null) {
        // NIP-A0 voice notes etc. that ship a precomputed waveform keep their seek bar.
        Waveform(waveform, controllerState, drawModifier)
        return
    }

    when (style) {
        // CLASSIC keeps its compact fixed-height wave (it does not stretch to fill the square).
        VisualizerStyle.CLASSIC -> FakeWaveformAnimation(mediaControllerState = controllerState, modifier = drawModifier)
        VisualizerStyle.STATIC -> {
            // StaticRenderer ignores the flow and shows a frozen frame.
            val empty = remember { emptyFlow<Spectrum>() }
            AudioVisualizer(style = VisualizerStyle.STATIC, spectrum = empty, modifier = drawModifier.audioVisualizerHeight())
        }
        VisualizerStyle.OFF -> Unit
        VisualizerStyle.BARS,
        VisualizerStyle.WAVES,
        VisualizerStyle.RADIAL,
        VisualizerStyle.AURORA,
        -> {
            val bluetooth by rememberIsBluetoothOutput()
            val delayFrames = if (bluetooth) BT_VISUALIZER_DELAY_FRAMES else FEED_VISUALIZER_DELAY_FRAMES
            val spectrum = remember(mediaId, delayFrames) { PcmTapRegistry.spectrumFor(mediaId).delayedByFrames(delayFrames) }
            AudioVisualizer(style = style, spectrum = spectrum, modifier = drawModifier.audioVisualizerHeight())
        }
    }
}

/**
 * Tracks whether audio is currently routed to a Bluetooth output, updating live as devices connect or
 * disconnect. Bluetooth adds codec/transmission latency downstream of AudioTrack, so the visualizer
 * needs a larger delay there. Keys off CONNECTED A2DP/LE output devices — a good proxy since A2DP
 * captures media playback, and Android exposes no reliable "active media route" before API 31.
 */
@Composable
private fun rememberIsBluetoothOutput(): State<Boolean> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val isBluetooth = remember { mutableStateOf(audioManager.isBluetoothOutput()) }
    DisposableEffect(audioManager) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    isBluetooth.value = audioManager.isBluetoothOutput()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    isBluetooth.value = audioManager.isBluetoothOutput()
                }
            }
        audioManager?.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager?.unregisterAudioDeviceCallback(callback) }
    }
    return isBluetooth
}

// The BLE device types are API 31 constants; an older device simply never reports them.
@Suppress("InlinedApi")
private fun AudioManager?.isBluetoothOutput(): Boolean {
    if (this == null) return false
    return getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
}

/**
 * Fills the available height only when the parent gives a bounded height clearly larger than
 * [fallback] (the full-screen media dialog); otherwise uses the fixed [fallback] strip. This avoids
 * filling a small bounded feed cell and avoids collapsing to zero under the feed's unbounded lazy
 * height. Always fills width.
 */
private fun Modifier.audioVisualizerHeight(fallback: Dp = 72.dp): Modifier =
    fillMaxWidth().layout { measurable, constraints ->
        val fallbackPx = fallback.roundToPx()
        val targetHeight =
            if (constraints.hasBoundedHeight) {
                // Fill a clearly-large cell (full-screen dialog); otherwise use the fallback strip but
                // never exceed the cell, so a small bounded cell can't overflow onto its neighbors.
                if (constraints.maxHeight >= fallbackPx * 2) constraints.maxHeight else minOf(fallbackPx, constraints.maxHeight)
            } else {
                // Unbounded (lazy feed): the fallback strip avoids collapsing to zero.
                fallbackPx
            }
        val placeable = measurable.measure(constraints.copy(minHeight = targetHeight, maxHeight = targetHeight))
        layout(placeable.width, targetHeight) { placeable.placeRelative(0, 0) }
    }

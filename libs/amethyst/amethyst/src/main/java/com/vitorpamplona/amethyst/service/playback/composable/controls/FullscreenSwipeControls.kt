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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.content.ContentResolver
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.playback.controls.MuteAction
import com.vitorpamplona.amethyst.commons.ui.playback.controls.computeLevel
import com.vitorpamplona.amethyst.commons.ui.playback.controls.levelToVolumeIndex
import com.vitorpamplona.amethyst.commons.ui.playback.controls.muteActionFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

enum class SwipeAxis { Brightness, Volume }

private const val BRIGHTNESS_FLOOR = 0.01f
private const val AUTO_HIDE_MILLIS = 800L

/**
 * Holds the live state for the fullscreen brightness/volume swipe. A single instance is remembered
 * by RenderVideoPlayer and shared between the drag [Modifier] and the [FullscreenSwipeLevelIndicator]
 * overlay. All device side-effects (AudioManager / window brightness) are applied here.
 */
class FullscreenSwipeControlsState {
    var axis by mutableStateOf<SwipeAxis?>(null)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var visible by mutableStateOf(false)
        private set

    // Bumped on every drag event and on drag end; the overlay keys its auto-hide timer on this so
    // the timer restarts while dragging and fires AUTO_HIDE_MILLIS after the last event.
    var interactionId by mutableIntStateOf(0)
        private set

    private var dragStartLevel = 0f
    private var accumulatedDragPx = 0f

    // Max volume is device-invariant; cache it at drag start so the per-frame drag path doesn't
    // re-query AudioManager on every pointer event.
    private var maxVolume = 0

    // Last discrete value actually pushed to the device this drag. Continuous finger movement maps
    // to the same volume index / brightness step across several frames; skipping unchanged writes
    // avoids redundant AudioManager calls and window-attribute relayouts with no visible effect.
    private var lastVolumeIndex = -1
    private var lastBrightnessStep = -1

    fun startDrag(
        axis: SwipeAxis,
        audioManager: AudioManager?,
        window: Window?,
        resolver: ContentResolver,
    ) {
        this.axis = axis
        accumulatedDragPx = 0f
        when (axis) {
            SwipeAxis.Volume -> {
                maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                lastVolumeIndex = -1
                dragStartLevel = currentVolumeFraction(audioManager, maxVolume)
            }
            SwipeAxis.Brightness -> {
                lastBrightnessStep = -1
                dragStartLevel = currentBrightnessFraction(window, resolver)
            }
        }
        level = dragStartLevel
        visible = true
        interactionId++
    }

    fun onDrag(
        dragAmountPx: Float,
        heightPx: Float,
        audioManager: AudioManager?,
        window: Window?,
        isMuted: () -> Boolean,
        setMuted: (Boolean) -> Unit,
    ) {
        accumulatedDragPx += dragAmountPx
        level = computeLevel(dragStartLevel, accumulatedDragPx, heightPx)
        when (axis) {
            SwipeAxis.Volume -> {
                audioManager?.let { applyVolumeIfChanged(it) }
                applyMuteSync(isMuted, setMuted)
            }
            SwipeAxis.Brightness -> window?.let { applyBrightnessIfChanged(it) }
            null -> Unit
        }
        interactionId++
    }

    // Directional mute sync: dragging up unmutes a muted video; reaching zero mutes it. Kept outside
    // the AudioManager branch so per-video mute still tracks the gesture even if device volume is
    // unavailable.
    private fun applyMuteSync(
        isMuted: () -> Boolean,
        setMuted: (Boolean) -> Unit,
    ) {
        when (muteActionFor(level, movedUp = accumulatedDragPx < 0f, isMuted = isMuted())) {
            MuteAction.Mute -> setMuted(true)
            MuteAction.Unmute -> setMuted(false)
            MuteAction.None -> Unit
        }
    }

    private fun applyVolumeIfChanged(audioManager: AudioManager) {
        if (maxVolume <= 0) return
        val index = levelToVolumeIndex(level, maxVolume)
        if (index == lastVolumeIndex) return
        lastVolumeIndex = index
        // Flag 0 = no system volume UI; we draw our own ring.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    private fun applyBrightnessIfChanged(window: Window) {
        val target = level.coerceIn(BRIGHTNESS_FLOOR, 1f)
        // Quantize to the panel's 0..255 range; sub-step movement is imperceptible and would
        // otherwise reassign window.attributes (a relayout) every frame for no visible change.
        val step = (target * 255f).roundToInt()
        if (step == lastBrightnessStep) return
        lastBrightnessStep = step
        applyBrightness(window, target)
    }

    fun endDrag() {
        interactionId++
    }

    fun hide() {
        visible = false
    }

    /**
     * Clears any brightness override this controller applied, restoring the window to the system
     * brightness. Call from the fullscreen player's onDispose so leaving fullscreen never leaves
     * the screen dimmed.
     */
    fun releaseBrightness(window: Window?) {
        window?.let { releaseBrightnessOverride(it) }
    }
}

private fun currentVolumeFraction(
    audioManager: AudioManager?,
    max: Int,
): Float {
    audioManager ?: return 0f
    if (max <= 0) return 0f
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun currentBrightnessFraction(
    window: Window?,
    resolver: ContentResolver,
): Float {
    val override = window?.attributes?.screenBrightness ?: -1f
    if (override in 0f..1f) return override
    val system =
        try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
    return (system / 255f).coerceIn(0f, 1f)
}

private fun applyBrightness(
    window: Window,
    brightness: Float,
) {
    val params = window.attributes
    params.screenBrightness = brightness
    window.attributes = params
}

private fun releaseBrightnessOverride(window: Window) {
    val params = window.attributes
    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

/**
 * Vertical-drag handler for the fullscreen video surface. Left half of the surface controls
 * brightness, right half controls volume. Must be a separate [pointerInput] from the existing
 * tap/double-tap handler so taps still work.
 */
fun Modifier.fullscreenSwipeControls(
    state: FullscreenSwipeControlsState,
    audioManager: AudioManager?,
    window: Window?,
    resolver: ContentResolver,
    isMuted: () -> Boolean,
    setMuted: (Boolean) -> Unit,
): Modifier =
    // isMuted/setMuted are intentionally not pointerInput keys: they change identity every
    // recomposition but read live state, so adding them would restart the gesture for no reason.
    pointerInput(state, audioManager, window, resolver) {
        detectVerticalDragGestures(
            onDragStart = { offset ->
                val axis = if (offset.x < size.width / 2f) SwipeAxis.Brightness else SwipeAxis.Volume
                state.startDrag(axis, audioManager, window, resolver)
            },
            onVerticalDrag = { _, dragAmount ->
                state.onDrag(dragAmount, size.height.toFloat(), audioManager, window, isMuted, setMuted)
            },
            onDragEnd = { state.endDrag() },
            onDragCancel = { state.endDrag() },
        )
    }

/** Centered ring + glyph that appears while swiping and fades out shortly after the drag ends. */
@Composable
fun BoxScope.FullscreenSwipeLevelIndicator(
    state: FullscreenSwipeControlsState,
    isMuted: () -> Boolean,
) {
    // Launch once on the stable state and watch interactionId via a snapshotFlow instead of keying
    // the effect on it: collectLatest restarts the auto-hide delay on each drag event, and reading
    // interactionId here (not as a composition key) avoids re-keying the effect every frame. The
    // indicator still recomposes per frame to redraw the arc as level changes — fine for a transient
    // drag overlay.
    LaunchedEffect(state) {
        snapshotFlow { state.interactionId }
            .collectLatest {
                if (state.visible) {
                    delay(AUTO_HIDE_MILLIS)
                    state.hide()
                }
            }
    }

    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, label = "swipeIndicatorAlpha")
    if (alpha <= 0f) return

    val axis = state.axis ?: return
    val level = state.level
    val ringColor = MaterialTheme.colorScheme.onBackground
    val trackColor = ringColor.copy(alpha = 0.25f)
    val backdrop = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)

    Box(
        modifier =
            Modifier
                .align(Alignment.Center)
                .size(110.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * level.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }

        val symbol =
            when (axis) {
                SwipeAxis.Brightness -> MaterialSymbols.BrightnessMedium
                SwipeAxis.Volume ->
                    if (isMuted() || level <= 0f) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp
            }
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = ringColor,
            modifier = Modifier.size(36.dp),
        )
    }
}

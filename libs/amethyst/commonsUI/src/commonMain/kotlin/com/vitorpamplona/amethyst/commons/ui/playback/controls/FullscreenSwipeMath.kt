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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import kotlin.math.roundToInt

/**
 * Maps a vertical drag to a 0..1 level. Dragging up (negative accumulated pixels) increases the
 * level; dragging down decreases it. A drag spanning the full element height covers the entire
 * 0..1 range. The result is clamped to 0..1.
 */
fun computeLevel(
    startLevel: Float,
    accumulatedDragPx: Float,
    heightPx: Float,
): Float {
    if (heightPx <= 0f) return startLevel.coerceIn(0f, 1f)
    return (startLevel - accumulatedDragPx / heightPx).coerceIn(0f, 1f)
}

/** Clamps [level] to 0..1, then rounds it to a discrete stream-volume index in 0..max. Returns 0 when max <= 0. */
fun levelToVolumeIndex(
    level: Float,
    max: Int,
): Int {
    if (max <= 0) return 0
    return (level.coerceIn(0f, 1f) * max).roundToInt()
}

/** The mute change a volume swipe should trigger on the per-video player. */
enum class MuteAction { Mute, Unmute, None }

/**
 * Directional mute sync for the volume swipe.
 *
 * - Reaching zero mutes the video (no-op if already muted).
 * - Dragging the finger up ([movedUp], i.e. net upward from where the drag started) unmutes a muted
 *   video — so a muted video pinned at max device volume still unmutes even though [level] can't rise.
 * - A downward swipe that stays above zero leaves the mute state untouched.
 *
 * [movedUp] is the drag direction, not a level comparison, so the clamp at level 1.0 doesn't swallow
 * the unmute intent.
 */
fun muteActionFor(
    level: Float,
    movedUp: Boolean,
    isMuted: Boolean,
): MuteAction =
    when {
        level <= 0f -> if (isMuted) MuteAction.None else MuteAction.Mute
        isMuted && movedUp -> MuteAction.Unmute
        else -> MuteAction.None
    }

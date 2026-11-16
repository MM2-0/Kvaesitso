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
package com.vitorpamplona.amethyst.commons.ui.layouts

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalView

/** Posts a moved copy of the mouse-wheel MotionEvent to the Compose view it came from. */
@Composable
internal actual fun rememberWheelRedispatcher(): (event: PointerEvent, dxPx: Float) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { event, dxPx ->
            val motion = event.motionEvent
            if (motion != null && motion.actionMasked == MotionEvent.ACTION_SCROLL) {
                // Copied now: Android recycles the original once this dispatch returns.
                val moved = MotionEvent.obtain(motion).apply { offsetLocation(dxPx, 0f) }
                view.post {
                    view.dispatchGenericMotionEvent(moved)
                    moved.recycle()
                }
            }
        }
    }
}

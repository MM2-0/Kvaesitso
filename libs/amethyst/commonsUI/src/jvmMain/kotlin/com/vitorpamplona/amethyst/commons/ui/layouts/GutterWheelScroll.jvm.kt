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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/** Posts a copy of the AWT wheel event, after the current one is handled, to the same component. */
@Composable
internal actual fun rememberWheelRedispatcher(): (event: PointerEvent, dxPx: Float) -> Unit =
    remember {
        { event, dxPx ->
            val wheel = event.awtEventOrNull as? MouseWheelEvent
            val target = wheel?.component
            if (wheel != null && target != null) {
                // AWT measures in points; its scale is pixels per point. Not Compose's density, which
                // a desktop app may raise past AWT's to draw at a scale AWT can't (fractional on X11).
                val scale = target.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
                val dx = (dxPx / scale).roundToInt()
                val moved =
                    MouseWheelEvent(
                        target,
                        wheel.id,
                        wheel.`when`,
                        wheel.modifiersEx,
                        wheel.x + dx,
                        wheel.y,
                        wheel.xOnScreen + dx,
                        wheel.yOnScreen,
                        wheel.clickCount,
                        wheel.isPopupTrigger,
                        wheel.scrollType,
                        wheel.scrollAmount,
                        wheel.wheelRotation,
                        wheel.preciseWheelRotation,
                    )
                // The pointer goes back where it really is right after: Compose takes a wheel event's
                // position as the pointer's, and would otherwise hover what sits mid-column.
                val back =
                    MouseEvent(
                        target,
                        MouseEvent.MOUSE_MOVED,
                        wheel.`when`,
                        wheel.modifiersEx,
                        wheel.x,
                        wheel.y,
                        wheel.xOnScreen,
                        wheel.yOnScreen,
                        0,
                        false,
                        MouseEvent.NOBUTTON,
                    )
                SwingUtilities.invokeLater {
                    target.dispatchEvent(moved)
                    target.dispatchEvent(back)
                }
            }
        }
    }

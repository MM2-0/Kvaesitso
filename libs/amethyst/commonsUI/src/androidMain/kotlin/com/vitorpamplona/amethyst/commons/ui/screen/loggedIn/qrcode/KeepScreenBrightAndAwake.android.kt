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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow

@Composable
actual fun KeepScreenBrightAndAwake() {
    val view = LocalView.current
    // NOT `(view.context as? Activity)`: under Compose the context is routinely a
    // ContextThemeWrapper, so that cast silently yields null and brightness never changes —
    // no crash, no log, just a dead feature. getActivityWindow() unwraps the ContextWrapper
    // chain (WindowUtils.kt:39-46).
    val window = getActivityWindow()

    DisposableEffect(window, view) {
        // Capture the RAW attribute, not a computed fraction. When no override is set this is
        // BRIGHTNESS_OVERRIDE_NONE (-1f), and restoring that value returns the device to auto
        // brightness. Restoring a *computed* fraction would install an override where none
        // existed and silently disable auto-brightness for the rest of the session.
        val previousBrightness = window?.attributes?.screenBrightness

        // F8: same capture/replay discipline as brightness above, and for the same reason.
        // `view` is the Activity's single shared root ComposeView, and PlayerEventListener
        // (ControlWhenPlayerIsActive.kt:150-165) owns this exact flag while media plays.
        // Hard-setting `false` on dispose — instead of restoring what was here before this
        // screen took it over — would clobber that ownership: navigating back from the QR
        // screen while audio or video is still playing would let the screen sleep mid-playback.
        val previousKeepScreenOn = view.keepScreenOn

        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = 1f }
        }
        view.keepScreenOn = true

        onDispose {
            // Restore the captured value rather than calling a release helper: resetting to
            // BRIGHTNESS_OVERRIDE_NONE unconditionally would clobber an override the user
            // already had, e.g. one left by the fullscreen video controls.
            window?.let { w ->
                previousBrightness?.let { prev ->
                    w.attributes = w.attributes.apply { screenBrightness = prev }
                }
            }
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

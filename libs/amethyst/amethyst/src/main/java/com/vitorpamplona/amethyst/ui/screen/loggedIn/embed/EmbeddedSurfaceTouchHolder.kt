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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Host-side wrapper for an embedded sandbox surface ([androidx.privacysandbox.ui.client.view.SandboxedSdkView]).
 *
 * The surface streams a WebView from the keyless `:napplet` process; touch input is forwarded to it, but
 * the WebView — being in another process — **can't** call [requestDisallowInterceptTouchEvent] on this
 * process's view ancestors. So any scrollable/gesture ancestor (a Compose scroll container, the nav
 * drawer, a pull-to-refresh) steals the vertical drag once it passes touch-slop, and the embedded
 * WebView receives `ACTION_CANCEL` instead of the scroll — it never moves.
 *
 * This wrapper closes that gap: on every touch-down it asks all ancestors not to intercept the gesture,
 * so the surface keeps it and scrolls normally. (Compose's `AndroidView` honors this and disables its
 * own gesture interception for the pointer.) Taps and the surface's own gestures are unaffected.
 */
class EmbeddedSurfaceTouchHolder(
    context: Context,
) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        // Never intercept ourselves — the child surface handles the gesture.
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }
}

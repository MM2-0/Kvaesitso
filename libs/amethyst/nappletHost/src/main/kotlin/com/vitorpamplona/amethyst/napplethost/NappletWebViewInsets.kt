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
package com.vitorpamplona.amethyst.napplethost

import android.view.View
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Insets the root of a **full-screen** sandbox host (browser / napplet / nsite) by the system bars,
 * the display cutout, **and the IME**, then hides those types from its children.
 *
 * The IME half is not optional on current Android. These activities used to lean on
 * `windowSoftInputMode="adjustResize"` to shrink the window when the keyboard opened — the WebView
 * shrank with it and the focused input stayed visible. That flag is a **no-op for apps targeting SDK
 * 35+** running on Android 15+: edge-to-edge is enforced, the window keeps its full height, and the
 * keyboard simply draws over the bottom of the page. Padding the root by the IME inset reproduces the
 * old resize behaviour on our side of the window.
 *
 * The bottom is `max(bars, ime)` rather than a sum because an open IME sits *over* the navigation bar;
 * adding them would leave a dead band the height of the nav bar above the keyboard.
 *
 * Zeroing the consumed types before they reach the children matters for the same reason
 * [dropSystemBarInsets] exists: on targetSdk 35+ a WebView applies whatever insets it receives to its
 * own web content, so leaving them un-consumed pads the page a second time.
 */
fun View.applyFullScreenHostInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val barTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        val bars = insets.getInsets(barTypes)
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
        WindowInsetsCompat
            .Builder(insets)
            .setInsets(barTypes or WindowInsetsCompat.Type.ime(), Insets.NONE)
            .build()
    }
}

/**
 * Stops an **embedded** sandbox WebView from inseting its own web content for the system bars.
 *
 * On targetSdk 35+ a WebView auto-applies any window insets it receives to its web content. The
 * full-screen [NappletHostActivity] handles this by consuming the insets at its root before they reach
 * the WebView; an embedded surface (browser / nsite / napplet hosted in a SurfaceControlViewHost) has
 * no such root, and is already placed inside the host's inset-free content area — so without this the
 * page gets padded a *second* time, leaving an empty band under the status and navigation bars.
 *
 * We zero only the system-bar + display-cutout insets and keep IME, so the page still resizes for the
 * soft keyboard. Setting the listener also bypasses the WebView's own auto-apply, which is the point.
 */
fun WebView.dropSystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val bars = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        WindowInsetsCompat
            .Builder(insets)
            .setInsets(bars, Insets.NONE)
            .build()
    }
}

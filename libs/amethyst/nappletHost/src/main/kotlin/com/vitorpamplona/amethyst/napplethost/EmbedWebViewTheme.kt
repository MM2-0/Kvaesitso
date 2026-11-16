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

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper

/**
 * A context that makes a hosted WebView (embedded surface OR full-screen activity) follow the **app** theme
 * ("DARK"/"LIGHT") rather than the device.
 *
 * WebView's dark decision (`prefers-color-scheme` via algorithmic darkening) reads the context's **theme**
 * (`?android:attr/isLightTheme`), NOT just the Configuration `uiMode` — and an off-window
 * `SurfaceControlViewHost` surface context carries neither the host window's theme nor its night mode. So we
 * force the night flag in the Configuration AND wrap it in a DayNight theme whose `isLightTheme` then resolves
 * from that flag.
 *
 * (Verified on device with a standalone repro: `createConfigurationContext` alone — a night Configuration with
 * no theme — does NOT flip the renderer; the DayNight `ContextThemeWrapper` is what does it, even across the
 * cross-process embedded surface. `setApplicationNightMode` and per-WebView config dispatch do nothing.)
 *
 * "SYSTEM" (or any unrecognized value) returns [base] unchanged, i.e. follows the device — the host already
 * resolves SYSTEM→DARK/LIGHT before handing the theme down for the embedded surfaces.
 */
internal fun nightThemedContext(
    base: Context,
    themeType: String,
): Context {
    val night =
        when (themeType) {
            "DARK" -> Configuration.UI_MODE_NIGHT_YES
            "LIGHT" -> Configuration.UI_MODE_NIGHT_NO
            else -> return base
        }
    val config =
        Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    // DayNight arrived in API 29, the same release as WebView's dark rendering; older devices get the
    // plain DeviceDefault theme, which exists there, instead of a resource id they do not have.
    val theme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.R.style.Theme_DeviceDefault_DayNight
        } else {
            android.R.style.Theme_DeviceDefault
        }
    return ContextThemeWrapper(base.createConfigurationContext(config), theme)
}

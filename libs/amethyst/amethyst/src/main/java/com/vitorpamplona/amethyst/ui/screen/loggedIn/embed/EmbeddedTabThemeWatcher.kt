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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.ThemeType

/**
 * Keeps the warm embedded tabs in sync with the app's DARK/LIGHT theme. An embed WebView resolves its
 * theme from the context it's built with (`nightThemedContext`), once, at construction — a runtime config
 * change does NOT re-flip the renderer — so the only way a live theme switch reaches an already-running
 * surface is to rebuild it. This watches the resolved theme and, on an actual flip, asks
 * [EmbeddedTabHost] to tear down + re-acquire every session in the new theme.
 *
 * Mount once next to [EmbeddedTabLayer]/[EmbeddedTabPreloader]. Draws nothing.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabThemeWatcher() {
    val theme by Amethyst.instance.uiPrefs.value.theme
        .collectAsStateWithLifecycle()
    // SYSTEM resolves against the device night mode, so a scheduled/auto device flip also rebuilds.
    val systemDark = isSystemInDarkTheme()
    val resolvedDark =
        when (theme) {
            ThemeType.DARK -> true
            ThemeType.LIGHT -> false
            ThemeType.SYSTEM -> systemDark
        }

    // The host remembers the theme the warm surfaces were built in; a real flip triggers exactly one rebuild.
    LaunchedEffect(resolvedDark) {
        EmbeddedTabHost.rebuildIfThemeChanged(resolvedDark)
    }
}

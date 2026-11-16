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
package com.vitorpamplona.amethyst.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.ui.components.DefaultInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalTranslationPlatform
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.DisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.collectDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystMaterialTheme
import com.vitorpamplona.amethyst.commons.ui.theme.amethystDarkColors
import com.vitorpamplona.amethyst.commons.ui.theme.amethystLightColors
import com.vitorpamplona.amethyst.commons.ui.theme.collectAccentColor
import com.vitorpamplona.amethyst.commons.ui.theme.isDarkTheme
import com.vitorpamplona.amethyst.commons.ui.theme.transparentBackground
import com.vitorpamplona.amethyst.ui.components.AndroidRichTextPlatform
import com.vitorpamplona.amethyst.ui.components.FlavorTranslationPlatform
import com.vitorpamplona.amethyst.ui.note.platform.AndroidNotePlatform
import com.vitorpamplona.amethyst.ui.platform.AndroidAppPlatform
import com.vitorpamplona.amethyst.ui.platform.AndroidAppServices
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The app theme. A null [accountPubKey] draws with the logged-in account's accent; a screen acting
 * for one specific account (a signer request, a call) passes its pubkey to keep that account's color.
 */
@Composable
fun AmethystTheme(
    accountPubKey: HexKey? = null,
    content: @Composable () -> Unit,
) {
    val uiPrefs = Amethyst.instance.uiPrefs.value
    val theme by uiPrefs.theme.collectAsStateWithLifecycle()
    val accentColor = collectAccentColor(Amethyst.instance.sessionManager, uiPrefs.accentColor, accountPubKey)
    val fontFamily by uiPrefs.fontFamily.collectAsStateWithLifecycle()
    val fontSize by uiPrefs.fontSize.collectAsStateWithLifecycle()
    val displaySettings = collectDisplaySettings(Amethyst.instance.uiState)

    AmethystTheme(theme, accentColor, fontFamily, fontSize, displaySettings, content)
}

/**
 * The shared [AmethystMaterialTheme] plus what only Android has: the system status and
 * navigation bars, tinted to match.
 */
@Composable
fun AmethystTheme(
    prefTheme: ThemeType,
    accentColor: AccentColorType = AccentColorType.PURPLE,
    fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    fontSize: FontSizeType = FontSizeType.NORMAL,
    displaySettings: DisplaySettings = DisplaySettings(),
    content: @Composable () -> Unit,
) {
    // Deliberately no UiModeManager.nightMode write: changing the device night mode needs
    // MODIFY_DAY_NIGHT_MODE, which this app does not declare, so the call silently no-ops — and it
    // ran on every recomposition of the theme, writing device state from inside composition. The
    // in-app choice is applied through the colour scheme below, which is what actually took effect.
    val darkTheme = isDarkTheme(prefTheme)
    val colors =
        remember(darkTheme, accentColor) {
            if (darkTheme) amethystDarkColors(accentColor) else amethystLightColors(accentColor)
        }

    AmethystMaterialTheme(
        darkTheme = darkTheme,
        accentColor = accentColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        displaySettings = displaySettings,
        // ImageLoaderSetup registers the avatar thumbnail cache and the local Blossom bridge.
        profilePictureCache = true,
        colors = colors,
    ) {
        // The platform halves of the shared note and rich-text renderers: media, LaTeX, payments,
        // link previews, the quoted-note card, the note types built on platform engines and the
        // flavour's translator. Every Activity root goes through this theme.
        CompositionLocalProvider(
            LocalRichTextPlatform provides AndroidRichTextPlatform,
            LocalInlineQuoteRenderer provides DefaultInlineQuoteRenderer,
            LocalTranslationPlatform provides FlavorTranslationPlatform,
            LocalNotePlatform provides AndroidNotePlatform,
            LocalAppPlatform provides AndroidAppPlatform,
            LocalAppServices provides AndroidAppServices,
            content = content,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)

            insets.isAppearanceLightNavigationBars = !darkTheme
            insets.isAppearanceLightStatusBars = !darkTheme

            @Suppress("DEPRECATION")
            window.statusBarColor = colors.transparentBackground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.transparentBackground.toArgb()

            view.setBackgroundColor(colors.background.toArgb())
        }
    }
}

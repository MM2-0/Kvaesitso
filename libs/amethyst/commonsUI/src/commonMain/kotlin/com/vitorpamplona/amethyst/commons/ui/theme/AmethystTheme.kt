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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.accentColorFlow
import com.vitorpamplona.amethyst.commons.account.currentAccentColor
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbolsDefaults
import com.vitorpamplona.amethyst.commons.icons.symbols.ProvideAppIcons
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.ui.components.LocalAnimationsEnabled
import com.vitorpamplona.amethyst.commons.ui.components.LocalProfilePictureCache
import com.vitorpamplona.amethyst.commons.ui.screen.DisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.StateFlow

// The accent color (primary/secondary/tertiary) is user-selectable in Settings -> Accent Color.
// Purple keeps the original Amethyst look (purple primary + teal secondary). Every other accent
// uses its single hue across primary and secondary for a cohesive single-color theme.
private fun accentPrimary(
    accent: AccentColorType,
    dark: Boolean,
): Color =
    when (accent) {
        AccentColorType.PURPLE -> if (dark) Purple200 else Purple500
        AccentColorType.BLUE -> if (dark) AccentBlueDark else AccentBlueLight
        AccentColorType.GREEN -> if (dark) AccentGreenDark else AccentGreenLight
        AccentColorType.ORANGE -> if (dark) AccentOrangeDark else AccentOrangeLight
        AccentColorType.RED -> if (dark) AccentRedDark else AccentRedLight
        AccentColorType.PINK -> if (dark) AccentPinkDark else AccentPinkLight
    }

private fun accentSecondary(
    accent: AccentColorType,
    dark: Boolean,
): Color = if (accent == AccentColorType.PURPLE) Teal200 else accentPrimary(accent, dark)

// Representative colour for an accent option, used by the Settings accent-picker swatches — the
// same primary the theme would apply for the given light/dark mode, so the swatch previews the
// real result.
fun AccentColorType.previewColor(dark: Boolean): Color = accentPrimary(this, dark)

/**
 * The accent the theme draws with: the account's own color, else the app-wide [appDefault]. A null
 * [accountPubKey] follows the logged-in account; a screen acting for one account passes its pubkey.
 * Starts on the current value, so an account's color shows from the first frame.
 */
@Composable
fun collectAccentColor(
    sessionManager: AccountSessionManager,
    appDefault: StateFlow<AccentColorType>,
    accountPubKey: HexKey? = null,
): AccentColorType {
    val flow = remember(sessionManager, appDefault, accountPubKey) { sessionManager.accentColorFlow(appDefault, accountPubKey) }
    return flow.collectAsState(sessionManager.currentAccentColor(appDefault, accountPubKey)).value
}

fun amethystDarkColors(accent: AccentColorType): ColorScheme =
    amethystDarkColorScheme(
        primary = accentPrimary(accent, dark = true),
        secondary = accentSecondary(accent, dark = true),
        inversePrimary = accentPrimary(accent, dark = false),
    )

fun amethystLightColors(accent: AccentColorType): ColorScheme =
    amethystLightColorScheme(
        primary = accentPrimary(accent, dark = false),
        secondary = accentSecondary(accent, dark = false),
        inversePrimary = accentPrimary(accent, dark = true),
    )

/**
 * Whether [prefTheme] resolves to dark, following the system for anything but an explicit choice.
 * [systemDark] is the system's preference; a front end that reads it better than Compose does (the
 * desktop asks the OS, which Compose cannot do on Linux) passes its own.
 */
@Composable
fun isDarkTheme(
    prefTheme: ThemeType,
    systemDark: Boolean = isSystemInDarkTheme(),
): Boolean =
    when (prefTheme) {
        ThemeType.DARK -> true
        ThemeType.LIGHT -> false
        else -> systemDark
    }

/**
 * Amethyst's Material theme and the app-wide composition locals, for any front end: the
 * accent-derived colour scheme, the chosen font family and size, the display settings, and the
 * icon font. Platform chrome (Android's system bars) is the caller's to apply.
 *
 * [profilePictureCache] is true where the image loader registers the avatar thumbnail cache.
 * [systemFontFamily] is what the "system" font choice draws with (null: Compose's default, which is
 * already the OS font on Android), and [iconWeight] the Material Symbols stroke weight: the desktop
 * passes the OS's own UI font and an icon weight that matches its icons. [typography] is the type
 * scale before the font family is applied: the desktop passes one sized for desktop reading.
 * [showHover] false hides the hover layer: the desktop clears it while its window is in the
 * background, as native apps do.
 */
@Composable
fun AmethystMaterialTheme(
    darkTheme: Boolean,
    accentColor: AccentColorType = AccentColorType.PURPLE,
    fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    fontSize: FontSizeType = FontSizeType.NORMAL,
    displaySettings: DisplaySettings = DisplaySettings(),
    profilePictureCache: Boolean = false,
    systemFontFamily: FontFamily? = null,
    iconWeight: Int = MaterialSymbolsDefaults.WEIGHT,
    typography: Typography = DefaultTypography,
    showHover: Boolean = true,
    colors: ColorScheme = remember(darkTheme, accentColor) { if (darkTheme) amethystDarkColors(accentColor) else amethystLightColors(accentColor) },
    content: @Composable () -> Unit,
) {
    val resolvedFontFamily =
        remember(fontFamily, systemFontFamily) {
            if (fontFamily == FontFamilyType.SYSTEM) systemFontFamily else fontFamily.toFontFamily()
        }
    val themedTypography = remember(resolvedFontFamily, typography) { typography.withFontFamily(resolvedFontFamily) }

    val density = LocalDensity.current
    val scaledDensity =
        remember(density, fontSize) {
            Density(density.density, density.fontScale * fontSize.scale)
        }

    val rippleConfiguration = remember(darkTheme, showHover) { amethystRippleConfiguration(darkTheme, showHover) }

    MaterialTheme(
        colorScheme = colors,
        typography = themedTypography,
        shapes = Shapes,
        content = {
            ProvideAppIcons(weight = iconWeight) {
                CompositionLocalProvider(
                    LocalDensity provides scaledDensity,
                    LocalRippleConfiguration provides rippleConfiguration,
                    LocalProfilePictureCache provides profilePictureCache,
                    LocalDisplaySettings provides displaySettings,
                    // Performance mode turns decorative animations (crossfades) off app-wide.
                    LocalAnimationsEnabled provides !displaySettings.performanceMode,
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = resolvedFontFamily)),
                    content = content,
                )
            }
        },
    )
}

// Maps the user-selected font preference to a Compose [FontFamily].
// SYSTEM returns null so the platform default is used unchanged.
fun FontFamilyType.toFontFamily(): FontFamily? =
    when (this) {
        FontFamilyType.SYSTEM -> null
        FontFamilyType.SANS_SERIF -> FontFamily.SansSerif
        FontFamilyType.SERIF -> FontFamily.Serif
        FontFamilyType.MONOSPACE -> FontFamily.Monospace
    }

/**
 * Material's ripple, with a lighter hover layer in light mode. A note card is a whole clickable
 * card, and Material's 8% black wash over it reads as a heavy gray block under a mouse; 2.2% takes
 * the #FDFDFD background to 248 gray (243 for a note inside a hovered note, as hovers stack) and
 * still marks the card. Press, focus and drag keep Material's values (touch feedback is unchanged), and
 * dark mode keeps its hover: a white wash over a dark card is already faint.
 *
 * RippleAlpha is deprecated in Material3 1.12, but its replacements (RippleThemeConfiguration,
 * RippleConfiguration.Focus) only cover focus: the hover layer has no public setting yet.
 */
@Suppress("DEPRECATION")
fun amethystRippleConfiguration(
    darkTheme: Boolean,
    showHover: Boolean = true,
): RippleConfiguration {
    val defaults = RippleDefaults.RippleAlpha
    return RippleConfiguration(
        rippleAlpha =
            RippleAlpha(
                draggedAlpha = defaults.draggedAlpha,
                focusedAlpha = defaults.focusedAlpha,
                hoveredAlpha =
                    when {
                        !showHover -> 0f
                        darkTheme -> defaults.hoveredAlpha
                        else -> LIGHT_HOVER_ALPHA
                    },
                pressedAlpha = defaults.pressedAlpha,
            ),
    )
}

private const val LIGHT_HOVER_ALPHA = 0.022f

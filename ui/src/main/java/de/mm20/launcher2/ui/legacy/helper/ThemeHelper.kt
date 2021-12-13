package de.mm20.launcher2.ui.legacy.helper

import android.content.res.Resources
import de.mm20.launcher2.preferences.ColorSchemes
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R

object ThemeHelper {
    fun applyTheme(theme: Resources.Theme) {
        val colorScheme = when(LauncherPreferences.instance.colorScheme) {
            ColorSchemes.BLACK -> R.style.BlackWhiteColors
            else -> R.style.DefaultColors
        }
        theme.applyStyle(colorScheme, true)
    }
}
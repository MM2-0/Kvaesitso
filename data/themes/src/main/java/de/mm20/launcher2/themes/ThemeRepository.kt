package de.mm20.launcher2.themes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class ThemeRepository(
    private val context: Context,
) {
    private val customTheme = MutableStateFlow(Theme(
        id = UUID.randomUUID(),
        builtIn = false,
        name = "Custom",
        corePalette = EmptyCorePalette,
        lightColorScheme = DefaultLightColorScheme,
        darkColorScheme = DefaultDarkColorScheme,
    ))

    fun getThemes(): Flow<List<Theme>> {
        return flowOf(getBuiltInThemes()).combine(customTheme) {
            builtIn, custom ->
            builtIn + custom
        }
    }

    fun getTheme(id: UUID): Flow<Theme?> {
        if (id == DefaultThemeId) return flowOf(getDefaultTheme())
        return customTheme
    }

    fun createTheme(theme: Theme) {
    }

    fun updateTheme(theme: Theme) {
        Log.d("MM20", "updateTheme: $theme")
        customTheme.value = theme
    }

    fun getThemeOrDefault(id: UUID): Flow<Theme> {
        return getTheme(id).map { it ?: getDefaultTheme() }
    }

    private fun getBuiltInThemes(): List<Theme> {
        return listOf(
            getDefaultTheme(),
        )
    }

    fun getDefaultTheme(): Theme {
        return Theme(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_default),
            corePalette = EmptyCorePalette,
            lightColorScheme = DefaultLightColorScheme,
            darkColorScheme = DefaultDarkColorScheme,
        )
    }

}
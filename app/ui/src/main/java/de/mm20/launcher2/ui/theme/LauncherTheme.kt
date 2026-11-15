package de.mm20.launcher2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.mm20.launcher2.preferences.ColorScheme as ColorSchemePref
import de.mm20.launcher2.preferences.ui.BadgeSettings
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.ui.component.LocalBadgeColor
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import de.mm20.launcher2.ui.theme.motion.scaledBy
import de.mm20.launcher2.ui.theme.shapes.shapesOf
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.transparencySchemeOf
import de.mm20.launcher2.ui.theme.typography.typographyOf
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.compose.koinInject

@Composable
fun LauncherTheme(
    wallpaperGlass: Boolean = true,
    loadingContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val uiSettings: UiSettings = koinInject()
    val themeRepository: ThemeRepository = koinInject()
    val performanceSettings: PerformanceSettings = koinInject()
    val badgeSettings: BadgeSettings = koinInject()

    val reduceAnimations by remember { performanceSettings.reduceAnimations }.collectAsState(false)
    val animationSpeed by remember { performanceSettings.animationSpeed }.collectAsState(1f)
    val badgeColorArgb by remember { badgeSettings.notificationColor }.collectAsState(null)
    val badgeColor = badgeColorArgb?.let { Color(it) }

    val themeColors by remember {
        uiSettings.colorsId.flatMapLatest {
            themeRepository.colors.getOrDefault(it)
        }
    }
        .collectAsState(null)

    val themeShapes by remember {
        uiSettings.shapesId.flatMapLatest {
            themeRepository.shapes.getOrDefault(it)
        }
    }
        .collectAsState(null)

    val themeTypography by remember {
        uiSettings.typographyId.flatMapLatest {
            themeRepository.typographies.getOrDefault(it)
        }
    }
        .collectAsState(null)

    val themeTransparencies by remember {
        uiSettings.transparenciesId.flatMapLatest {
            themeRepository.transparencies.getOrDefault(it)
        }
    }
        .collectAsState(null)

    val colorSchemePref by remember {
        uiSettings.colorScheme
    }
        .collectAsState(ColorSchemePref.System)
    val nightStart by remember { uiSettings.colorSchemeNightStart }.collectAsState(20)
    val dayStart by remember { uiSettings.colorSchemeDayStart }.collectAsState(7)
    val fontScale by remember { uiSettings.fontScale }.collectAsState(1f)

    val darkTheme =
        when (colorSchemePref) {
            ColorSchemePref.Dark -> true
            ColorSchemePref.Light -> false
            ColorSchemePref.System -> isSystemInDarkTheme()
            ColorSchemePref.Time -> rememberIsNight(nightStart, dayStart)
        }

    if (
        themeColors == null ||
            themeShapes == null ||
            themeTransparencies == null ||
            themeTypography == null
    ) {
        loadingContent()
        return
    }

    val colorScheme =
        if (darkTheme) {
            darkColorSchemeOf(themeColors!!)
        } else {
            lightColorSchemeOf(themeColors!!)
        }

    val shapes = shapesOf(themeShapes!!)
    val typography = typographyOf(themeTypography!!)

    val transparencyScheme = transparencySchemeOf(themeTransparencies!!, darkTheme)

    val baseDensity = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    if (!view.isInEditMode && window != null) {
        androidx.compose.runtime.SideEffect {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalTransparencyScheme provides transparencyScheme,
        LocalBadgeColor provides badgeColor,
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            motionScheme =
                remember(reduceAnimations, animationSpeed) {
                    MotionScheme.expressive().scaledBy(!reduceAnimations, animationSpeed)
                },
            content = {
                // Compose's default content colour is black. Without this, icons and texts that sit
                // on a plain background (not in a Surface or Scaffold) are black on the dark theme.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    content = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        if (wallpaperGlass) {
                            de.mm20.launcher2.ui.component.FrostedGlassProvider {
                                if (context is de.mm20.launcher2.ui.settings.SettingsActivity) {
                                    de.mm20.launcher2.ui.component.VirtualAppTheme(
                                        content = content
                                    )
                                } else content()
                            }
                        } else content()
                    },
                )
            },
        )
    }
}

/**
 * True while the current time falls in the dark ("night") window that starts at [nightStart] and
 * ends at [dayStart] (hours of day, wrapping past midnight). Re-checks every minute so the theme
 * flips at the boundary without restarting the launcher.
 */
@Composable
private fun rememberIsNight(nightStart: Int, dayStart: Int): Boolean {
    var isNight by remember {
        mutableStateOf(computeIsNight(LocalTime.now().hour, nightStart, dayStart))
    }
    LaunchedEffect(nightStart, dayStart) {
        while (true) {
            isNight = computeIsNight(LocalTime.now().hour, nightStart, dayStart)
            delay(60_000L)
        }
    }
    return isNight
}

private fun computeIsNight(hour: Int, nightStart: Int, dayStart: Int): Boolean {
    if (nightStart == dayStart) return false
    val nightLength = ((dayStart - nightStart) + 24) % 24
    val offset = ((hour - nightStart) + 24) % 24
    return offset < nightLength
}

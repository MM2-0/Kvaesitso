package de.mm20.launcher2.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.Font
import de.mm20.launcher2.preferences.SurfaceShape
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.*
import de.mm20.launcher2.ui.theme.shapes.shapesOf
import de.mm20.launcher2.ui.theme.typography.DefaultTypography
import de.mm20.launcher2.ui.theme.typography.getDeviceDefaultTypography
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import de.mm20.launcher2.preferences.ColorScheme as ColorSchemePref


@Composable
fun LauncherTheme(
    content: @Composable () -> Unit
) {

    val context = LocalContext.current
    val uiSettings: UiSettings = koinInject()
    val themeRepository: ThemeRepository = koinInject()

    val themeColors by remember {
        uiSettings.colors.flatMapLatest {
            themeRepository.getColorsOrDefault(it)
        }
    }.collectAsState(null)

    val themeShapes by remember {
        uiSettings.shapes.flatMapLatest {
            themeRepository.getShapesOrDefault(it)
        }
    }.collectAsState(null)

    val colorSchemePref by remember { uiSettings.colorScheme }.collectAsState(
        ColorSchemePref.System
    )
    val darkTheme =
        colorSchemePref == ColorSchemePref.Dark || colorSchemePref == ColorSchemePref.System && isSystemInDarkTheme()

    if (themeColors == null || themeShapes == null) {
        return
    }

    val colorScheme = if (darkTheme) {
        darkColorSchemeOf(themeColors!!)
    } else {
        lightColorSchemeOf(themeColors!!)
    }

    val shapes = shapesOf(themeShapes!!)



    val font by remember { uiSettings.font }.collectAsState(
        Font.Outfit
    )

    val typography = remember(font) {
        getTypography(context, font)
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

fun getTypography(context: Context, font: Font?): Typography {
    return when (font) {
        Font.System -> getDeviceDefaultTypography(context)
        else -> DefaultTypography
    }
}
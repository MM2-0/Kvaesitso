package de.mm20.launcher2.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.locals.LocalColorScheme
import de.mm20.launcher2.ui.theme.colors.toDarkColorScheme
import de.mm20.launcher2.ui.theme.colors.toLightColorScheme
import kotlinx.coroutines.flow.map

val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)


val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 57.sp,
        fontWeight = FontWeight.Normal,
    ),
    displayMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 45.sp,
        fontWeight = FontWeight.Normal,
    ),
    displaySmall = TextStyle(
        fontFamily = Inter,
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {

    val dataStore = LocalContext.current.dataStore

    val theme by remember {
        dataStore.data.map { it.appearance.theme }
    }.collectAsState(initial = Theme.System)

    val darkTheme = theme == Theme.Dark || theme == Theme.System && isSystemInDarkTheme()

    val colorScheme = LocalColorScheme.current

    val colors = if (darkTheme) {
        colorScheme.toDarkColorScheme()
    } else {
        colorScheme.toLightColorScheme()
    }

    androidx.compose.material.MaterialTheme(
        colors = if (darkTheme) darkColors() else lightColors()
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            content = content
        )
    }


}
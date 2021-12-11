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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.locals.LocalColorScheme
import de.mm20.launcher2.ui.theme.colors.toDarkColorScheme
import de.mm20.launcher2.ui.theme.colors.toLightColorScheme
import kotlinx.coroutines.flow.map

val Poppins = FontFamily(
    Font(R.font.poppins100, FontWeight.Thin, FontStyle.Normal),
    Font(R.font.poppins100i, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.poppins200, FontWeight.ExtraLight, FontStyle.Normal),
    Font(R.font.poppins200i, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.poppins300, FontWeight.Light, FontStyle.Normal),
    Font(R.font.poppins300i, FontWeight.Light, FontStyle.Italic),
    Font(R.font.poppins400, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.poppins400i, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.poppins500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.poppins500i, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.poppins600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.poppins600i, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.poppins700, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.poppins700i, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.poppins800, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.poppins800i, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.poppins900, FontWeight.Black, FontStyle.Normal),
    Font(R.font.poppins900i, FontWeight.Black, FontStyle.Italic),
)


val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Poppins,
        fontSize = 57.sp,
        fontWeight = FontWeight.Normal,
    ),
    displayMedium = TextStyle(
        fontFamily = Poppins,
        fontSize = 45.sp,
        fontWeight = FontWeight.Normal,
    ),
    displaySmall = TextStyle(
        fontFamily = Poppins,
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineLarge = TextStyle(
        fontFamily = Poppins,
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = TextStyle(
        fontFamily = Poppins,
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontFamily = Poppins,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins,
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
        fontFamily = Poppins,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = Poppins,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = Poppins,
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
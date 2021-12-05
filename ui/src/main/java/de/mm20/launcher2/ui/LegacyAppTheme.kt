package de.mm20.launcher2.ui

import android.util.TypedValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.MaterialTheme as Material2Theme

val legacyTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp,
        fontWeight = FontWeight.Normal,
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        fontWeight = FontWeight.Normal,
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
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
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    ),
)


@Composable
fun LegacyLauncherTheme(content: @Composable () -> Unit) {
    val theme = LocalContext.current.theme

    val colorSurface = TypedValue()
    theme.resolveAttribute(R.attr.colorSurface, colorSurface, true)
    val colorSurfaceVariant = TypedValue()
    theme.resolveAttribute(R.attr.colorSurfaceVariant, colorSurfaceVariant, true)

    val colorPrimary = TypedValue()
    theme.resolveAttribute(R.attr.colorPrimary, colorSurfaceVariant, true)


    MaterialTheme(
        typography = legacyTypography,
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
            surface = Color(colorSurface.data),
            primary = Color(colorSurface.data),
        ) else lightColorScheme(
            surface = Color(colorSurface.data),
            primary = Color(colorSurface.data),
        )
    ) {
        Material2Theme(
            colors = if (isSystemInDarkTheme()) darkColors(
                surface = Color(colorSurface.data),
                primary = Color(colorSurface.data),
            ) else lightColors(
                surface = Color(colorSurface.data),
                primary = Color(colorSurface.data),
            ),
            content = content
        )
    }
}
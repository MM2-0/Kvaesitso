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
    theme.resolveAttribute(R.attr.colorPrimary, colorPrimary, true)
    val colorOnPrimary = TypedValue()
    theme.resolveAttribute(R.attr.colorOnPrimary, colorOnPrimary, true)
    val colorPrimaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorPrimaryContainer, colorPrimaryContainer, true)
    val colorOnPrimaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorOnPrimaryContainer, colorOnPrimaryContainer, true)

    val colorSecondary = TypedValue()
    theme.resolveAttribute(R.attr.colorSecondary, colorSecondary, true)
    val colorOnSecondary = TypedValue()
    theme.resolveAttribute(R.attr.colorOnSecondary, colorOnSecondary, true)
    val colorSecondaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorSecondaryContainer, colorSecondaryContainer, true)
    val colorOnSecondaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorOnSecondaryContainer, colorOnSecondaryContainer, true)

    val colorTertiary = TypedValue()
    theme.resolveAttribute(R.attr.colorTertiary, colorTertiary, true)
    val colorOnTertiary = TypedValue()
    theme.resolveAttribute(R.attr.colorOnTertiary, colorOnTertiary, true)
    val colorTertiaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorTertiaryContainer, colorTertiaryContainer, true)
    val colorOnTertiaryContainer = TypedValue()
    theme.resolveAttribute(R.attr.colorOnTertiaryContainer, colorOnTertiaryContainer, true)


    MaterialTheme(
        typography = legacyTypography,
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
            surface = Color(colorSurface.data),
            surfaceVariant = Color(colorSurfaceVariant.data),
            primary = Color(colorPrimary.data),
            onPrimary = Color(colorOnPrimary.data),
            primaryContainer = Color(colorPrimaryContainer.data),
            onPrimaryContainer = Color(colorOnPrimaryContainer.data),
            secondary = Color(colorSecondary.data),
            onSecondary = Color(colorOnSecondary.data),
            secondaryContainer = Color(colorSecondaryContainer.data),
            onSecondaryContainer = Color(colorOnSecondaryContainer.data),
            tertiary = Color(colorTertiary.data),
            onTertiary = Color(colorOnTertiary.data),
            tertiaryContainer = Color(colorTertiaryContainer.data),
            onTertiaryContainer = Color(colorOnTertiaryContainer.data),
        ) else lightColorScheme(
            surface = Color(colorSurface.data),
            surfaceVariant = Color(colorSurfaceVariant.data),
            primary = Color(colorPrimary.data),
            onPrimary = Color(colorOnPrimary.data),
            primaryContainer = Color(colorPrimaryContainer.data),
            onPrimaryContainer = Color(colorOnPrimaryContainer.data),
            secondary = Color(colorSecondary.data),
            onSecondary = Color(colorOnSecondary.data),
            secondaryContainer = Color(colorSecondaryContainer.data),
            onSecondaryContainer = Color(colorOnSecondaryContainer.data),
            tertiary = Color(colorTertiary.data),
            onTertiary = Color(colorOnTertiary.data),
            tertiaryContainer = Color(colorTertiaryContainer.data),
            onTertiaryContainer = Color(colorOnTertiaryContainer.data),
        )
    ) {
        Material2Theme(
            colors = if (isSystemInDarkTheme()) darkColors(
                surface = Color(colorSurface.data),
                primary = Color(colorPrimary.data),
            ) else lightColors(
                surface = Color(colorSurface.data),
                primary = Color(colorPrimary.data),
            ),
            content = content
        )
    }
}
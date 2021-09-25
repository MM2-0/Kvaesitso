package de.mm20.launcher2.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val legacyTypography = Typography(
    h1 = TextStyle(
        fontSize = 96.sp,
        fontWeight = FontWeight.Light,
    ),
    h2 = TextStyle(
        fontSize = 60.sp,
        fontWeight = FontWeight.Light,
    ),
    h3 = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Normal,
    ),
    h4 = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Normal,
    ),
    h5 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    h6 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    caption = TextStyle(
        fontSize = 13.sp
    ),
    subtitle1 = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    ),
    subtitle2 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    body1 = TextStyle(
        fontSize = 14.sp
    ),
    body2 = TextStyle(
        fontSize = 13.sp
    )
)

@Composable
fun LegacyLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = legacyTypography,
        content = content,
        colors = if (isSystemInDarkTheme()) darkColors() else lightColors()
    )
}
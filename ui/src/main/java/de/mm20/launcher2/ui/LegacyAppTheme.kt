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
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    h2 = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    ),
    h3 = TextStyle(
        fontSize = 13.sp,
    ),
    caption = TextStyle(
        fontSize = 13.sp
    ),
    body1 = TextStyle(
        fontSize = 13.sp
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
       colors = if(isSystemInDarkTheme()) darkColors() else lightColors()
   )
}
package de.mm20.launcher2.ui.theme.typography

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

fun makeTypography(
    headlineFamily: FontFamily? = null,
    bodyFamily: FontFamily? = null): Typography {
    val baseTypography = Typography()
    return Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = bodyFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = bodyFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = bodyFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold)
    )
}
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
        displayLargeEmphasized = baseTypography.displayLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        displayMediumEmphasized = baseTypography.displayMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        displaySmallEmphasized = baseTypography.displaySmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineLargeEmphasized = baseTypography.headlineLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Bold),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineMediumEmphasized = baseTypography.headlineMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Bold),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        headlineSmallEmphasized = baseTypography.headlineSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Bold),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleLargeEmphasized = baseTypography.titleLargeEmphasized.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Bold),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleMediumEmphasized = baseTypography.titleMediumEmphasized.copy(fontFamily = headlineFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        titleSmallEmphasized = baseTypography.titleSmallEmphasized.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Bold),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = bodyFamily),
        bodyLargeEmphasized = baseTypography.bodyLargeEmphasized.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = bodyFamily),
        bodyMediumEmphasized = baseTypography.bodyMediumEmphasized.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = bodyFamily),
        bodySmallEmphasized = baseTypography.bodySmallEmphasized.copy(fontFamily = bodyFamily, fontWeight = FontWeight.Medium),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        labelLargeEmphasized = baseTypography.labelLargeEmphasized.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        labelMediumEmphasized = baseTypography.labelMediumEmphasized.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = headlineFamily, fontWeight = FontWeight.Medium),
        labelSmallEmphasized = baseTypography.labelSmallEmphasized.copy(fontFamily = headlineFamily, fontWeight = FontWeight.SemiBold),
    )
}
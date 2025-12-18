package de.mm20.launcher2.ui.theme.typography

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.themes.typography.DefaultEmphasizedTextStyles
import de.mm20.launcher2.themes.typography.DefaultTextStyles
import de.mm20.launcher2.ui.theme.typography.fontfamily.Outfit
import de.mm20.launcher2.ui.theme.typography.fontfamily.getDeviceBodyFontFamily
import de.mm20.launcher2.ui.theme.typography.fontfamily.getDeviceHeadlineFontFamily
import de.mm20.launcher2.themes.typography.FontFamily as ThemeFontFamily
import de.mm20.launcher2.themes.typography.FontWeight as ThemeFontWeight
import de.mm20.launcher2.themes.typography.TextStyle as ThemeTextStyle
import de.mm20.launcher2.themes.typography.Typography as ThemeTypography

@Composable
fun typographyOf(typography: ThemeTypography): Typography {
    val context = LocalContext.current
    return remember(context, typography) {
        val base = Typography()

        val fonts = getFontFamilies(context, typography.fonts)

        base.copy(
            displayLarge = textStyleOf(
                typography.styles.displayLarge,
                DefaultTextStyles.displayLarge!!,
                base.displayLarge,
                fonts,
            ),
            displayLargeEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.displayLarge,
                typography.styles.displayLarge,
                DefaultEmphasizedTextStyles.displayLarge!!,
                DefaultTextStyles.displayLarge!!,
                base.displayLargeEmphasized,
                fonts,
            ),
            displayMedium = textStyleOf(
                typography.styles.displayMedium,
                DefaultTextStyles.displayMedium!!,
                base.displayMedium,
                fonts,
            ),
            displayMediumEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.displayMedium,
                typography.styles.displayMedium,
                DefaultEmphasizedTextStyles.displayMedium!!,
                DefaultTextStyles.displayMedium!!,
                base.displayMediumEmphasized,
                fonts,
            ),
            displaySmall = textStyleOf(
                typography.styles.displaySmall,
                DefaultTextStyles.displaySmall!!,
                base.displaySmall,
                fonts,
            ),
            displaySmallEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.displaySmall,
                typography.styles.displaySmall,
                DefaultEmphasizedTextStyles.displaySmall!!,
                DefaultTextStyles.displaySmall!!,
                base.displaySmallEmphasized,
                fonts,
            ),
            headlineLarge = textStyleOf(
                typography.styles.headlineLarge,
                DefaultTextStyles.headlineLarge!!,
                base.headlineLarge,
                fonts,
            ),
            headlineLargeEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.headlineLarge,
                typography.styles.headlineLarge,
                DefaultEmphasizedTextStyles.headlineLarge!!,
                DefaultTextStyles.headlineLarge!!,
                base.headlineLargeEmphasized,
                fonts,
            ),
            headlineMedium = textStyleOf(
                typography.styles.headlineMedium,
                DefaultTextStyles.headlineMedium!!,
                base.headlineMedium,
                fonts,
            ),
            headlineMediumEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.headlineMedium,
                typography.styles.headlineMedium,
                DefaultEmphasizedTextStyles.headlineMedium!!,
                DefaultTextStyles.headlineMedium!!,
                base.headlineMediumEmphasized,
                fonts,
            ),
            headlineSmall = textStyleOf(
                typography.styles.headlineSmall,
                DefaultTextStyles.headlineSmall!!,
                base.headlineSmall,
                fonts,
            ),
            headlineSmallEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.headlineSmall,
                typography.styles.headlineSmall,
                DefaultEmphasizedTextStyles.headlineSmall!!,
                DefaultTextStyles.headlineSmall!!,
                base.headlineSmallEmphasized,
                fonts,
            ),
            titleLarge = textStyleOf(
                typography.styles.titleLarge,
                DefaultTextStyles.titleLarge!!,
                base.titleLarge,
                fonts,
            ),
            titleLargeEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.titleLarge,
                typography.styles.titleLarge,
                DefaultEmphasizedTextStyles.titleLarge!!,
                DefaultTextStyles.titleLarge!!,
                base.titleLargeEmphasized,
                fonts,
            ),
            titleMedium = textStyleOf(
                typography.styles.titleMedium,
                DefaultTextStyles.titleMedium!!,
                base.titleMedium,
                fonts,
            ),
            titleMediumEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.titleMedium,
                typography.styles.titleMedium,
                DefaultEmphasizedTextStyles.titleMedium!!,
                DefaultTextStyles.titleMedium!!,
                base.titleMediumEmphasized,
                fonts,
            ),
            titleSmall = textStyleOf(
                typography.styles.titleSmall,
                DefaultTextStyles.titleSmall!!,
                base.titleSmall,
                fonts,
            ),
            titleSmallEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.titleSmall,
                typography.styles.titleSmall,
                DefaultEmphasizedTextStyles.titleSmall!!,
                DefaultTextStyles.titleSmall!!,
                base.titleSmallEmphasized,
                fonts,
            ),
            bodyLarge = textStyleOf(
                typography.styles.bodyLarge,
                DefaultTextStyles.bodyLarge!!,
                base.bodyLarge,
                fonts,
            ),
            bodyLargeEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.bodyLarge,
                typography.styles.bodyLarge,
                DefaultEmphasizedTextStyles.bodyLarge!!,
                DefaultTextStyles.bodyLarge!!,
                base.bodyLargeEmphasized,
                fonts,
            ),
            bodyMedium = textStyleOf(
                typography.styles.bodyMedium,
                DefaultTextStyles.bodyMedium!!,
                base.bodyMedium,
                fonts,
            ),
            bodyMediumEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.bodyMedium,
                typography.styles.bodyMedium,
                DefaultEmphasizedTextStyles.bodyMedium!!,
                DefaultTextStyles.bodyMedium!!,
                base.bodyMediumEmphasized,
                fonts,
            ),
            bodySmall = textStyleOf(
                typography.styles.bodySmall,
                DefaultTextStyles.bodySmall!!,
                base.bodySmall,
                fonts,
            ),
            bodySmallEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.bodySmall,
                typography.styles.bodySmall,
                DefaultEmphasizedTextStyles.bodySmall!!,
                DefaultTextStyles.bodySmall!!,
                base.bodySmallEmphasized,
                fonts,
            ),
            labelLarge = textStyleOf(
                typography.styles.labelLarge,
                DefaultTextStyles.labelLarge!!,
                base.labelLarge,
                fonts,
            ),
            labelLargeEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.labelLarge,
                typography.styles.labelLarge,
                DefaultEmphasizedTextStyles.labelLarge!!,
                DefaultTextStyles.labelLarge!!,
                base.labelLargeEmphasized,
                fonts,
            ),
            labelMedium = textStyleOf(
                typography.styles.labelMedium,
                DefaultTextStyles.labelMedium!!,
                base.labelMedium,
                fonts,
            ),
            labelMediumEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.labelMedium,
                typography.styles.labelMedium,
                DefaultEmphasizedTextStyles.labelMedium!!,
                DefaultTextStyles.labelMedium!!,
                base.labelMediumEmphasized,
                fonts,
            ),
            labelSmall = textStyleOf(
                typography.styles.labelSmall,
                DefaultTextStyles.labelSmall!!,
                base.labelSmall,
                fonts,
            ),
            labelSmallEmphasized = emphasizedTextStyleOf(
                typography.emphasizedStyles.labelSmall,
                typography.styles.labelSmall,
                DefaultEmphasizedTextStyles.labelSmall!!,
                DefaultTextStyles.labelSmall!!,
                base.labelSmallEmphasized,
                fonts,
            ),
        )
    }
}

private fun textStyleOf(
    style: ThemeTextStyle<ThemeFontWeight.Absolute?>?,
    fallback: ThemeTextStyle<ThemeFontWeight.Absolute?>,
    base: TextStyle,
    fonts: Map<String, FontFamily?>,
): TextStyle {
    return base.copy(
        fontFamily = (style?.fontFamily ?: fallback.fontFamily)?.let { fonts[it] }
            ?: base.fontFamily,
        fontWeight = (style?.fontWeight?.weight
            ?: fallback.fontWeight?.weight)?.let { FontWeight(it.coerceIn(100, 900)) } ?: base.fontWeight,
        fontSize = (style?.fontSize ?: fallback.fontSize)?.sp ?: base.fontSize,
        lineHeight = (style?.lineHeight ?: fallback.lineHeight)?.em ?: base.lineHeight,
        letterSpacing = (style?.letterSpacing ?: fallback.letterSpacing)?.em ?: base.letterSpacing,
    )
}

private fun emphasizedTextStyleOf(
    style: ThemeTextStyle<ThemeFontWeight?>?,
    parent: ThemeTextStyle<ThemeFontWeight.Absolute?>?,
    fallback: ThemeTextStyle<ThemeFontWeight?>,
    fallbackParent: ThemeTextStyle<ThemeFontWeight.Absolute?>,
    base: TextStyle,
    fonts: Map<String, FontFamily?>,
): TextStyle {
    val weight: ThemeFontWeight? = style?.fontWeight ?: fallback.fontWeight
    val parentWeight = parent?.fontWeight ?: fallbackParent.fontWeight

    val fontWeight = when (weight) {
        is ThemeFontWeight.Absolute -> FontWeight(weight.weight.coerceIn(100, 900))
        is ThemeFontWeight.Relative if (parentWeight != null) -> {
            FontWeight((parentWeight.weight + weight.relativeWeight).coerceIn(100, 900))
        }

        else -> base.fontWeight
    }

    return base.copy(
        fontFamily = (style?.fontFamily
            ?: parent?.fontFamily
            ?: fallback.fontFamily
            ?: fallbackParent.fontFamily)
            ?.let { fonts[it] }
            ?: base.fontFamily,
        fontWeight = fontWeight,
        fontSize = (style?.fontSize ?: parent?.fontSize ?: fallback.fontSize
        ?: fallbackParent.fontSize)?.sp ?: base.fontSize,
        lineHeight = (style?.lineHeight ?: parent?.lineHeight ?: fallback.lineHeight
        ?: fallbackParent.lineHeight)?.em
            ?: base.lineHeight,
        letterSpacing = (style?.letterSpacing ?: parent?.letterSpacing
        ?: fallback.letterSpacing ?: fallbackParent.letterSpacing)?.em ?: base.letterSpacing,
    )
}

private fun getFontFamilies(
    context: Context,
    fonts: Map<String, ThemeFontFamily?>
): Map<String, FontFamily?> {
    val distinct = fonts.values.distinct()

    val map: Map<ThemeFontFamily?, FontFamily> = distinct.associateWith {
        fontFamilyOf(context, it)
    }

    return fonts.keys.associateWith { map[fonts[it]] }
}

fun fontFamilyOf(
    context: Context,
    fontFamily: ThemeFontFamily?
): FontFamily {
    return when (fontFamily) {
        is ThemeFontFamily.LauncherDefault -> Outfit
        is ThemeFontFamily.DeviceHeadline -> getDeviceHeadlineFontFamily(context)
        is ThemeFontFamily.DeviceBody -> getDeviceBodyFontFamily(context)
        is ThemeFontFamily.SansSerif -> FontFamily.SansSerif
        is ThemeFontFamily.Serif -> FontFamily.Serif
        is ThemeFontFamily.Monospace -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}
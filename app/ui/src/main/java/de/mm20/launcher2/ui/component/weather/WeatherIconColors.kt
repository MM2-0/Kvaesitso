package de.mm20.launcher2.ui.component.weather

import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import blend.Blend
import de.mm20.launcher2.ui.ktx.atTone
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import hct.Hct


data class WeatherIconColors(
    val sun: Color,
    val moon: Color,
    val cloudDark1: Color,
    val cloudDark2: Color,
    val cloudMedium1: Color,
    val cloudMedium2: Color,
    val cloudLight1: Color,
    val cloudLight2: Color,
    val rain: Color,
    val snow: Color,
    val hail: Color,
    val fog: Color,
    val wind: Color,
    val windDark: Color,
    val lightningBolt: Color,
    val hot: Color,
    val cold: Color,
)

object WeatherIconDefaults {
    @Composable
    fun colors(): WeatherIconColors {
        val darkTheme = LocalDarkTheme.current

        if (darkTheme) {
            return darkColors()
        } else {
            return lightColors()
        }
    }

    @Composable
    fun invertedColors(): WeatherIconColors {
        val darkTheme = LocalDarkTheme.current

        if (!darkTheme) {
            return darkColors()
        } else {
            return lightColors()
        }
    }

    @Composable
    fun darkColors(): WeatherIconColors {
        val context = LocalContext.current

        val themeColor = MaterialTheme.colorScheme.primary

        val neutral1 = MaterialTheme.colorScheme.outline
        val neutral2 = MaterialTheme.colorScheme.outline

        return remember(themeColor) {
            WeatherIconColors(
                sun = harmonize(0xFFFFB300.toInt(), themeColor),
                moon = harmonize(0xFF9E9E9E.toInt(), themeColor),
                cloudDark1 = harmonize(neutral1.atTone(40).toArgb(), themeColor),
                cloudDark2 = harmonize(neutral1.atTone(30).toArgb(), themeColor),
                cloudMedium1 = harmonize(neutral1.atTone(60).toArgb(), themeColor),
                cloudMedium2 = harmonize(neutral1.atTone(50).toArgb(), themeColor),
                cloudLight1 = harmonize(neutral1.atTone(95).toArgb(), themeColor),
                cloudLight2 = harmonize(neutral1.atTone(85).toArgb(), themeColor),
                rain = harmonize(0xFF64B5F6.toInt(), themeColor),
                snow = harmonize(0xFFF5F5F5.toInt(), themeColor),
                hail = harmonize(0xFFF5F5F5.toInt(), themeColor),
                fog = harmonize(neutral1.atTone(95).toArgb(), themeColor),
                wind = harmonize(neutral2.atTone(70).toArgb(), themeColor),
                windDark = harmonize(neutral2.atTone(40).toArgb(), themeColor),
                lightningBolt = harmonize(0xFFFFB300.toInt(), themeColor),
                hot = harmonize(0xFFE57373.toInt(), themeColor),
                cold = harmonize(0xFF4FC3F7.toInt(), themeColor),
            )
        }
    }

    @Composable
    fun lightColors(): WeatherIconColors {
        val context = LocalContext.current

        val themeColor = MaterialTheme.colorScheme.primary
        val neutral1 = MaterialTheme.colorScheme.outline
        val neutral2 = MaterialTheme.colorScheme.outline

        return remember(themeColor) {
            WeatherIconColors(
                sun = harmonize(0xFFFFB300.toInt(), themeColor),
                moon = harmonize(0xFF9E9E9E.toInt(), themeColor),
                cloudDark1 = harmonize(neutral1.atTone(30).toArgb(), themeColor),
                cloudDark2 = harmonize(neutral1.atTone(20).toArgb(), themeColor),
                cloudMedium1 = harmonize(neutral1.atTone(50).toArgb(), themeColor),
                cloudMedium2 = harmonize(neutral1.atTone(40).toArgb(), themeColor),
                cloudLight1 = harmonize(neutral1.atTone(85).toArgb(), themeColor),
                cloudLight2 = harmonize(neutral1.atTone(75).toArgb(), themeColor),
                rain = harmonize(0xFF1E88E5.toInt(), themeColor),
                snow = harmonize(0xFFE0E0E0.toInt(), themeColor),
                hail = harmonize(0xFFE0E0E0.toInt(), themeColor),
                fog = harmonize(neutral1.atTone(85).toArgb(), themeColor),
                wind = harmonize(neutral2.atTone(75).toArgb(), themeColor),
                windDark = harmonize(neutral2.atTone(45).toArgb(), themeColor),
                lightningBolt = harmonize(0xFFFFB300.toInt(), themeColor),
                hot = harmonize(0xFFE53935.toInt(), themeColor),
                cold = harmonize(0xFF039BE5.toInt(), themeColor),
            )
        }
    }

    @Composable
    fun monochromeColors(baseColor: Color): WeatherIconColors {
        val baseTone = Hct.fromInt(baseColor.toArgb()).tone.toInt()

        return remember(baseColor) {
            /**
             * Adjusts the tone of a color.
             * The tone is adjusted up or down to not overlap with the base color's tone.
             *
             * @param tone The tone to adjust.
             * @return The adjusted tone.
             */
            fun t(tone: Int): Int {
                val upper = (baseTone + 15f).coerceAtMost(100f)
                val lower = (baseTone - 15f).coerceAtLeast(0f)
                val diff = upper - lower
                val scale = (100f - diff) / 100f

                val tn = tone * scale

                if (tn > lower) {
                    return (tn + diff).toInt()
                }
                return tn.toInt()
            }

            WeatherIconColors(
                sun = baseColor.atTone(t(90)),
                moon = baseColor.atTone(t(90)),
                cloudDark1 = baseColor.atTone(t(25)),
                cloudDark2 = baseColor.atTone(t(15)),
                cloudMedium1 = baseColor.atTone(t(45)),
                cloudMedium2 = baseColor.atTone(t(35)),
                cloudLight1 = baseColor.atTone(t(65)),
                cloudLight2 = baseColor.atTone(t(55)),
                rain = baseColor.atTone(t(55)),
                snow = baseColor.atTone(t(100)),
                hail = baseColor.atTone(t(90)),
                fog = baseColor.atTone(t(60)),
                wind = baseColor.atTone(t(65)),
                windDark = baseColor.atTone(t(45)),
                lightningBolt = baseColor.atTone(t(90)),
                hot = baseColor.atTone(t(55)),
                cold = baseColor.atTone(t(55)),
            )
        }
    }
}

private fun harmonize(@ColorInt baseColor: Int, themeColor: Color): Color {
    return Color(Blend.harmonize(baseColor, themeColor.toArgb()))
}
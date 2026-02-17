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
import hct.Hct


data class WeatherIconColors(
    val sun: Color,
    val moon: Color,
    val cloud1: Color,
    val cloud2: Color,
    val cloud3: Color,
    val cloud4: Color,
    val cloud5: Color,
    val rain: Color,
    val snow: Color,
    val hail: Color,
    val fog: Color,
    val wind: Color,
    val lightningBolt: Color,
    val hot: Color,
    val cold: Color,
)

object WeatherIconDefaults {

    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.surface
    ): WeatherIconColors {
        val themeColor = MaterialTheme.colorScheme.primary
        val neutralColor = MaterialTheme.colorScheme.outline

        return remember(background, neutralColor, themeColor) {
            val baseTone = Hct.fromInt(background.toArgb()).tone.toInt()

            WeatherIconColors(
                sun = harmonize(0xfff7ae00.toInt(), themeColor).atTone(t(baseTone, 75)),
                moon = neutralColor.atTone(t(baseTone, 90)),
                cloud1 = neutralColor.atTone(t(baseTone, 20)),
                cloud2 = neutralColor.atTone(t(baseTone, 35)),
                cloud3 = neutralColor.atTone(t(baseTone, 50)),
                cloud4 = neutralColor.atTone(t(baseTone, 65)),
                cloud5 = neutralColor.atTone(t(baseTone, 90)),
                rain = harmonize(0xff1c71d8.toInt(), themeColor).atTone(t(baseTone, 50)),
                snow = neutralColor.atTone(t(baseTone, 100)),
                hail = neutralColor.atTone(t(baseTone, 90)),
                fog = neutralColor.atTone(t(baseTone, 60)),
                wind = neutralColor.atTone(t(baseTone, 65)),
                lightningBolt = harmonize(0xfff7ae00.toInt(), themeColor).atTone(t(baseTone, 80)),
                hot = harmonize(0xffec3435.toInt(), themeColor).atTone(t(baseTone, 50)),
                cold = harmonize(0xff1c71d8.toInt(), themeColor).atTone(t(baseTone, 50)),
            )
        }
    }

    @Composable
    fun themedColors(backgroundColor: Color): WeatherIconColors {

        val themeColor = MaterialTheme.colorScheme.primary
        val neutralColor = MaterialTheme.colorScheme.outline

        return remember(backgroundColor, themeColor, neutralColor) {
            val baseTone = Hct.fromInt(backgroundColor.toArgb()).tone.toInt()

            WeatherIconColors(
                sun = themeColor.atTone(t(baseTone, 75)),
                moon = neutralColor.atTone(t(baseTone, 90)),
                cloud1 = neutralColor.atTone(t(baseTone, 20)),
                cloud2 = neutralColor.atTone(t(baseTone, 35)),
                cloud3 = neutralColor.atTone(t(baseTone, 50)),
                cloud4 = neutralColor.atTone(t(baseTone, 65)),
                cloud5 = neutralColor.atTone(t(baseTone, 90)),
                rain = themeColor.atTone(t(baseTone, 55)),
                snow = neutralColor.atTone(t(baseTone, 100)),
                hail = neutralColor.atTone(t(baseTone, 90)),
                fog = neutralColor.atTone(t(baseTone, 60)),
                wind = neutralColor.atTone(t(baseTone, 65)),
                lightningBolt = themeColor.atTone(t(baseTone, 80)),
                hot = themeColor.atTone(t(baseTone, 50)),
                cold = themeColor.atTone(t(baseTone, 50)),
            )
        }
    }

    private fun t(base: Int, tone: Int): Int {
        val upper = (base + 15f).coerceAtMost(100f)
        val lower = (base - 15f).coerceAtLeast(0f)
        val diff = upper - lower
        val scale = (100f - diff) / 100f

        val tn = tone * scale

        if (tn > lower) {
            return (tn + diff).toInt()
        }
        return tn.toInt()
    }
}

private fun harmonize(@ColorInt baseColor: Int, themeColor: Color): Color {
    return Color(Blend.harmonize(baseColor, themeColor.toArgb()))
}
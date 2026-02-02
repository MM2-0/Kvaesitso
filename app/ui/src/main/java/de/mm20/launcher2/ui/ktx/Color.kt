package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hct.Hct
import kotlin.math.atan2
import kotlin.math.roundToInt

fun Color.toHexString(): String {
    val red = (this.red * 255).roundToInt()
    val green = (this.green * 255).roundToInt()
    val blue = (this.blue * 255).roundToInt()
    return "#" +
            red.toString(16).run { if (length == 1) "0$this" else this } +
            green.toString(16).run { if (length == 1) "0$this" else this } +
            blue.toString(16).run { if (length == 1) "0$this" else this }
}

fun Color.Companion.hct(hue: Float, chroma: Float, tone: Float): Color {
    val hct = Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble())
    return Color(hct.toInt())
}

fun Color.atTone(tone: Int): Color {
    return Color(
        Hct.fromInt(this.toArgb()).apply {
            this.setTone(tone.toDouble())
        }.toInt()
    )
}

val Color.hue: Float
    get() {
        val r = this.red / 255f
        val g = this.green / 255f
        val b = this.blue / 255f
        // sqrt(3)
        return atan2(1.7320508f * (g - b), 2f * r - g - b)
    }

fun android.graphics.Color.toComposeColor() = Color(this.toArgb())
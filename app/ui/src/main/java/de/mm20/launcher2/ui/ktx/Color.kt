package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.graphics.Color
import hct.Hct
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

val Color.hue: Float
    get() {
        val R = this.red / 255f
        val G = this.green / 255f
        val B = this.blue / 255f
        return atan2(sqrt(3f) * (G - B), 2 * R - G - B)
    }

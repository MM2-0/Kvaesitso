package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.graphics.Color
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
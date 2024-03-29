package de.mm20.launcher2.ktx

import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

fun Int.isBrightColor(): Boolean {
    val darkness = 1 - (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    return darkness < 0.5
}

val Int.sat : Float
    get() {
        FloatArray(3).also {
            ColorUtils.RGBToHSL(red, green, blue, it)
            return it[1]
        }
    }

package de.mm20.launcher2.ktx

import kotlin.math.PI
import kotlin.math.ceil

fun Float.ceilToInt(): Int {
    return ceil(this).toInt()
}

private const val TWO_PI_F = (2.0 * PI).toFloat()
val Float.Companion.TWO_PI: Float
    get() = TWO_PI_F

private const val PI_F = PI.toFloat()
val Float.Companion.PI: Float
    get() = PI_F

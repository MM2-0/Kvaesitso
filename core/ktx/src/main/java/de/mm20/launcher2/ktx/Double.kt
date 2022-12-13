package de.mm20.launcher2.ktx

import kotlin.math.ceil

fun Double.ceilToInt(): Int {
    return ceil(this).toInt()
}

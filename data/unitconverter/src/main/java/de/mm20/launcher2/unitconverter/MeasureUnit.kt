package de.mm20.launcher2.unitconverter

import android.content.Context

interface MeasureUnit {
    val symbol: String
    fun formatName(context: Context, value: Double): String
}

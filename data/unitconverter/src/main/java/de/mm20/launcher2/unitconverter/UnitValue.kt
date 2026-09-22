package de.mm20.launcher2.unitconverter

data class UnitValue(
        val value: Double,
        val symbol: String,
        val formattedName: String,
        val formattedValue: String
)

internal fun Double.formatValue(): String {
    if (kotlin.math.abs(this) > 1e5 || kotlin.math.abs(this) < 1e-3) {
        return java.text.DecimalFormat("#.###E0").format(this)
    }
    return java.text.DecimalFormat("#.###").format(this)
}
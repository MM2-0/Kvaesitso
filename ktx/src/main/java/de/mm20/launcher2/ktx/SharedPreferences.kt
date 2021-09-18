package de.mm20.launcher2.ktx

import android.content.SharedPreferences

fun SharedPreferences.Editor.putDouble(key: String, value: Double) {
    putLong(key, value.toBits())
}

fun SharedPreferences.getDouble(key: String): Double? {
    if (contains(key)) return Double.fromBits(getLong(key, 0))
    return null
}

fun SharedPreferences.getDouble(key: String, defValue: Double): Double {
    if (contains(key)) return Double.fromBits(getLong(key, 0))
    return defValue
}
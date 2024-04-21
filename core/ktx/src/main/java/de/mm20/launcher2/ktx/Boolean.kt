package de.mm20.launcher2.ktx

inline fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}
package de.mm20.launcher2.ktx

inline fun <T> T?.or(block: () -> T?): T? = this ?: block()

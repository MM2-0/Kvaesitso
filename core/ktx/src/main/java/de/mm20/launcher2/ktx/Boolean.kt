package de.mm20.launcher2.ktx

fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

fun <T> Boolean.fold(
    whenTrue: () -> T,
    otherwise: () -> T
): T = if (this) whenTrue() else otherwise()

fun <T> Boolean.foldOrNull(
    whenTrue: () -> T
): T? = fold(whenTrue) { null }

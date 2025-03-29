package de.mm20.launcher2.ktx

import java.util.*

fun <T> List<T>.randomElement(): T {
    if (isEmpty()) throw IndexOutOfBoundsException("List is empty")
    return get(Random().nextInt(size))
}

fun <T> List<T>.randomElementOrNull(): T? {
    if (isEmpty()) return null
    return get(Random().nextInt(size))
}

fun <T> List<T>?.ifNullOrEmpty(block: () -> List<T>): List<T> {
    return if (this.isNullOrEmpty()) block() else this
}

fun <T> List<T>.distinctByEquality(equalityPredicate: (T, T) -> Boolean): List<T> {
    if (size < 2) return this

    val ret = mutableListOf<T>()

    for (item in this) {
        if (ret.none { equalityPredicate(it, item) }) ret.add(item)
    }

    return ret
}

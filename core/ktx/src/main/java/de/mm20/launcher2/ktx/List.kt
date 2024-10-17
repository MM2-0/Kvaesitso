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

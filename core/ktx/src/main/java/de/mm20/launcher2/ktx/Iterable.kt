package de.mm20.launcher2.ktx

inline fun <T, R : Comparable<R>> Iterable<T>.pseudoMedianBy(crossinline selector: (T) -> R): T? {
    val count = this.count()

    if (count == 0)
        return null

    return this.sortedBy { selector(it) } [ count / 2 ]
}
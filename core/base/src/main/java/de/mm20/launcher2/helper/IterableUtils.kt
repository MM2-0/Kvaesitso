package de.mm20.launcher2.helper

object IterableUtils {
    inline fun <T, R : Comparable<R>> List<T>.medianBy(crossinline selector: (T) -> R?): T? {
        if (this.isEmpty())
            return null

        return this.sortedBy { selector(it) } [ this.size / 2 ]
    }
}
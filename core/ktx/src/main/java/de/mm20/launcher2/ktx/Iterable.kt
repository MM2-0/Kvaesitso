package de.mm20.launcher2.ktx

fun <T,V> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<V>?) : List<V> {
    val destination = mutableListOf<V>()
    for (it in this) {
        val transformed = transform(it) ?: continue
        destination.addAll(transformed)
    }
    return destination
}

/**
 * Converts an Iterable of pairs into a Map where the keys are the first elements of the pairs
 * and the values are lists of the second elements of the pairs.
 *
 * For example, given the input:
 * ```
 * val pairs = listOf(
 *    "a" to 1,
 *    "b" to 2,
 *    "a" to 3,
 *    "c" to 4,
 * )
 * ```
 * the output will be:
 * ```
 * mapOf(
 *   "a" to listOf(1, 3),
 *   "b" to listOf(2),
 *   "c" to listOf(4),
 * )
 * ```
 */
fun <T,V> Iterable<Pair<T, V>>.toMultiMap() : Map<T, List<V>> {
    val destination = mutableMapOf<T, MutableList<V>>()
    for ((k, v) in this) {
        destination.getOrPut(k) { mutableListOf() } += v
    }
    return destination
}

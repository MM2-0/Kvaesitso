package de.mm20.launcher2.ktx

fun <T,V> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<V>?) : List<V> {
    val destination = mutableListOf<V>()
    for (it in this) {
        val transformed = transform(it) ?: continue
        destination.addAll(transformed)
    }
    return destination
}

fun <T,V> Iterable<Pair<T, V>>.toMultiMap() : Map<T, List<V>> {
    val destination = mutableMapOf<T, MutableList<V>>()
    for ((k, v) in this) {
        destination.getOrPut(k) { mutableListOf() } += v
    }
    return destination
}

package de.mm20.launcher2.ktx

fun <A, B> Pair<A, A>.map(transform: (A) -> B): Pair<B, B> = transform(first) to transform(second)

fun <A, B, C> Pair<A, B>.into(transform: (A, B) -> C): C = transform(first, second)

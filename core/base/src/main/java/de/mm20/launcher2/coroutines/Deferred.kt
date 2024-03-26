package de.mm20.launcher2.coroutines

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun <T> deferred(block: suspend () -> T): Deferred<T> {
    val deferred = CompletableDeferred<T>()
    return object : Deferred<T> by deferred {
        private val mutex = Mutex()
        override suspend fun await(): T {
            mutex.withLock {
                if (!deferred.isCompleted) {
                    block().also { deferred.complete(it) }
                }
            }
            return deferred.await()
        }
    }
}

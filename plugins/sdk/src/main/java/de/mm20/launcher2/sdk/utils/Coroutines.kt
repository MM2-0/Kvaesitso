package de.mm20.launcher2.sdk.utils

import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

internal fun <T> launchWithCancellationSignal(
    cancellationSignal: CancellationSignal?,
    block: suspend CoroutineScope.() -> T
): T {
    return runBlocking {
        val deferred = async(block = block)
        cancellationSignal?.setOnCancelListener {
            deferred.cancel()
        }
        deferred.await()
    }
}

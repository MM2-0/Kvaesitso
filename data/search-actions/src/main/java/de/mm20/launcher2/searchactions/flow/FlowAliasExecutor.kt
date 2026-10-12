package de.mm20.launcher2.searchactions.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** Shared across result instances so rebuilding a query cannot bypass the execution guard. */
class FlowAliasExecutor {
    enum class Result { Launched, Unavailable, Failed, Busy }
    private val mutex = Mutex()

    suspend fun activate(
        alias: FlowAlias,
        resolve: suspend (String) -> FlowAliasTarget?,
        launch: suspend (FlowAliasTarget) -> Boolean,
    ): Result {
        if (!mutex.tryLock()) return Result.Busy
        try {
            currentCoroutineContext().ensureActive()
            if (FlowAliases.validate(alias) != null) return Result.Unavailable
            val target = resolve(alias.targetKey)
            if (target == null || target.key != alias.targetKey || !target.eligible) return Result.Unavailable
            currentCoroutineContext().ensureActive()
            return if (launch(target)) Result.Launched else Result.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.Failed
        } finally {
            mutex.unlock()
        }
    }
}

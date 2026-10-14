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
        launchBatch: (suspend (List<FlowAliasTarget>) -> Boolean)? = null,
    ): Result {
        if (!mutex.tryLock()) return Result.Busy
        try {
            currentCoroutineContext().ensureActive()
            if (FlowAliases.validate(alias) != null) return Result.Unavailable
            val targets = alias.targetKeys.map { key ->
                val target = resolve(key)
                if (target == null || target.key != key || !target.eligible) return Result.Unavailable
                target
            }
            currentCoroutineContext().ensureActive()
            // No partial dispatch when a caller has not supplied an ordered platform adapter.
            if (targets.size > 1 && launchBatch == null) return Result.Unavailable
            return if (if (targets.size == 1) launch(targets.single()) else launchBatch!!(targets)) Result.Launched else Result.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.Failed
        } finally {
            mutex.unlock()
        }
    }
}

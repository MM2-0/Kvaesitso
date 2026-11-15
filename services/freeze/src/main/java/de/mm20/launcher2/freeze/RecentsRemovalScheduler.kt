package de.mm20.launcher2.freeze

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Owned by the callback consumer; workers only read snapshots and never mutate its task index. */
internal class RecentsRemovalScheduler(
    private val scope: CoroutineScope,
    private val userId: Int,
    private val enabled: suspend () -> Boolean,
    private val snapshot: () -> List<RecentsTask>?,
    private val onRemoval: suspend (String, suspend () -> Boolean) -> Unit,
    private val observationLost: () -> Unit,
    private val debounceMillis: Long = 750,
    private val retryMillis: Long = 500,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val slots = Semaphore(4)
    private val session = AtomicLong(0)

    fun reset() {
        session.incrementAndGet()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun submit(removed: RecentsTask): Boolean {
        if (removed.userId != userId) return true
        jobs.entries.removeAll { it.value.isCompleted }
        jobs.remove(removed.packageName)?.cancel()
        if (jobs.size >= 256) return false
        val generation = session.get()
        jobs[removed.packageName] = scope.launch {
            try {
                delay(debounceMillis)
                slots.withPermit {
                    suspend fun stillRemoved(): Boolean {
                        if (generation != session.get() || !enabled()) return false
                        val latest =
                            snapshot()
                                ?: run {
                                    observationLost()
                                    return false
                                }
                        return generation == session.get() &&
                            latest.none {
                                it.userId == userId &&
                                    (it.packageName == removed.packageName || it.id == removed.id)
                            }
                    }
                    repeat(4) { attempt ->
                        if (generation != session.get() || !enabled()) return@launch
                        val remaining =
                            snapshot()
                                ?: run {
                                    observationLost()
                                    return@launch
                                }
                        if (remaining.any { it.id == removed.id && it.userId == removed.userId }) {
                            if (attempt < 3) delay(retryMillis)
                        } else {
                            if (removedLastTask(removed, remaining, userId)) {
                                onRemoval(removed.packageName, ::stillRemoved)
                            }
                            return@launch
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                observationLost()
            }
        }
        return true
    }
}

package de.mm20.launcher2.freeze

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class RecentsRemovalSchedulerTest {
    @Test
    fun clearAllIsBoundedAndDoesNotSerializeTheAnimationDelay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val completed = AtomicInteger()
        val scheduler =
            RecentsRemovalScheduler(
                scope,
                0,
                { true },
                { emptyList() },
                { _, proof ->
                    val count = active.incrementAndGet()
                    maximum.updateAndGet { maxOf(it, count) }
                    delay(40)
                    if (proof()) completed.incrementAndGet()
                    active.decrementAndGet()
                },
                { error("Unexpected observation loss") },
                debounceMillis = 10,
                retryMillis = 5,
            )
        try {
            repeat(12) { assertTrue(scheduler.submit(RecentsTask(it, "com.test.app$it", 0))) }
            withTimeout(2000) { while (completed.get() < 12) delay(5) }
            assertTrue(maximum.get() in 2..4)
        } finally {
            scheduler.reset()
            scope.cancel()
        }
    }

    @Test
    fun reopeningDuringExclusionChecksInvalidatesTheFinalProof() = runBlocking {
        val tasks = MutableStateFlow<List<RecentsTask>>(emptyList())
        val entered = CompletableDeferred<Unit>()
        val continueCheck = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Boolean>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler =
            RecentsRemovalScheduler(
                scope,
                0,
                { true },
                { tasks.value },
                { _, proof ->
                    entered.complete(Unit)
                    continueCheck.await()
                    result.complete(proof())
                },
                {},
                debounceMillis = 1,
                retryMillis = 1,
            )
        try {
            scheduler.submit(RecentsTask(1, "com.test.app", 0))
            withTimeout(2000) { entered.await() }
            tasks.value = listOf(RecentsTask(2, "com.test.app", 0))
            continueCheck.complete(Unit)
            assertFalse(withTimeout(2000) { result.await() })
        } finally {
            scheduler.reset()
            scope.cancel()
        }
    }

    @Test
    fun disconnectInvalidatesEvenAnAlreadyCapturedProof() = runBlocking {
        val proof = CompletableDeferred<suspend () -> Boolean>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler =
            RecentsRemovalScheduler(
                scope,
                0,
                { true },
                { emptyList() },
                { _, check ->
                    proof.complete(check)
                },
                {},
                debounceMillis = 1,
            )
        try {
            scheduler.submit(RecentsTask(1, "com.test.app", 0))
            val captured = withTimeout(2000) { proof.await() }
            scheduler.reset()
            assertFalse(captured())
        } finally {
            scheduler.reset()
            scope.cancel()
        }
    }

    @Test
    fun anotherTaskOrProfileCannotBeMistakenForLastTaskRemoval() = runBlocking {
        val calls = AtomicInteger()
        val tasks = MutableStateFlow(listOf(RecentsTask(2, "com.test.app", 0)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler =
            RecentsRemovalScheduler(
                scope,
                0,
                { true },
                { tasks.value },
                { _, check ->
                    if (check()) calls.incrementAndGet()
                },
                {},
                debounceMillis = 1,
                retryMillis = 1,
            )
        try {
            scheduler.submit(RecentsTask(1, "com.test.app", 0))
            scheduler.submit(RecentsTask(3, "com.test.work", 10))
            delay(50)
            assertEquals(0, calls.get())
            tasks.value = emptyList()
            scheduler.submit(RecentsTask(2, "com.test.app", 0))
            withTimeout(2000) { while (calls.get() == 0) delay(5) }
            assertEquals(1, calls.get())
        } finally {
            scheduler.reset()
            scope.cancel()
        }
    }

    @Test
    fun unknownSnapshotFailsClosed() = runBlocking {
        val lost = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler =
            RecentsRemovalScheduler(
                scope,
                0,
                { true },
                { null },
                { _, _ -> error("Must not freeze") },
                { lost.complete(Unit) },
                debounceMillis = 1,
            )
        try {
            scheduler.submit(RecentsTask(1, "com.test.app", 0))
            withTimeout(2000) { lost.await() }
        } finally {
            scheduler.reset()
            scope.cancel()
        }
    }
}

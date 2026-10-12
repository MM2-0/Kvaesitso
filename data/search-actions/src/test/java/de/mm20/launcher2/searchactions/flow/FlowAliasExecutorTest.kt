package de.mm20.launcher2.searchactions.flow

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FlowAliasExecutorTest {
    private val alias = FlowAlias("one", "vlak", "app://train:Main")
    private val target = FlowAliasTarget(alias.targetKey, "Train")

    @Test fun matchingHasNoSideEffectsAndActivationLaunchesExactTarget() = runBlocking {
        var launches = 0
        repeat(100) { assertTrue(FlowAliases.matches(alias, "VLAK")) }
        assertEquals(0, launches)
        assertEquals(FlowAliasExecutor.Result.Launched, FlowAliasExecutor().activate(alias,
            resolve = { assertEquals(alias.targetKey, it); target },
            launch = { assertEquals(target, it); launches++; true }))
        assertEquals(1, launches)
    }

    @Test fun unavailableHiddenSuspendedPrivateAndWrongProfileNeverLaunch() = runBlocking {
        for (resolved in listOf(null, target.copy(hidden = true), target.copy(suspended = true),
            target.copy(privateProfile = true), target.copy(personal = false), target.copy(key = "app://other:Main"))) {
            assertEquals(FlowAliasExecutor.Result.Unavailable, FlowAliasExecutor().activate(alias,
                resolve = { resolved }, launch = { fail("Ineligible target launched"); true }))
        }
    }

    @Test fun concurrentActivationsDoNotLaunchTwice() = runBlocking {
        val executor = FlowAliasExecutor()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var launches = 0
        val first = async {
            executor.activate(alias, { entered.complete(Unit); release.await(); target }, { launches++; true })
        }
        entered.await()
        assertEquals(FlowAliasExecutor.Result.Busy, executor.activate(alias, { target }, { launches++; true }))
        release.complete(Unit)
        assertEquals(FlowAliasExecutor.Result.Launched, first.await())
        assertEquals(1, launches)
    }

    @Test fun cancellationPropagatesAndReleasesGuard() = runBlocking {
        val executor = FlowAliasExecutor()
        val entered = CompletableDeferred<Unit>()
        val first = launch { executor.activate(alias, { entered.complete(Unit); awaitCancellation() }, { fail("Cancelled launch"); true }) }
        entered.await()
        first.cancelAndJoin()
        assertEquals(FlowAliasExecutor.Result.Launched, executor.activate(alias, { target }, { true }))
    }

    @Test fun cancellationBeforeDispatchPreventsLaunchEvenIfResolverReturns() = runBlocking {
        val executor = FlowAliasExecutor()
        var launches = 0
        val job = launch {
            executor.activate(alias, {
                currentCoroutineContext().cancel()
                target
            }, { launches++; true })
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, launches)
        assertEquals(FlowAliasExecutor.Result.Launched, executor.activate(alias, { target }, { true }))
    }

    @Test fun removedTargetsAndFailedLaunchesDoNotFallBack() = runBlocking {
        val executor = FlowAliasExecutor()
        assertEquals(FlowAliasExecutor.Result.Unavailable, executor.activate(alias, { null }, { fail("Removed target"); true }))
        assertEquals(FlowAliasExecutor.Result.Failed, executor.activate(alias, { target }, { false }))
        assertEquals(FlowAliasExecutor.Result.Failed, executor.activate(alias, { throw SecurityException() }, { true }))
        assertEquals(FlowAliasExecutor.Result.Launched, executor.activate(alias, { target }, { true }))
    }
}

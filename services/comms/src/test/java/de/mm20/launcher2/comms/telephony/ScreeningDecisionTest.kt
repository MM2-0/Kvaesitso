package de.mm20.launcher2.comms.telephony

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ScreeningDecisionTest {
    @Test
    fun timeoutWinsAndLateBlockCannotChangeTheAnsweredCall() {
        val answers = mutableListOf<Boolean>()
        val decision = ScreeningDecision { answers += it }
        decision.complete(false)
        decision.complete(true)
        decision.complete(false)
        assertEquals(listOf(false), answers)
    }

    @Test
    fun normalBlockWinsAndCleanupCannotAllowItAgain() {
        val answers = mutableListOf<Boolean>()
        val decision = ScreeningDecision { answers += it }
        decision.complete(true)
        decision.complete(false)
        assertEquals(listOf(true), answers)
    }

    @Test
    fun simultaneousDeadlineAndLookupRespondOnlyOnce() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val answers = AtomicInteger()
                val start = CountDownLatch(1)
                val finish = CountDownLatch(2)
                val decision = ScreeningDecision { answers.incrementAndGet() }
                for (block in listOf(true, false)) pool.execute {
                    start.await()
                    decision.complete(block)
                    finish.countDown()
                }
                start.countDown()
                assertTrue(finish.await(5, TimeUnit.SECONDS))
                assertEquals(1, answers.get())
            }
        } finally {
            pool.shutdownNow()
        }
    }
}

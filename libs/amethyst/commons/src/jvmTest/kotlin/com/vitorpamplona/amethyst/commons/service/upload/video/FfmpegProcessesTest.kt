/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.service.upload.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real runner against a real child process; no ffmpeg involved. Real time, so no `runTest`. */
class FfmpegProcessesTest {
    private val isWindows = FfmpegTestSupport.isWindows

    // Not `timeout /t`: it exits at once when stdin is redirected, which the runner always does.
    private fun sleep30(): List<String> = if (isWindows) listOf("powershell", "-NoProfile", "-Command", "Start-Sleep", "-Seconds", "30") else listOf("sleep", "30")

    private fun shell(script: String): List<String> = if (isWindows) listOf("cmd", "/c", script.replace(";", " &")) else listOf("sh", "-c", script)

    @Test
    fun stalledChildIsKilledAndUnregistered() =
        runBlocking {
            val started = System.currentTimeMillis()
            val outcome = FfmpegProcesses.run(sleep30(), {}, stallTimeoutMs = 200)
            assertEquals(-1, outcome.exitCode)
            assertTrue(System.currentTimeMillis() - started < 5_000, "killed within a few seconds")
            assertTrue(FfmpegProcesses.live.isEmpty())
        }

    @Test
    fun exitAndStderrAreForwarded() =
        runBlocking {
            val out = mutableListOf<String>()
            val outcome = FfmpegProcesses.run(shell("echo out; echo err 1>&2; exit 3"), { out += it }, 5_000)
            assertEquals(3, outcome.exitCode)
            assertEquals("err", outcome.stderrTail.trim())
            assertEquals(listOf("out"), out.map { it.trim() })
        }

    @Test
    fun shutdownRefusesNewProcessesAsCancellation() =
        runBlocking {
            try {
                FfmpegProcesses.shutdownNow()
                assertFailsWith<CancellationException> { FfmpegProcesses.run(sleep30(), {}, 60_000) }
                assertTrue(FfmpegProcesses.live.isEmpty())
            } finally {
                FfmpegProcesses.shuttingDown = false
            }
        }

    @Test
    fun shutdownKillsARunningChildAsCancellationNotFailure() =
        runBlocking {
            try {
                var outcome: Result<ProcessOutcome>? = null
                val job = launch { outcome = runCatching { FfmpegProcesses.run(sleep30(), {}, 60_000) } }
                delay(300)
                FfmpegProcesses.shutdownNow()
                job.join()
                assertTrue(outcome!!.exceptionOrNull() is CancellationException, "a shutdown kill must not read as an encoder failure: $outcome")
                assertTrue(FfmpegProcesses.live.isEmpty())
            } finally {
                FfmpegProcesses.shuttingDown = false
            }
        }

    @Test
    fun cancellationKillsTheChild() =
        runBlocking {
            val job = launch { FfmpegProcesses.run(sleep30(), {}, 60_000) }
            delay(100)
            job.cancel()
            job.join()
            assertTrue(FfmpegProcesses.live.isEmpty())
        }

    @Test
    fun aFailureWhileWaitingStillKillsTheChild() =
        runBlocking {
            if (isWindows) return@runBlocking
            val job = launch { runCatching { FfmpegProcesses.run(sleep30(), {}, 60_000) } }
            while (FfmpegProcesses.live.isEmpty()) delay(10)
            val child = FfmpegProcesses.live.single()
            // Interrupt the thread blocked in waitFor: an InterruptedException, a failure that is no cancellation.
            var waiter: Thread? = null
            while (waiter == null) {
                waiter =
                    Thread
                        .getAllStackTraces()
                        .entries
                        .firstOrNull { (_, stack) ->
                            stack.any { it.methodName == "waitFor" } && stack.any { it.className.startsWith(FfmpegProcesses::class.java.name) }
                        }?.key
                if (waiter == null) delay(10)
            }
            waiter.interrupt()
            job.join()
            assertFalse(child.isAlive, "the child must not outlive a failed wait")
            assertTrue(FfmpegProcesses.live.isEmpty())
        }
}

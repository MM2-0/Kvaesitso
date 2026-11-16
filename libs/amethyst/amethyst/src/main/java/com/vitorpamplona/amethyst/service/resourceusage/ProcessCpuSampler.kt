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
package com.vitorpamplona.amethyst.service.resourceusage

import android.os.Process
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys

/**
 * Samples whole-process CPU time (user+system, [Process.getElapsedCpuTime])
 * into the ledger as day deltas. This is the honest aggregate cost of
 * everything that runs on the CPU — event parsing, signature verification,
 * coroutines, recomposition — without per-subsystem guesswork. Sampled from
 * the accountant's pre-flush hook, so it costs one syscall per flush and
 * nothing while idle. Per-process monotonic: a fresh process simply starts a
 * fresh baseline.
 */
class ProcessCpuSampler(
    private val accountant: ResourceUsageAccountant,
    private val cpuMs: () -> Long = { Process.getElapsedCpuTime() },
) {
    private var lastSampleMs = cpuMs()

    fun register() {
        accountant.addPreFlushHook(::sample)
    }

    @Synchronized
    fun sample() {
        val now = cpuMs()
        val delta = now - lastSampleMs
        lastSampleMs = now
        if (delta > 0) accountant.add(UsageKeys.CPU_MS, delta)
    }
}

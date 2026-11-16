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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoWEstimatorLiveRateTest {
    @Test
    fun liveRateIsNullWhenIdleAndMeasuredWhileMining() =
        runBlocking {
            assertNull(PoWEstimator.liveHashesPerSecond(), "nothing mines, so there is no live rate")

            val stop = AtomicBoolean(false)
            val mining =
                CoroutineScope(Dispatchers.Default).launch {
                    runCatching {
                        PoWMiner.mine(
                            EventTemplate<TextNoteEvent>(1683596206, TextNoteEvent.KIND, emptyArray(), "A note to mine"),
                            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                            256,
                            workers = 2,
                            isActive = { !stop.get() },
                        )
                    }
                }
            try {
                while (!PoWMiner.isMining()) Thread.sleep(5)
                val live = assertNotNull(PoWEstimator.liveHashesPerSecond(), "a running miner has a live rate")
                assertTrue(live > 1_000.0, "live rate should be a real hash rate, got $live")
            } finally {
                stop.set(true)
                mining.join()
            }
        }
}

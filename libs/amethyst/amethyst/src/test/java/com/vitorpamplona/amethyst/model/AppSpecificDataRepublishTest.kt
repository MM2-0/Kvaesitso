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
package com.vitorpamplona.amethyst.model

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.nextCreatedAtToSupersede
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The settings pickers (Side Menu, bottom bar) republish the NIP-78 app-specific data event on every
 * discrete toggle, and `created_at` is whole seconds. These pin what that costs in LocalCache — the
 * failure the user sees as "my side-menu rows came back after a restart". The timestamp rule itself
 * is unit-tested in quartz, next to the supersession rule it exists to beat.
 */
class AppSpecificDataRepublishTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val dTag = "AmethystSettingsRepublishTest"

    private val address get() = AppSpecificDataEvent.createAddress(signer.pubKey, dTag)

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    @Test
    fun theWallClockAloneLosesAnEditMadeInTheSameSecond() =
        runBlocking {
            // Why the fix has to exist: LocalCache resolves a same-second tie the NIP-01 way — the
            // lowest id stays — so a republish in the same second lands only when its id happens to
            // sort lower, about half the time; otherwise it never reaches the collector that persists it.
            val sameSecond = TimeUtils.now()

            val first = signer.sign(AppSpecificDataEvent.build(dTag, "first", createdAt = sameSecond))
            val second = signer.sign(AppSpecificDataEvent.build(dTag, "second", createdAt = sameSecond))
            LocalCache.justConsumeMyOwnEvent(first)
            LocalCache.justConsumeMyOwnEvent(second)

            val kept = if (first.id < second.id) first else second
            assertEquals(kept.content, LocalCache.getOrCreateAddressableNote(address).event?.content)
        }

    @Test
    fun aStampedRepublishInTheSameSecondLands() =
        runBlocking {
            val sameSecond = TimeUtils.now()

            val first = signer.sign(AppSpecificDataEvent.build(dTag, "first", createdAt = nextCreatedAtToSupersede(0L, sameSecond)))
            LocalCache.justConsumeMyOwnEvent(first)

            val second =
                signer.sign(
                    AppSpecificDataEvent.build(
                        dTag,
                        "second",
                        createdAt = nextCreatedAtToSupersede(first.createdAt, sameSecond),
                    ),
                )
            LocalCache.justConsumeMyOwnEvent(second)

            assertEquals("second", LocalCache.getOrCreateAddressableNote(address).event?.content)
        }
}

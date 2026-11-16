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
package com.vitorpamplona.amethyst.commons.model.nip60Cashu

import com.vitorpamplona.amethyst.commons.cashu.ops.NutzapRedemption
import com.vitorpamplona.amethyst.commons.cashu.ops.RedeemCompleted
import com.vitorpamplona.amethyst.commons.cashu.ops.RedeemedNutzaps
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutzapRedeemSchedulerTest {
    private val mintA = "https://mint-a.example.com"
    private val mintB = "https://mint-b.example.com"
    private val accepted = setOf(mintA, mintB)
    private val signer = NostrSignerInternal(KeyPair())
    private var now = 1_000_000L
    private val scheduler = NutzapRedeemScheduler { now }

    private var counter = 0

    private fun nutzap(mint: String): NutzapEvent = runBlocking { signer.sign(NutzapEvent.buildToUser("zap ${counter++}", listOf("{}"), mint, "sat", signer.pubKey)) }

    private fun result(
        redeemed: List<NutzapEvent> = emptyList(),
        belowFee: List<NutzapEvent> = emptyList(),
        failed: List<NutzapEvent> = emptyList(),
    ) = NutzapRedemption(
        redeemed =
            if (redeemed.isEmpty()) {
                emptyList()
            } else {
                listOf(RedeemedNutzaps(redeemed, RedeemCompleted(1, mockk<CashuTokenEvent>(), mockk<CashuSpendingHistoryEvent>(), "")))
            },
        unredeemable = emptyMap(),
        belowFee = belowFee,
        notSaved = emptyList(),
        failed = failed.associateWith { IllegalStateException("mint down") },
    )

    @Test
    fun `nutzaps at a mint the wallet does not list are never selected`() {
        val ours = nutzap(mintA)
        val foreign = nutzap("http://127.0.0.1:8080")
        val noMint = nutzap("")

        assertEquals(listOf(ours), scheduler.select(listOf(ours, foreign, noMint), accepted))
    }

    @Test
    fun `the accepted list matches mint URLs by their normalized form`() {
        val zap = nutzap("https://MINT-A.example.com/")

        assertEquals(listOf(zap), scheduler.select(listOf(zap), setOf(mintA)))
    }

    @Test
    fun `a mint whose nutzaps are all below the fee waits for a new one, then goes whole`() {
        val small = List(3) { nutzap(mintA) }
        scheduler.record(result(belowFee = small))

        assertTrue(scheduler.select(small, accepted).isEmpty())

        val fresh = nutzap(mintA)
        assertEquals((small + fresh).toSet(), scheduler.select(small + fresh, accepted).toSet())
    }

    @Test
    fun `a failing mint backs off, doubling, while other mints carry on`() {
        val atA = nutzap(mintA)
        val atB = nutzap(mintB)

        val first = scheduler.record(result(failed = listOf(atA)))
        assertEquals(mapOf(mintA to 60_000L), first.backingOff)
        assertEquals(listOf(atB), scheduler.select(listOf(atA, atB), accepted))

        now += 60_000L
        assertEquals(listOf(atA, atB), scheduler.select(listOf(atA, atB), accepted))

        val second = scheduler.record(result(failed = listOf(atA)))
        assertEquals(mapOf(mintA to 120_000L), second.backingOff)
    }

    @Test
    fun `a success at the mint clears its backoff`() {
        val atA = nutzap(mintA)
        scheduler.record(result(failed = listOf(atA)))
        scheduler.record(result(redeemed = listOf(nutzap(mintA))))

        assertEquals(listOf(atA), scheduler.select(listOf(atA), accepted))
    }

    @Test
    fun `backoff is capped at an hour`() {
        assertEquals(3_600_000L, NutzapRedeemScheduler.backoffMs(50))
    }
}

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
package com.vitorpamplona.amethyst.commons.cashu

import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletQueryState
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.cashuInboundNutzapBackfillFilters
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.cashuOwnEventBackfillFilters
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.cashuProofBackfillFilters
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.cashuWalletFilters
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The NIP-60 balance is a pure function of the kind:7375 events the client
 * holds, and those arrive over a subscription a relay is free to truncate. This
 * pins both halves of that problem:
 *
 *  - what a truncated delivery does to the number the user sees, and
 *  - that the backfill filter is shaped so `fetchAllPages` can walk past the
 *    truncation instead of inheriting it.
 */
class CashuBalanceTruncationTest {
    private val owner: HexKey = "a".repeat(64)

    /**
     * Build a kind:7375 whose decrypted content is [content]. The event's own
     * `content` string is irrelevant here — [CashuWalletReader.computeUnspent]
     * is handed the already-decrypted map, exactly as the wallet holder does
     * after its NIP-44 pass.
     */
    private fun tokenEvent(
        idPrefix: String,
        createdAt: Long,
    ): CashuTokenEvent =
        CashuTokenEvent(
            id = idPrefix.padEnd(64, '0'),
            pubKey = owner,
            createdAt = createdAt,
            tags = emptyArray(),
            content = "",
            sig = "0".repeat(128),
        )

    private fun proofs(
        keysetId: String,
        vararg amounts: Long,
    ) = amounts.mapIndexed { i, amount ->
        CashuProof(id = keysetId, amount = amount, secret = "$keysetId-$i-$amount", c = "02${"0".repeat(64)}")
    }

    /**
     * Three mints, funded at different times: the oldest one holds most of the
     * money and hasn't been touched since. This is the shape of a real wallet —
     * activity concentrates on the mint you last used.
     */
    private fun wallet(): Pair<List<CashuTokenEvent>, Map<HexKey, TokenContent>> {
        val oldMint = tokenEvent("aa", createdAt = 1_000)
        val midMint = tokenEvent("bb", createdAt = 2_000)
        val hotMint = tokenEvent("cc", createdAt = 3_000)

        val contents =
            mapOf(
                oldMint.id to TokenContent(mint = "https://old.mint", proofs = proofs("ks1", 1024L, 459L)),
                midMint.id to TokenContent(mint = "https://mid.mint", proofs = proofs("ks2", 1000L)),
                hotMint.id to TokenContent(mint = "https://hot.mint", proofs = proofs("ks3", 32L, 7L)),
            )
        return listOf(oldMint, midMint, hotMint) to contents
    }

    private fun balanceOf(
        events: List<CashuTokenEvent>,
        contents: Map<HexKey, TokenContent>,
    ) = CashuWalletReader
        .computeUnspent(events, contents)
        .sumOf { it.content.totalAmount() }

    @Test
    fun `complete delivery reports the whole balance`() {
        val (events, contents) = wallet()
        assertEquals(2522L, balanceOf(events, contents))
    }

    @Test
    fun `a relay that serves only the newest events under-reports the balance`() {
        val (events, contents) = wallet()

        // What a capped REQ returns: the newest N matching events. The proofs
        // that fall off are the old, untouched mints — precisely the balance
        // the user forgot they had, which is why the shortfall is large rather
        // than marginal.
        val newestOnly = events.sortedByDescending { it.createdAt }.take(1)

        assertEquals(39L, balanceOf(newestOnly, contents))
        assertNotEquals(
            "a truncated delivery must not be mistaken for a complete one",
            balanceOf(events, contents),
            balanceOf(newestOnly, contents),
        )
    }

    @Test
    fun `each device sees a different number for the same wallet`() {
        val (events, contents) = wallet()
        val byAge = events.sortedByDescending { it.createdAt }

        // Same account, same relays, three delivery depths — three balances,
        // none of which is an error the client can detect locally: every one of
        // them is a correct sum over an incomplete set.
        assertEquals(39L, balanceOf(byAge.take(1), contents))
        assertEquals(1039L, balanceOf(byAge.take(2), contents))
        assertEquals(2522L, balanceOf(byAge.take(3), contents))
    }

    @Test
    fun `del rollover still retires spent tokens once everything is delivered`() {
        val (events, contents) = wallet()
        val spent = events.first { it.id.startsWith("aa") }
        val rollover = tokenEvent("dd", createdAt = 4_000)

        val withRollover = events + rollover
        val contentsWithRollover =
            contents +
                (
                    rollover.id to
                        TokenContent(
                            mint = "https://old.mint",
                            proofs = proofs("ks1", 512L),
                            del = listOf(spent.id),
                        )
                )

        // Backfilling every kind:7375 the account ever published cannot inflate
        // the balance through superseded events: the `del` chain retires them.
        assertEquals(1551L, balanceOf(withRollover, contentsWithRollover))
    }

    @Test
    fun `backfill filter asks for proofs only, with no limit for fetchAllPages to inherit`() {
        val filters = cashuProofBackfillFilters(owner)

        assertEquals(1, filters.size)
        val filter = filters.single()

        assertEquals(listOf(CashuTokenEvent.KIND), filter.kinds)
        assertEquals(listOf(owner), filter.authors)

        // fetchAllPages ends the walk on a fulfilled `limit` (End.LIMIT_REACHED)
        // rather than on a drained page, so a limit here would reintroduce the
        // very truncation the walk exists to defeat.
        assertNull("the paged walk must run to exhaustion, not to a limit", filter.limit)

        // Cursors are what make paging work; a preset window would pin the walk
        // to one slice of history.
        assertNull(filter.since)
        assertNull(filter.until)
    }

    @Test
    fun `own-event backfill covers every kind the live subscription authors`() {
        val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")
        val liveOwnKinds =
            cashuWalletFilters(
                CashuWalletQueryState(owner, setOf(relay), emptySet()),
                since = null,
            ).single { it.filter.authors == listOf(owner) }
                .filter.kinds
                .orEmpty()

        val backfillKinds = cashuOwnEventBackfillFilters(owner).single().kinds.orEmpty()

        // A headless client has no scrolling list to page for it, so its one-shot walk has to reach
        // everything the capped live query would have asked for — otherwise the gap just moves.
        liveOwnKinds.forEach {
            assertTrue("backfill must cover live-authored kind $it", it in backfillKinds)
        }
    }

    @Test
    fun `inbound nutzap backfill matches by recipient tag, not author`() {
        val f = cashuInboundNutzapBackfillFilters(owner).single()

        // Someone else signs a nutzap addressed to me, so it can only be found by the #p tag —
        // authors=[me] would return nothing and look like an empty inbox.
        assertEquals(listOf(owner), f.tags?.get("p"))
        assertNull(f.authors)
        assertNull("paged walks must not carry a limit", f.limit)
    }

    @Test
    fun `the live subscription mixes proofs with history — which is what starves them`() {
        val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")
        val filters =
            cashuWalletFilters(
                CashuWalletQueryState(
                    pubkey = owner,
                    ownEventRelays = setOf(relay),
                    inboxRelays = emptySet(),
                ),
                since = null,
            )

        val ownFilter = filters.single { it.filter.authors == listOf(owner) }.filter
        val kinds = ownFilter.kinds.orEmpty()

        // One REQ carries the proofs and the (far more numerous) history rows.
        // A cap applied to that combined stream is spent mostly on history, so
        // this filter alone can never be trusted to deliver the whole proof set
        // — hence the separate paged backfill.
        assertTrue(CashuTokenEvent.KIND in kinds)
        assertTrue(kinds.size > 1)
    }
}

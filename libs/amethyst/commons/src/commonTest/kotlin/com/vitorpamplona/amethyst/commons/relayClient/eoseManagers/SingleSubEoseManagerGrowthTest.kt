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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A [SingleSubEoseManager] serving several entities from one subscription keeps one EOSE cursor
 * per relay. Before [SingleSubEoseManager.sinceDroppingGrownRelays], the profile, community,
 * repository, follow-pack and channel assemblers handed the second entity opened on a relay the
 * first one's cursor, so the second one's history was never requested.
 */
class SingleSubEoseManagerGrowthTest {
    private val r = NormalizedRelayUrl("wss://r.example/")
    private val s = NormalizedRelayUrl("wss://s.example/")

    /** Records the cursor each assembly would have used. */
    private class Probe : SingleSubEoseManager<String>(EmptyNostrClient(), { emptySet() }) {
        var keysPerRelay: Map<NormalizedRelayUrl, Collection<String>> = emptyMap()
        var used: SincePerRelayMap? = null

        override fun updateFilter(
            keys: List<String>,
            since: SincePerRelayMap?,
        ): List<RelayBasedFilter> {
            used = sinceDroppingGrownRelays(keysPerRelay, since)
            return emptyList()
        }

        override fun distinct(key: String) = key
    }

    @Test
    fun aSecondEntityOnARelayIsAskedFromScratch() {
        val probe = Probe()
        probe.keysPerRelay = mapOf(r to listOf("profileA"), s to listOf("profileA"))
        probe.updateSubscriptions(setOf("profileA"))
        probe.newEose(r, 1_000)
        probe.newEose(s, 1_000)

        // The same entities: the cursor holds.
        probe.updateSubscriptions(setOf("profileA"))
        assertEquals(1_000, probe.used?.get(r)?.time)

        // Profile B opens on r only: r forgets its cursor, s keeps its own.
        probe.keysPerRelay = mapOf(r to listOf("profileA", "profileB"), s to listOf("profileA"))
        probe.updateSubscriptions(setOf("profileA", "profileB"))
        assertNull(probe.used?.get(r), "r gained profile B and must be asked from scratch")
        assertEquals(1_000, probe.used?.get(s)?.time)
        assertNull(probe.since()[r], "the stored cursor is dropped too, not only this assembly's copy")
    }

    @Test
    fun anEntityLeavingKeepsTheCursor() {
        val probe = Probe()
        probe.keysPerRelay = mapOf(r to listOf("profileA", "profileB"))
        probe.updateSubscriptions(setOf("profileA", "profileB"))
        probe.newEose(r, 1_000)

        probe.keysPerRelay = mapOf(r to listOf("profileA"))
        probe.updateSubscriptions(setOf("profileA"))
        assertEquals(1_000, probe.used?.get(r)?.time)
    }
}

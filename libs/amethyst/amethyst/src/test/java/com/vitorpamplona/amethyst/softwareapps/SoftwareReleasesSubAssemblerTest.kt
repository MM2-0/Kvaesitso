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
package com.vitorpamplona.amethyst.softwareapps

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.softwareapps.SoftwareReleasesQueryState
import com.vitorpamplona.amethyst.commons.relayClient.softwareapps.SoftwareReleasesSubAssembler
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareReleasesSubAssemblerTest {
    private val relay = "wss://relay.zapstore.dev".normalizeRelayUrl()

    // An app page opened from a link used to show only the releases some feed had fetched: nothing asked
    // relays for them. The page's subscription must ask the app's own relay for its trusted releases.
    @Test
    fun asksTheAppsRelayForItsTrustedReleases() {
        val publisher = NostrSignerSync(KeyPair())
        val maintainer = KeyPair().pubKey.toHexKey()
        val app = publisher.sign(SoftwareApplicationEvent.build("com.example.releases", "Example") { add(arrayOf("p", maintainer)) })
        LocalCache.justConsume(app, null, true)
        val note = LocalCache.getOrCreateAddressableNote(Address(app.kind, app.pubKey, "com.example.releases"))
        note.addRelaySync(relay) // the app was seen there

        val assembler = SoftwareReleasesSubAssembler(LocalCache, mockk(relaxed = true)) { emptySet() }
        try {
            val filters = assembler.updateFilter(SoftwareReleasesQueryState(note), null)

            assertTrue("the relay the app came from is asked: ${filters.map { it.relay }}", filters.any { it.relay == relay })
            filters.forEach { f ->
                assertEquals(listOf(ReleaseArtifactSetEvent.KIND), f.filter.kinds)
                assertEquals(setOf(app.pubKey, maintainer), f.filter.authors?.toSet())
                assertEquals(listOf("com.example.releases"), f.filter.tags?.get("i"))
            }
        } finally {
            assembler.destroy()
        }
    }

    @Test
    fun anAppNotLoadedYetAsksNothing() {
        val note = LocalCache.getOrCreateAddressableNote(Address(SoftwareApplicationEvent.KIND, "ab".repeat(32), "com.example.missing"))
        val assembler = SoftwareReleasesSubAssembler(LocalCache, mockk(relaxed = true)) { emptySet() }
        try {
            assertTrue(assembler.updateFilter(SoftwareReleasesQueryState(note), null).isEmpty())
        } finally {
            assembler.destroy()
        }
    }
}

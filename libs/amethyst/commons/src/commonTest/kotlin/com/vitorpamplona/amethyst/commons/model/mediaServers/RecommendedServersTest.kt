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
package com.vitorpamplona.amethyst.commons.model.mediaServers

import com.vitorpamplona.quartz.utils.Rfc3986
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendedServersTest {
    /** The hosts a list of these URLs shows as rows, the way `BlossomServersViewModel.toServerNames` names them. */
    private fun hostsOf(vararg urls: String): Set<String> = urls.mapTo(HashSet()) { Rfc3986.host(it) }

    private fun named(vararg names: String): List<ServerName> = names.map { name -> DEFAULT_MEDIA_SERVERS.first { it.name == name } }

    @Test
    fun `with nothing added every default is missing in default order`() {
        assertEquals(DEFAULT_MEDIA_SERVERS, missingRecommendedServers(emptySet()))
    }

    @Test
    fun `with every default added nothing is missing`() {
        val added = hostsOf(*DEFAULT_MEDIA_SERVERS.map { it.baseUrl }.toTypedArray(), "https://fertschnigfotography.com/")
        assertTrue(missingRecommendedServers(added).isEmpty())
    }

    @Test
    fun `a partial list leaves only the missing defaults still in default order`() {
        val added = hostsOf("https://blossom.primal.net/", "https://24242.io/", "https://nostrmedia.com", "https://example.com")
        assertEquals(
            named("Nostr.Build", "Nostrcheck.me", "Azzamo", "YakiHonne", "Sovbit", "Nostr.Download", "Satellite (Paid)"),
            missingRecommendedServers(added),
        )
    }

    @Test
    fun `a trailing slash does not change the match`() {
        // Default is written without the slash, the list entry with it, and the other way round.
        val added = hostsOf("https://cdn.nostrcheck.me/", "https://blossom.band")
        assertEquals(DEFAULT_MEDIA_SERVERS - named("Nostrcheck.me", "Nostr.Build"), missingRecommendedServers(added))
    }

    @Test
    fun `host case does not change the match`() {
        assertEquals(DEFAULT_MEDIA_SERVERS - named("Nostr.Build"), missingRecommendedServers(hostsOf("https://Blossom.Band/")))
    }

    @Test
    fun `a recommended host resolves to its friendly entry`() {
        assertEquals("Nostr.Build", recommendedServer("blossom.band")?.name)
        assertEquals("Nostrcheck.me", recommendedServer("cdn.nostrcheck.me")?.name)
        assertNull(recommendedServer("fertschnigfotography.com"))
    }
}

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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.FiltersChanged
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two invariants [ExplainedFilter] rests on. Both fail silently in production if broken — the
 * first leaks intent to relays, the second loses the purpose a few seconds after the app starts —
 * so they are pinned here rather than left to review.
 */
class ExplainedFilterTest {
    private val pubkey = "aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b"
    private val other = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362d5a1f9e0b1c"

    private fun plain(since: Long? = null) =
        Filter(
            kinds = listOf(1, 7, 6, 9735),
            authors = listOf(pubkey),
            tags = mapOf("p" to listOf(other)),
            since = since,
            limit = 20,
        )

    private fun explained(since: Long? = null) =
        ExplainedFilter(
            kinds = listOf(1, 7, 6, 9735),
            authors = listOf(pubkey),
            tags = mapOf("p" to listOf(other)),
            since = since,
            limit = 20,
            purpose = SubPurpose.NOTIFICATIONS,
            purposeDetail = "inbox relays for the active account",
            entityIds = listOf("cafe0000000000000000000000000000000000000000000000000000000000ff"),
            accountPubKeys = listOfNotNull(pubkey),
            scope = HashtagTopNavPerRelayFilter(setOf("askednostr")),
        )

    // ---- the wire must not learn why we asked -------------------------------

    /**
     * The privacy guarantee. It holds because `FilterSerializer` is registered for [Filter] and
     * writes an explicit protocol field list, so subclass state is dropped regardless of the runtime
     * type. If anyone swaps that for reflective serialization, this is what catches it.
     */
    @Test
    fun `serializes byte-identically to a plain filter`() {
        assertEquals(plain().toJson(), explained().toJson())
        assertEquals(plain(since = 1_785_379_272).toJson(), explained(since = 1_785_379_272).toJson())
    }

    @Test
    fun `neither the purpose nor its detail appears anywhere in the json`() {
        val json = explained().toJson()
        assertFalse("purpose leaked to the wire: $json", json.contains("NOTIFICATIONS", ignoreCase = true))
        assertFalse("purpose leaked to the wire: $json", json.contains("purpose", ignoreCase = true))
        assertFalse("detail leaked to the wire: $json", json.contains("inbox relays", ignoreCase = true))
        assertFalse("entityId leaked to the wire: $json", json.contains("cafe0000", ignoreCase = true))
        assertFalse("accountPubKey leaked as a field: $json", json.contains("accountPubKey", ignoreCase = true))
        // The scope is the feed selection behind the filter — a hashtag here, but for a follows feed
        // it is a slice of the user's follow list. Handing a relay the *selection* rather than the
        // authors it already sees would tell it which of its neighbours' filters belong together.
        assertFalse("scope leaked to the wire: $json", json.contains("askednostr", ignoreCase = true))
        assertFalse("scope leaked as a field: $json", json.contains("scope", ignoreCase = true))
    }

    /** The filter is serialized as part of a REQ, so check the real command too, not just the filter. */
    @Test
    fun `the REQ command carrying it is identical as well`() {
        assertEquals(
            ReqCmd("subid1", listOf(plain())).toJson(),
            ReqCmd("subid1", listOf(explained())).toJson(),
        )
    }

    // ---- the purpose must survive the live path -----------------------------

    /**
     * Assemblers call `copy(since = …)` after every EOSE to advance the window. Without the override
     * this returns a plain [Filter] and the purpose is gone — after the opening REQ, not at it, which
     * is why review would not catch it.
     */
    @Test
    fun `copy preserves the purpose`() {
        // Typed as the base Filter so the is-check below stays a runtime assertion — with the
        // override's covariant return type inferred, the compiler would prove it true statically.
        val advanced: Filter = explained().copy(since = 1_785_379_272)

        assertTrue("copy() must stay an ExplainedFilter", advanced is ExplainedFilter)
        assertEquals(SubPurpose.NOTIFICATIONS, advanced.purposeOrNull())
        assertEquals("inbox relays for the active account", (advanced as ExplainedFilter).purposeDetail)
        // entityIds/accountPubKey/scope ride the same path and would vanish just as silently
        assertEquals(listOf("cafe0000000000000000000000000000000000000000000000000000000000ff"), advanced.entityIds)
        assertEquals(listOf(pubkey), advanced.accountPubKeys)
        assertEquals(setOf("askednostr"), (advanced.scope as? HashtagTopNavPerRelayFilter)?.hashtags)
        assertEquals(1_785_379_272L, advanced.since)
        // and the protocol fields came along untouched
        assertEquals(listOf(pubkey), advanced.authors)
        assertEquals(listOf(1, 7, 6, 9735), advanced.kinds)
    }

    @Test
    fun `of() tags an existing filter without disturbing it`() {
        val tagged = ExplainedFilter.of(plain(since = 99), SubPurpose.DIRECT_MESSAGES)

        assertEquals(plain(since = 99).toJson(), tagged.toJson())
        assertEquals(SubPurpose.DIRECT_MESSAGES, tagged.purposeOrNull())
    }

    // ---- it must stay invisible to the rest of the client -------------------

    /**
     * `FiltersChanged` compares named fields, so tagging a filter must not look like a filter change.
     * If it did, every subscription would re-REQ on the next sync — invisible in tests, expensive on
     * a phone talking to ~400 relays.
     */
    @Test
    fun `tagging a filter does not look like a change worth resending`() {
        assertFalse(FiltersChanged.needsToResendRequest(listOf(plain()), listOf(explained())))
        assertFalse(FiltersChanged.needsToResendRequest(listOf(explained()), listOf(plain())))
    }

    @Test
    fun `an untagged filter reports no purpose`() {
        assertNull(plain().purposeOrNull())
        assertEquals(emptySet<SubPurpose>(), listOf(plain()).purposes())
    }

    @Test
    fun `purposes() summarises what a relay is doing for us`() {
        val filters =
            listOf(
                explained(),
                ExplainedFilter.of(plain(), SubPurpose.DIRECT_MESSAGES),
                ExplainedFilter.of(plain(), SubPurpose.NOTIFICATIONS),
                plain(),
            )

        assertEquals(setOf(SubPurpose.NOTIFICATIONS, SubPurpose.DIRECT_MESSAGES), filters.purposes())
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.newCommunity

import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-helper portion of [NewCommunityModel] that wires the
 * structured-rules editor state into a NIP-9B `kind:34551` payload. The full
 * `publish()` path needs a live [com.vitorpamplona.amethyst.commons.model.Account] (and
 * thus relays + a signer), so here we exercise the deterministic mapping that
 * sits between the editor's drafts and the Quartz tag types.
 */
class NewCommunityModelRulesTest {
    private val author = "a".repeat(64)
    private val denied = "b".repeat(64)
    private val wotRoot = "c".repeat(64)

    @Test
    fun `payload is null when no structured rules are set`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules = emptyList(),
                bannedPubkeys = emptyList(),
                wotGates = emptyList(),
                maxEventSize = null,
            )

        assertNull(payload)
    }

    @Test
    fun `payload non-null when only maxEventSize is set`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules = emptyList(),
                bannedPubkeys = emptyList(),
                wotGates = emptyList(),
                maxEventSize = 4096,
            )

        assertNotNull(payload)
        assertEquals(4096, payload!!.maxEventSize)
        assertTrue(payload.kindRules.isEmpty())
    }

    @Test
    fun `kind rule with limits round-trips into a tag`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules =
                    listOf(
                        KindRuleDraft(kind = 1, maxBytes = 1024, maxPerAuthorPerDay = 5),
                    ),
                bannedPubkeys = emptyList(),
                wotGates = emptyList(),
                maxEventSize = null,
            )

        assertNotNull(payload)
        assertEquals(1, payload!!.kindRules.size)
        val tag = payload.kindRules[0]
        assertEquals(1, tag.kind)
        assertEquals(1024, tag.maxBytes)
        assertEquals(5, tag.maxPerAuthorPerDay)
    }

    @Test
    fun `kind rule with no limits drops them from the published tag array`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules = listOf(KindRuleDraft(kind = 1111)),
                bannedPubkeys = emptyList(),
                wotGates = emptyList(),
                maxEventSize = null,
            )

        assertNotNull(payload)
        val tagArray = payload!!.kindRules[0].toTagArray()
        // arrayOfNotNull strips null limit fields, so a bare kind rule serialises to
        // exactly ["k", "<kind>"] - verifying the editor's "leave blank for no limit"
        // contract.
        assertEquals(2, tagArray.size)
        assertEquals("k", tagArray[0])
        assertEquals("1111", tagArray[1])
    }

    @Test
    fun `banned pubkey is published as a deny rule`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules = emptyList(),
                bannedPubkeys = listOf(BannedPubkeyDraft(pubkey = denied)),
                wotGates = emptyList(),
                maxEventSize = null,
            )

        assertNotNull(payload)
        assertEquals(1, payload!!.pubkeyRules.size)
        val rule = payload.pubkeyRules[0]
        assertEquals(denied, rule.pubkey)
        assertEquals(PubkeyRuleTag.Policy.DENY, rule.policy)
    }

    @Test
    fun `wot gate carries depth and root pubkey`() {
        val payload =
            NewCommunityModel.buildRulesPayload(
                kindRules = emptyList(),
                bannedPubkeys = emptyList(),
                wotGates = listOf(WotGateDraft(rootPubkey = wotRoot, depth = 3)),
                maxEventSize = null,
            )

        assertNotNull(payload)
        assertEquals(1, payload!!.wotGates.size)
        val gate = payload.wotGates[0]
        assertEquals(wotRoot, gate.rootPubkey)
        assertEquals(3, gate.depth)
    }

    @Test
    fun `rules editor and pubkey-input parser accept hex and npub`() {
        val hex = "deadbeef".repeat(8)
        // Round-trip via NPub.create so we don't have to hard-code a bech32 string.
        val npub =
            com.vitorpamplona.quartz.nip19Bech32.entities.NPub
                .create(hex)

        assertEquals(hex, parsePubkeyToHex(hex))
        assertEquals(hex, parsePubkeyToHex(npub))
        assertNull(parsePubkeyToHex("not a pubkey"))
        assertNull(parsePubkeyToHex(""))
        assertNull(parsePubkeyToHex("npub1invalid"))
    }
}

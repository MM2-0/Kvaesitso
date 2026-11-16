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
package com.vitorpamplona.amethyst.ui.components.namecoin

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.looksLikeNamecoinIdentifier
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.mapOutcomeToResolveState
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.namecoinLookupTerm
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinResolutionRowTest {
    // ── looksLikeNamecoinIdentifier ────────────────────────────────────────

    @Test
    fun `bare bit hostname is a namecoin identifier`() {
        assertTrue(looksLikeNamecoinIdentifier("testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("Example.BIT"))
    }

    @Test
    fun `user at bit hostname is a namecoin identifier`() {
        assertTrue(looksLikeNamecoinIdentifier("m@testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("ALICE@example.BIT"))
    }

    @Test
    fun `leading at sign is tolerated like the dropdown does`() {
        assertTrue(looksLikeNamecoinIdentifier("@testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("@m@testls.bit"))
    }

    @Test
    fun `dns nip05 is not a namecoin identifier`() {
        assertFalse(looksLikeNamecoinIdentifier("alice@example.com"))
        assertFalse(looksLikeNamecoinIdentifier("example.com"))
    }

    @Test
    fun `npub-shaped input is not a namecoin identifier`() {
        assertFalse(looksLikeNamecoinIdentifier("npub1w90qq8jq8x0z6nyz3vqgsk9vnp0w9p4ldwc0wq4xv2n7v8jch2nq3p6wrx"))
    }

    @Test
    fun `short or empty input is not a namecoin identifier`() {
        // Below the minimum length threshold the dropdown also uses
        // (UserSuggestionState.userSearchTermOrNull requires >2 chars).
        assertFalse(looksLikeNamecoinIdentifier(""))
        assertFalse(looksLikeNamecoinIdentifier(".bit"))
    }

    @Test
    fun `single-label bit name is still considered a namecoin identifier`() {
        // Namecoin allows single-character labels; "a.bit" is a valid
        // (if expensive) registration. Don't filter it out client-side.
        assertTrue(looksLikeNamecoinIdentifier("a.bit"))
    }

    @Test
    fun `bit substring elsewhere does not trigger`() {
        // "habit" or "rabbit.example.com" shouldn't match.
        assertFalse(looksLikeNamecoinIdentifier("rabbit.example.com"))
        assertFalse(looksLikeNamecoinIdentifier("ihabit"))
    }

    @Test
    fun `at without bit suffix does not match`() {
        // "foo@bar" with no .bit on the right side should not match.
        assertFalse(looksLikeNamecoinIdentifier("foo@bar"))
        assertFalse(looksLikeNamecoinIdentifier("foo@bar.com"))
    }

    @Test
    fun `d slash name is a namecoin identifier`() {
        // Direct domain-namespace references are accepted both via the
        // search bar (mirrors NamecoinNameResolver.isNamecoinIdentifier)
        // and via the dropdown's leading-at-sign convention.
        assertTrue(looksLikeNamecoinIdentifier("d/mstrofnone"))
        assertTrue(looksLikeNamecoinIdentifier("D/Example"))
        assertTrue(looksLikeNamecoinIdentifier("@d/mstrofnone"))
    }

    @Test
    fun `id slash name is a namecoin identifier`() {
        // Identity-namespace references must also flow to the resolver;
        // the parser at NamecoinNameResolver.parseIdentifier maps these
        // to (id/<name>, localPart="_").
        assertTrue(looksLikeNamecoinIdentifier("id/mstrofnone"))
        assertTrue(looksLikeNamecoinIdentifier("ID/Alice"))
        assertTrue(looksLikeNamecoinIdentifier("@id/alice"))
    }

    @Test
    fun `short single-label d and id names still trigger`() {
        // Namecoin allows single-character labels in both namespaces;
        // "d/a" / "id/a" should not be filtered out by the length floor.
        assertTrue(looksLikeNamecoinIdentifier("d/a"))
        assertTrue(looksLikeNamecoinIdentifier("id/a"))
    }

    @Test
    fun `bare namespace prefix with no name does not match`() {
        // "d/" and "id/" alone are not lookups.
        assertFalse(looksLikeNamecoinIdentifier("d/"))
        assertFalse(looksLikeNamecoinIdentifier("id/"))
    }

    @Test
    fun `other namecoin namespaces are not surfaced by the search row`() {
        // The Nostr-NIP05 contract only spans the domain (d/) and
        // identity (id/) namespaces. Other Namecoin namespaces exist on
        // chain but aren't part of the resolution surface, so the row
        // should stay quiet.
        assertFalse(looksLikeNamecoinIdentifier("a/somealias"))
        assertFalse(looksLikeNamecoinIdentifier("u/someuser"))
    }

    // ── namecoinLookupTerm ───────────────────────────────────────────────
    // The search field speaks the token language: a seeded chip (the
    // `kind:git ` the Git Repositories search icon pre-populates) is a
    // segment of its own, separate from the word typed after it. Only a
    // lone plain word is ever a Namecoin lookup; a compound query is a
    // search, and the row stays out of its way.

    @Test
    fun `bare identifier is looked up as itself`() {
        assertEquals("mstrofnone.bit", namecoinLookupTerm("mstrofnone.bit"))
        assertEquals("m@testls.bit", namecoinLookupTerm("m@testls.bit"))
        assertEquals("d/alice", namecoinLookupTerm("d/alice"))
        assertEquals("id/alice", namecoinLookupTerm("id/alice"))
    }

    @Test
    fun `leading at sign is stripped before the lookup`() {
        assertEquals("mstrofnone.bit", namecoinLookupTerm("@mstrofnone.bit"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("mstrofnone.bit", namecoinLookupTerm("  mstrofnone.bit  "))
    }

    @Test
    fun `seeded kind chip plus typed word is a search not a lookup`() {
        // The reported case: the Git Repositories search icon seeds
        // `kind:git ` and the user types a repository name after it. The
        // chip is a segment of its own; gluing it to the typed word made
        // the row report "No record for kind:git mstrofnone.bit".
        assertNull(namecoinLookupTerm("kind:git mstrofnone.bit"))
        assertNull(namecoinLookupTerm("kind:git testls.bit"))
    }

    @Test
    fun `any search token before the word makes it a search not a lookup`() {
        assertNull(namecoinLookupTerm("#nostr mstrofnone.bit"))
        assertNull(namecoinLookupTerm("since:2026-01-01 mstrofnone.bit"))
    }

    @Test
    fun `two plain words are a search not a lookup`() {
        assertNull(namecoinLookupTerm("foo mstrofnone.bit"))
        assertNull(namecoinLookupTerm("mstrofnone.bit foo"))
    }

    @Test
    fun `lone kind token is not a lookup`() {
        assertNull(namecoinLookupTerm("kind:git"))
    }

    @Test
    fun `blank input is not a lookup`() {
        assertNull(namecoinLookupTerm(""))
        assertNull(namecoinLookupTerm("   "))
    }

    @Test
    fun `plain word that is not namecoin-shaped is not a lookup`() {
        assertNull(namecoinLookupTerm("example.com"))
        assertNull(namecoinLookupTerm("rabbit"))
    }

    // ── mapOutcomeToResolveState ───────────────────────────────────────────
    // Reuses the shared NamecoinResolveState already used by
    // NamecoinNameService and the desktop SearchScreen so all surfaces
    // produce the same diagnostic strings for the same outcome.

    @Test
    fun `name not found maps to NotFound`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.NameNotFound("d/testls"))
        assertSame(NamecoinResolveState.NotFound, state)
    }

    @Test
    fun `no nostr field maps to Error and mentions the name`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.NoNostrField("d/noname"))
        require(state is NamecoinResolveState.Error)
        assertEquals(NamecoinResolveState.Reason.NoNostrField("d/noname"), state.reason)
    }

    @Test
    fun `malformed record preserves underlying parser detail`() {
        val state =
            mapOutcomeToResolveState(
                NamecoinResolveOutcome.MalformedRecord(
                    "d/broken",
                    "Unfinished JSON term at EOF at line 1, column 474",
                ),
            )
        require(state is NamecoinResolveState.Error)
        assertEquals(
            NamecoinResolveState.Reason.MalformedRecord("d/broken", "Unfinished JSON term at EOF at line 1, column 474"),
            state.reason,
        )
    }

    @Test
    fun `servers unreachable maps to a generic Error`() {
        val state =
            mapOutcomeToResolveState(NamecoinResolveOutcome.ServersUnreachable("Connection refused"))
        require(state is NamecoinResolveState.Error)
        assertSame(NamecoinResolveState.Reason.ServersUnreachable, state.reason)
    }

    @Test
    fun `invalid identifier maps to a generic Error`() {
        val state =
            mapOutcomeToResolveState(NamecoinResolveOutcome.InvalidIdentifier("not_a_name"))
        require(state is NamecoinResolveState.Error)
        assertSame(NamecoinResolveState.Reason.InvalidIdentifier, state.reason)
    }

    @Test
    fun `timeout maps to a timeout Error`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.Timeout)
        require(state is NamecoinResolveState.Error)
        assertSame(NamecoinResolveState.Reason.Timeout, state.reason)
    }

    @Test(expected = IllegalStateException::class)
    fun `success outcome must be handled by callers, not mapOutcomeToResolveState`() {
        // mapOutcomeToResolveState is documented as failure-only; callers must
        // route NamecoinResolveOutcome.Success through LocalCache themselves.
        mapOutcomeToResolveState(
            NamecoinResolveOutcome.Success(
                NamecoinNostrResult(
                    pubkey = "deadbeef".repeat(8),
                    relays = emptyList(),
                    namecoinName = "d/testls",
                    localPart = "_",
                ),
            ),
        )
    }

    // ── outcomes of a record that references other names ──────────────────

    @Test
    fun `a failed import reports why the imported name failed`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.ImportFailed("d/a", "d/b", NamecoinResolveOutcome.NameNotFound("d/b")))
        assertSame(NamecoinResolveState.NotFound, state)
    }

    @Test
    fun `a failed delegation reports why the delegate failed`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.DelegationFailed("d/a", "d/b", NamecoinResolveOutcome.Timeout))
        assertEquals(NamecoinResolveState.Error(NamecoinResolveState.Reason.Timeout), state)
    }

    @Test
    fun `references that loop or go too deep read as a malformed record`() {
        val cyclic = mapOutcomeToResolveState(NamecoinResolveOutcome.CyclicReference("d/a", listOf("d/a", "d/b", "d/a")))
        assertEquals(NamecoinResolveState.Error(NamecoinResolveState.Reason.MalformedRecord("d/a", "references loop: d/a → d/b → d/a")), cyclic)

        val budget = mapOutcomeToResolveState(NamecoinResolveOutcome.LookupBudgetExhausted("d/a", 16))
        assertEquals(NamecoinResolveState.Error(NamecoinResolveState.Reason.MalformedRecord("d/a", "references need more than 16 lookups")), budget)

        val depth = mapOutcomeToResolveState(NamecoinResolveOutcome.DepthLimitExceeded("d/a", 4))
        assertEquals(NamecoinResolveState.Error(NamecoinResolveState.Reason.MalformedRecord("d/a", "references nest deeper than 4")), depth)
    }
}

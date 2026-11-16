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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KindRegistryTest {
    @Test
    fun resolveNote() {
        assertEquals(listOf(1), KindRegistry.resolve("note"))
    }

    @Test
    fun resolveArticle() {
        assertEquals(listOf(30023), KindRegistry.resolve("article"))
    }

    @Test
    fun resolveChannel() {
        val kinds = KindRegistry.resolve("channel")!!
        assertTrue(40 in kinds)
        assertTrue(41 in kinds)
    }

    @Test
    fun resolveCaseInsensitive() {
        assertEquals(KindRegistry.resolve("NOTE"), KindRegistry.resolve("note"))
    }

    @Test
    fun resolveUnknown() {
        assertNull(KindRegistry.resolve("unknown"))
    }

    @Test
    fun isPseudoKindReply() {
        assertTrue(KindRegistry.isPseudoKind("reply"))
        assertTrue(KindRegistry.isPseudoKind("Reply"))
    }

    @Test
    fun isPseudoKindMedia() {
        assertTrue(KindRegistry.isPseudoKind("media"))
    }

    @Test
    fun isNotPseudoKind() {
        assertFalse(KindRegistry.isPseudoKind("note"))
        assertFalse(KindRegistry.isPseudoKind("article"))
    }

    @Test
    fun nameForKind1() {
        assertEquals("note", KindRegistry.nameFor(1))
    }

    @Test
    fun nameForKind30023() {
        assertEquals("article", KindRegistry.nameFor(30023))
    }

    @Test
    fun nameForUnknownKind() {
        assertNull(KindRegistry.nameFor(99999))
    }

    @Test
    fun allAliasesResolve() {
        KindRegistry.aliases.forEach { (alias, kinds) ->
            assertEquals(kinds, KindRegistry.resolve(alias))
        }
    }

    @Test
    fun presetsContainExpectedEntries() {
        assertTrue(KindRegistry.presets.containsKey("Notes"))
        assertTrue(KindRegistry.presets.containsKey("Articles"))
        assertTrue(KindRegistry.presets.containsKey("Media"))
        assertTrue(KindRegistry.presets.containsKey("Channels"))
    }

    // --- the vocabulary against the rest of search ------------------------------------------

    @Test
    fun noAliasNamesAKindSearchNeverReturns() {
        // The defect this catches shipped twice: `kind:repost` and `kind:profile` both drew a
        // chip, narrowed the query to a kind the result scan drops, and returned nothing —
        // forever, for every reader who tried them. An offered name has to be able to answer.
        val dead = KindRegistry.aliases.filterValues { kinds -> kinds.any { it in RenderableKinds.NEVER_IN_RESULTS } }
        assertEquals(emptyMap(), dead, "these aliases name kinds RenderableKinds.NEVER_IN_RESULTS drops")
    }

    @Test
    fun everyAliasWritesItselfBackAsItsOwnName() {
        // A window has to serialize to the name that produced it. Aliases overlap on purpose —
        // `short` is inside `stories` is inside `video`, `nest` is inside `live` — and an alias
        // that tokenized to a wider one would widen the reader's query a little more on every
        // round trip through the field.
        KindRegistry.aliases.forEach { (alias, kinds) ->
            assertEquals(listOf(alias), KindRegistry.tokenize(kinds), "kind:$alias ($kinds)")
        }
    }

    @Test
    fun everyAliasNamesKindsQuartzKnowsHowToBuild() {
        // A number that no event class claims is a filter nothing can ever match. Checked through
        // the factory rather than a list, so a kind removed from Quartz fails here.
        val unbuildable =
            KindRegistry.aliases.filterValues { kinds ->
                kinds.any { kind ->
                    EventFactory.create<Event>("9".repeat(64), "a".repeat(64), 1L, kind, emptyArray(), "", "")::class == Event::class
                }
            }
        assertEquals(emptyMap(), unbuildable, "these aliases name kinds EventFactory has no class for")
    }

    @Test
    fun theKindsWithNoNameOfTheirOwnAreStillTheOnesWithout() {
        // Pinned rather than fixed: a renderable kind with no alias can only be asked for as a
        // bare number, which is a worse experience but a deliberate one — not every kind deserves
        // a word. The list is here so adding a kind to RenderableKinds is a decision about
        // whether it earns a name, rather than silence.
        assertEquals(
            // 7516 is a found log: read inside a cache's thread, where `kind:geocache` already
            // leads, rather than looked up on its own. 30301 and 30302 are Kanban boards and cards,
            // but 30301 is also WalletScrutiny's build verdicts, so no one word names it honestly.
            // 30100-30104, 31341 and 31985 are drss.io's records, and other apps publish on most
            // of those numbers too (catalogs, voting rounds, game reviews).
            listOf(24, 54, 1018, 1111, 1337, 1808, 7516, 10001, 10003, 30000, 30001, 30005, 30053, 30100, 30101, 30102, 30104, 30296, 30297, 30301, 30302, 30817, 31337, 31341, 31985),
            RenderableKinds.ALL.filter { KindRegistry.nameFor(it) == null }.sorted(),
        )
    }
}

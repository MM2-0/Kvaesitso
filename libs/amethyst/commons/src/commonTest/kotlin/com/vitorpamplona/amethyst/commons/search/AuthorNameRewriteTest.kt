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

import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorNameRewriteTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"

        // The keys the rewrite writes: VITOR_HEX is NPUB decoded; MSTROFNONE_HEX stands alone.
        const val VITOR_HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        const val MSTROFNONE_HEX = "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"
    }

    private fun unresolved(text: String) = AuthorNameRewrite.unresolvedAuthorNames(text)

    @Test
    fun aFromNameIsFoundWithItsSpan() {
        val text = "kind:git from:mstrofnone.bit"
        val token = unresolved(text).single()
        assertEquals(KeyField.FROM, token.field)
        assertEquals("mstrofnone.bit", token.name)
        // The span covers the whole token, qualifier included, exactly where it was typed.
        assertEquals("from:mstrofnone.bit", text.substring(token.start, token.end))
    }

    @Test
    fun aToNameIsFound() {
        val token = unresolved("to:alice@example.com").single()
        assertEquals(KeyField.TO, token.field)
        assertEquals("alice@example.com", token.name)
    }

    @Test
    fun aKeyIsFinishedAndLeftAlone() {
        // Already a key — nothing to resolve, and rewriting it again would loop.
        assertTrue(unresolved("from:$NPUB").isEmpty())
        assertTrue(unresolved("from:$VITOR_HEX").isEmpty())
        assertTrue(unresolved("to:$NPUB").isEmpty())
    }

    @Test
    fun aQuotedQualifierIsLiteralText() {
        assertTrue(unresolved("\"from:mstrofnone.bit\"").isEmpty())
    }

    @Test
    fun aNegatedQualifierIsAnExclusion() {
        assertTrue(unresolved("-from:mstrofnone.bit").isEmpty())
    }

    @Test
    fun aPointerUnderToNamesNoPerson() {
        // A `to:` that has begun a NIP-19 pointer names an event; the pointer path owns it.
        assertTrue(unresolved("to:$NOTE").isEmpty())
    }

    @Test
    fun anEmptyQualifierNamesNobody() {
        assertTrue(unresolved("from:").isEmpty())
        assertTrue(unresolved("to:").isEmpty())
    }

    @Test
    fun anUppercaseQualifierStillNamesAPerson() {
        val token = unresolved("FROM:alice@example.com").single()
        assertEquals(KeyField.FROM, token.field)
        assertEquals("alice@example.com", token.name)
    }

    @Test
    fun anUnknownOperatorIsNotAnAuthorName() {
        assertTrue(unresolved("site:mstrofnone.bit").isEmpty())
    }

    @Test
    fun severalNamesAreAllFoundInOrder() {
        val tokens = unresolved("from:alice@example.com kind:git to:mstrofnone.bit from:$NPUB")
        assertEquals(listOf("alice@example.com", "mstrofnone.bit"), tokens.map { it.name })
        assertEquals(listOf(KeyField.FROM, KeyField.TO), tokens.map { it.field })
    }

    @Test
    fun rewriteSplicesTheNpubOverTheToken() {
        val text = "kind:git from:mstrofnone.bit amethyst"
        val token = unresolved(text).single()
        val rewritten = AuthorNameRewrite.rewrite(text, token, MSTROFNONE_HEX)
        assertEquals("kind:git from:${NPub.create(MSTROFNONE_HEX)} amethyst", rewritten)
    }

    @Test
    fun aRewrittenTokenIsExactlyWhatAPickWrites() {
        // SearchFieldState.pickPerson writes <field>:npub1… over the token; the rewrite of a
        // resolved name must produce the same text, so the pill and the parser read one thing.
        val text = "to:alice@example.com"
        val token = unresolved(text).single()
        val rewritten = AuthorNameRewrite.rewrite(text, token, VITOR_HEX)
        assertEquals("to:$NPUB", rewritten)
    }

    @Test
    fun aRewrittenTokenParsesAsTheAuthorAndStaysFinished() {
        val text = "kind:git from:mstrofnone.bit"
        val token = unresolved(text).single()
        val rewritten = AuthorNameRewrite.rewrite(text, token, MSTROFNONE_HEX)!!
        assertEquals(listOf(MSTROFNONE_HEX), QueryParser.parse(rewritten).authors.toList())
        // Nothing is left to resolve: the rewrite cannot trigger another.
        assertTrue(unresolved(rewritten).isEmpty())
    }

    @Test
    fun aRewrittenToTokenParsesAsTheMention() {
        val token = unresolved("to:mstrofnone.bit").single()
        val rewritten = AuthorNameRewrite.rewrite("to:mstrofnone.bit", token, MSTROFNONE_HEX)!!
        assertEquals(listOf(MSTROFNONE_HEX), QueryParser.parse(rewritten).mentions.toList())
    }

    @Test
    fun rewriteRejectsAKeyThatIsNotOne() {
        // A name must stay a name rather than be replaced by garbage.
        val token = unresolved("from:alice@example.com").single()
        assertNull(AuthorNameRewrite.rewrite("from:alice@example.com", token, "not-a-key"))
    }

    @Test
    fun severalRewritesApplyRightToLeft() {
        val text = "from:alice@example.com to:mstrofnone.bit"
        val tokens = unresolved(text)
        assertEquals(2, tokens.size)
        // Right to left, so the first splice never moves the second token’s span.
        var rewritten = text
        rewritten = AuthorNameRewrite.rewrite(rewritten, tokens[1], MSTROFNONE_HEX)!!
        rewritten = AuthorNameRewrite.rewrite(rewritten, tokens[0], VITOR_HEX)!!
        assertEquals("from:$NPUB to:${NPub.create(MSTROFNONE_HEX)}", rewritten)
        val query = QueryParser.parse(rewritten)
        assertEquals(listOf(VITOR_HEX), query.authors.toList())
        assertEquals(listOf(MSTROFNONE_HEX), query.mentions.toList())
    }
}

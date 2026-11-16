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
package com.vitorpamplona.amethyst.commons.ui.note.nip22Comments

import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure NIP-9B draft-validation helper exposed on
 * [CommentPostViewModel]'s companion. The ViewModel itself is too coupled to
 * Compose state, the relay subscription stack, and signers to construct in a
 * unit test, but the helper is the only place where draft size, kind, and
 * author flow into the validator — so testing it covers what matters.
 */
class CommentPostViewModelTest {
    private val community = "a".repeat(64)
    private val author = "b".repeat(64)
    private val denied = "c".repeat(64)

    private fun rules(
        allowedKind: Int = CommentEvent.KIND,
        maxBytes: Int? = null,
        deny: List<String> = emptyList(),
    ): CommunityRulesEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("d", "rules-1"))
                add(arrayOf("a", "34550:$community:my-community"))
                if (maxBytes != null) {
                    add(arrayOf("k", allowedKind.toString(), maxBytes.toString()))
                } else {
                    add(arrayOf("k", allowedKind.toString()))
                }
                deny.forEach { add(arrayOf("p", it, "deny")) }
            }.toTypedArray()
        return CommunityRulesEvent(
            id = "0".repeat(64),
            pubKey = community,
            createdAt = 100L,
            tags = tags,
            content = "",
            sig = "0".repeat(128),
        )
    }

    @Test
    fun `valid draft passes without violation`() {
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(),
                author = author,
                draftContent = "hello world",
            )
        assertNull(violation)
    }

    @Test
    fun `kind not allowed yields KindNotAllowed`() {
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(allowedKind = 30023), // long-form, not kind:1111
                author = author,
                draftContent = "hello",
            )
        assertTrue(violation is CommunityRulesValidator.Violation.KindNotAllowed)
        assertEquals(CommentEvent.KIND, (violation as CommunityRulesValidator.Violation.KindNotAllowed).kind)
    }

    @Test
    fun `oversize draft yields KindSizeExceeded`() {
        val draft = "x".repeat(120)
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(maxBytes = 100),
                author = author,
                draftContent = draft,
            )
        assertTrue(violation is CommunityRulesValidator.Violation.KindSizeExceeded)
        val ks = violation as CommunityRulesValidator.Violation.KindSizeExceeded
        assertEquals(120, ks.sizeBytes)
        assertEquals(100, ks.maxBytes)
    }

    @Test
    fun `denied author yields AuthorDenied regardless of content`() {
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(deny = listOf(denied)),
                author = denied,
                draftContent = "hello",
            )
        assertTrue(violation is CommunityRulesValidator.Violation.AuthorDenied)
    }

    @Test
    fun `under-size boundary passes`() {
        val draft = "x".repeat(100) // exactly the cap
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(maxBytes = 100),
                author = author,
                draftContent = draft,
            )
        assertNull(violation)
    }

    @Test
    fun `multibyte chars count toward size cap by bytes not chars`() {
        // "🚀" is 4 UTF-8 bytes, so 30 of them = 120 bytes > cap of 100.
        val draft = "🚀".repeat(30)
        val violation =
            CommentPostViewModel.validateDraft(
                rules = rules(maxBytes = 100),
                author = author,
                draftContent = draft,
            )
        assertTrue(violation is CommunityRulesValidator.Violation.KindSizeExceeded)
        assertEquals(120, (violation as CommunityRulesValidator.Violation.KindSizeExceeded).sizeBytes)
    }
}

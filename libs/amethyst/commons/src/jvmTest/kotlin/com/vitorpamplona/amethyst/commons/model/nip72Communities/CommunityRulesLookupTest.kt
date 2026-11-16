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
package com.vitorpamplona.amethyst.commons.model.nip72Communities

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-helper [violatesCommunityRules] entry point used by
 * [CommunityFeedFilter] and [CommunityModerationFeedFilter] when the user has
 * enabled "Hide posts that violate community rules".
 *
 * Builds synthetic [Note]s + [CommunityRulesEvent]s without going through
 * LocalCache, the relay client, or any signer. The validator itself is covered
 * by Quartz tests; here we cover the wiring between an editor-created rules
 * document and amethyst's [Note] type.
 */
class CommunityRulesLookupTest {
    private val communityOwner = "a".repeat(64)
    private val author = "b".repeat(64)
    private val denied = "c".repeat(64)

    private fun rules(
        allowedKinds: List<Int> = listOf(CommentEvent.KIND),
        kindMaxBytes: Int? = null,
        deny: List<String> = emptyList(),
        globalMaxBytes: Int? = null,
    ): CommunityRulesEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("d", "rules-1"))
                add(arrayOf("a", "34550:$communityOwner:my-community"))
                allowedKinds.forEach { kind ->
                    if (kindMaxBytes != null) {
                        add(arrayOf("k", kind.toString(), kindMaxBytes.toString()))
                    } else {
                        add(arrayOf("k", kind.toString()))
                    }
                }
                deny.forEach { add(arrayOf("p", it, "deny")) }
                globalMaxBytes?.let { add(arrayOf("max_event_size", it.toString())) }
            }.toTypedArray()
        return CommunityRulesEvent(
            id = "0".repeat(64),
            pubKey = communityOwner,
            createdAt = 100L,
            tags = tags,
            content = "",
            sig = "0".repeat(128),
        )
    }

    private fun textNote(
        content: String,
        pubKey: String = author,
    ): Note {
        val n = Note("d".repeat(64))
        n.event =
            TextNoteEvent(
                id = "1".repeat(64),
                pubKey = pubKey,
                createdAt = 200L,
                tags = emptyArray(),
                content = content,
                sig = "0".repeat(128),
            )
        return n
    }

    private fun commentNote(
        content: String,
        pubKey: String = author,
    ): Note {
        val n = Note("e".repeat(64))
        n.event =
            CommentEvent(
                id = "2".repeat(64),
                pubKey = pubKey,
                createdAt = 200L,
                tags = emptyArray(),
                content = content,
                sig = "0".repeat(128),
            )
        return n
    }

    @Test
    fun `comment passes when its kind is whitelisted`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND)))
        assertFalse(violatesCommunityRules(v, commentNote("hello")))
    }

    @Test
    fun `text note fails when only comments are whitelisted`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND)))
        assertTrue(violatesCommunityRules(v, textNote("hello")))
    }

    @Test
    fun `denied author is rejected even if kind allowed`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND), deny = listOf(denied)))
        assertTrue(violatesCommunityRules(v, commentNote("hello", pubKey = denied)))
    }

    @Test
    fun `oversize comment fails per-kind size cap`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND), kindMaxBytes = 5))
        assertTrue(violatesCommunityRules(v, commentNote("longer than five bytes")))
    }

    @Test
    fun `under-size comment passes per-kind size cap`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND), kindMaxBytes = 100))
        assertFalse(violatesCommunityRules(v, commentNote("hi")))
    }

    @Test
    fun `global max event size also rejects oversize`() {
        val v = CommunityRulesValidator(rules(allowedKinds = listOf(CommentEvent.KIND), globalMaxBytes = 4))
        assertTrue(violatesCommunityRules(v, commentNote("five!")))
    }

    @Test
    fun `note without an event is treated as passing - filter has nothing to evaluate`() {
        val v = CommunityRulesValidator(rules())
        val empty = Note("f".repeat(64))
        assertFalse(violatesCommunityRules(v, empty))
    }
}

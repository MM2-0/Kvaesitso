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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteIsHiddenForTest {
    private val rootId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val replyId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private val authorPubKey = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

    private val noHidden =
        LiveHiddenUsers(
            showSensitiveContent = null,
            hiddenWordsCase = emptyList(),
            hiddenUsersHashCodes = emptySet(),
            spammersHashCodes = emptySet(),
            mutedThreads = emptySet(),
        )

    private fun textNoteEvent(
        id: String,
        pubKey: String = authorPubKey,
        eTags: Array<Array<String>> = emptyArray(),
    ) = TextNoteEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags = eTags,
        content = "hello",
        sig = "sig",
    )

    private fun rootETag(eventId: String) = arrayOf("e", eventId, "", "root")

    @Test
    fun reply_inMutedThread_isHidden() {
        val event = textNoteEvent(id = replyId, eTags = arrayOf(rootETag(rootId)))
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertTrue(note.isHiddenFor(choices), "Reply inside a muted thread must be hidden")
    }

    @Test
    fun topLevelNote_ownIdMuted_isHidden() {
        val event = textNoteEvent(id = rootId, eTags = emptyArray())
        val note = Note(rootId).also { it.event = event }
        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertTrue(note.isHiddenFor(choices), "Top-level note whose id is muted must be hidden")
    }

    @Test
    fun note_inUnmutedThread_isNotHidden() {
        val otherRoot = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val event = textNoteEvent(id = replyId, eTags = arrayOf(rootETag(otherRoot)))
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertFalse(note.isHiddenFor(choices), "Reply in an un-muted thread must not be hidden")
    }

    @Test
    fun authorHidden_isHidden_regression() {
        val event = textNoteEvent(id = replyId)
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(hiddenUsersHashCodes = setOf(authorPubKey.hashCode()))

        assertTrue(note.isHiddenFor(choices), "Note whose author is hidden must still be hidden")
    }

    private fun tTag(hashtag: String) = arrayOf("t", hashtag)

    @Test
    fun note_withMutedHashtag_isHidden() {
        val event = textNoteEvent(id = replyId, eTags = arrayOf(tTag("bitcoin")))
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(hiddenHashtags = setOf("bitcoin"))

        assertTrue(note.isHiddenFor(choices), "Note tagged with a muted hashtag must be hidden")
    }

    @Test
    fun note_withMutedHashtag_isCaseInsensitive() {
        val event = textNoteEvent(id = replyId, eTags = arrayOf(tTag("Bitcoin")))
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(hiddenHashtags = setOf("bitcoin"))

        assertTrue(note.isHiddenFor(choices), "Muted-hashtag matching must ignore case")
    }

    @Test
    fun note_withoutMutedHashtag_isNotHidden() {
        val event = textNoteEvent(id = replyId, eTags = arrayOf(tTag("nostr")))
        val note = Note(replyId).also { it.event = event }
        val choices = noHidden.copy(hiddenHashtags = setOf("bitcoin"))

        assertFalse(note.isHiddenFor(choices), "Note without the muted hashtag must not be hidden")
    }
}

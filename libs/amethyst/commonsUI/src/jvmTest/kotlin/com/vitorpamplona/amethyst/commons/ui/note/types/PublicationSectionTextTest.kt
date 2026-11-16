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
package com.vitorpamplona.amethyst.commons.ui.note.types

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A section translates the markdown its AsciiDoc converts to, so the copy menus must look its
 * translation up by that same string. Keyed on the raw AsciiDoc they would always miss, and
 * "Copy Translated" would never be offered for a chapter.
 */
class PublicationSectionTextTest {
    private val author = "3e1ad0f3a5d3c12245db7788546c43ade3d97c6e046c594f6017cd6cd4164690"
    private val content = "== The Maiden\n\nOn an evening in the latter part of May a _middle-aged_ man was walking homeward."

    private val event =
        Event(
            id = "dde7f1f8725e080fe52fba8b881a46958227f91c0772e5e60c616beccefff384",
            pubKey = author,
            createdAt = 1774299012,
            kind = PublicationContentEvent.KIND,
            tags = arrayOf(arrayOf("d", "pg110-chapter-2-x"), arrayOf("title", "X")),
            content = content,
            sig = "",
        )

    private fun note(): Note {
        val section = Event.fromJson(event.toJson()) as PublicationContentEvent
        return Note(section.id).also {
            it.loadEvent(section, User(author, UserContext { addr -> AddressableNote(addr) }), emptyList())
        }
    }

    @Test
    fun copyTextIsTheMarkdownTheSectionRendersAndTranslates() {
        val note = note()
        val rendered = publicationSectionMarkdown(note.event as PublicationContentEvent)

        assertNotEquals(content, rendered, "the heading converts, so the two strings differ")
        assertEquals(rendered, displayedNoteText(note, content))
    }
}

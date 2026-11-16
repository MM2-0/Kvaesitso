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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LongFormDatesTest {
    private val createdAt = 1_780_000_000L

    @Test
    fun noPublishedAtHasNothingToAdd() {
        assertNull(publishedAtWorthShowing(null, createdAt))
    }

    @Test
    fun publishedAtNextToCreatedAtIsTheSameDate() {
        // The thread's author row already shows created_at; a second, unlabeled copy is noise.
        assertNull(publishedAtWorthShowing(createdAt, createdAt))
        assertNull(publishedAtWorthShowing(createdAt - 59 * 60, createdAt))
    }

    @Test
    fun anArticleEditedLaterKeepsItsPublicationDate() {
        val published = createdAt - 3 * 24 * 60 * 60
        assertEquals(published, publishedAtWorthShowing(published, createdAt))
    }
}

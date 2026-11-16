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
package com.vitorpamplona.amethyst.commons.chats.ui

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubjectChangeTest {
    private val key = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private fun message(subject: String?) =
        Event(
            id = key,
            pubKey = key,
            createdAt = 1L,
            kind = 14,
            tags = if (subject != null) arrayOf(arrayOf("subject", subject)) else emptyArray(),
            content = "hi",
            sig = key,
        )

    @Test
    fun noSubjectIsNoRename() {
        assertNull(subjectChangeOf(message(null), message(null)))
    }

    @Test
    fun aNewSubjectIsARename() {
        assertEquals("Product", subjectChangeOf(message(null), message("Product")))
        assertEquals("Product", subjectChangeOf(message("Old"), message("Product")))
    }

    @Test
    fun aRepeatedSubjectIsNotARename() {
        // Some clients tag every message of a named group with its subject.
        assertNull(subjectChangeOf(message("Product"), message("Product")))
        assertNull(subjectChangeOf(message("Product"), message(" Product ")))
    }

    @Test
    fun aBlankSubjectIsNoRename() {
        assertNull(subjectChangeOf(message("Product"), message("  ")))
    }
}

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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorialBriefTest {
    /**
     * The brief is one text in two apps: Brainstorm's client/src/lib/frontPage/editorial.md pins
     * the same hash of its trimmed text. Change both, and this, together.
     */
    @Test
    fun theBriefIsTheOneBrainstormCarries() {
        assertEquals(BRIEF_SHA256, sha256(EDITORIAL_BRIEF.trim().encodeToByteArray()).toHexKey())
    }

    @Test
    fun theBriefCarriesNoCorrectionsThatContradictIt() {
        // Folded into the body: no stacked corrections, no promise the app does not keep.
        listOf("Corrections for this harness", "Step 5", "both light and dark themes", "og:title", "twitter:", "UTC", "NO CONTINUITY", "thrown away", "second refusal", "jumble", "njump", "zap.stream", "shopstr").forEach {
            assertFalse(EDITORIAL_BRIEF.contains(it), it)
        }
        listOf("Vol. I · No. N", "<style id=\"house\"></style>", "<!-- sections:", "Every `<img>` has an `alt`", "Never describe a picture you have not seen", "a quote that fails is sent back to you to fix", "## The Kitchen", "Never invent a quantity", "<CITE_BASE><64-hex-event-id>", "<PROFILE_BASE><64-hex>").forEach {
            assertTrue(EDITORIAL_BRIEF.contains(it), it)
        }
    }

    companion object {
        const val BRIEF_SHA256 = "f3b16ee788ba8d859dfeb349c94c7facbf7158eaa02d5151ee94618972b24792"
    }
}

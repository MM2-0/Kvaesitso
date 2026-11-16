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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NappletArtifactPolicyTest {
    private val htmlHash = "ab".repeat(32)
    private val index = PathTag("/index.html", htmlHash)

    @Test
    fun usesTheSignedArtifactHashAsIdentity() {
        assertEquals(
            htmlHash,
            NappletArtifactPolicy.verifiedArtifactHash(listOf(index), htmlHash),
        )
    }

    @Test
    fun rejectsUppercaseAndMissingIdentity() {
        val computed = htmlHash
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(index), computed.uppercase()))
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(index), null))
    }

    @Test
    fun rejectsDriftedOrNonSelfContainedArtifacts() {
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(index), "22".repeat(32)))
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(PathTag("index.html", htmlHash)), null))
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(index, PathTag("/app.js", "22".repeat(32))), null))
        assertNull(NappletArtifactPolicy.verifiedArtifactHash(listOf(PathTag("/index.html", "not-a-sha256")), null))
    }
}

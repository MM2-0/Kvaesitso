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
package com.vitorpamplona.amethyst.model.nipB7Blossom

import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.resetTargetOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlossomServerListResetTest {
    private fun server(host: String) = ServerName(host, "https://$host/", ServerType.Blossom)

    private val a = server("a.example")
    private val b = server("b.example")
    private val c = server("c.example")
    private val custom = server("my-custom.example")

    @Test
    fun transientDefaultEmissionDoesNotClobberSavedPick() {
        // Regression guard: before the user's BlossomServersEvent loads, the raw
        // published list is empty and `merged` is the DEFAULT_MEDIA_SERVERS fallback.
        // The saved custom pick must NOT be reset.
        val result =
            resetTargetOrNull(
                rawList = emptyList(),
                merged = listOf(a, b, c),
                current = custom,
            )

        assertNull(result)
    }

    @Test
    fun loadedListWithoutCurrentResetsToFirst() {
        // The user removed their current default from the published list -> reset.
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example", "b.example", "c.example"),
                merged = listOf(a, b, c),
                current = custom,
            )

        assertEquals(a, result)
    }

    @Test
    fun loadedListContainingCurrentDoesNotReset() {
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example", "b.example", "c.example"),
                merged = listOf(a, b, c),
                current = b,
            )

        assertNull(result)
    }
}

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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Telling two coordinators apart on the discovery list.
 *
 * An announced name is the coordinator's own word for itself and collides
 * constantly: the reference server ships as "My coordinator", so a run against
 * a public relay comes back with a dozen rows carrying that name and nothing
 * else to separate them.
 */
class CordnCoordinatorNameTest {
    @Test
    fun `a name nobody else uses is left alone`() {
        val names = listOf("cordn-net", "My coordinator")

        assertEquals("cordn-net", disambiguate("cordn-net", "aabbccdd11223344", names))
    }

    @Test
    fun `a colliding name gains its key`() {
        val names = listOf("My coordinator", "My coordinator", "cordn-net")

        assertEquals("My coordinator · aabbccdd", disambiguate("My coordinator", "aabbccdd11223344", names))
    }

    @Test
    fun `two that collide get different suffixes`() {
        val names = listOf("My coordinator", "My coordinator")

        val first = disambiguate("My coordinator", "aaaaaaaa11112222", names)
        val second = disambiguate("My coordinator", "bbbbbbbb33334444", names)

        assertEquals("My coordinator · aaaaaaaa", first)
        assertEquals("My coordinator · bbbbbbbb", second)
    }
}

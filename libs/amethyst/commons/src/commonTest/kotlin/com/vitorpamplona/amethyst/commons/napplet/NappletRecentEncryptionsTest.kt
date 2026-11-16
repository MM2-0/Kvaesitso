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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NappletRecentEncryptionsTest {
    private val alice = "a".repeat(64)

    @Test
    fun findsWhatItJustEncrypted() {
        val memory = NappletRecentEncryptions(now = { 1_000 })
        memory.record("cipher1", alice, "hello")

        val entry = memory.lookup("cipher1")
        assertEquals(alice, entry?.recipient)
        assertEquals("hello", entry?.plaintext)
        assertNull(memory.lookup("someOtherCiphertext"))
    }

    @Test
    fun forgetsEntriesOlderThanTheWindow() {
        var clock = 1_000L
        val memory = NappletRecentEncryptions(maxAgeSeconds = 600, now = { clock })
        memory.record("cipher1", alice, "hello")

        clock += 601
        assertNull(memory.lookup("cipher1"))
    }

    @Test
    fun dropsTheOldestOnceFull() {
        val memory = NappletRecentEncryptions(capacity = 2, now = { 1_000 })
        memory.record("c1", alice, "one")
        memory.record("c2", alice, "two")
        memory.record("c3", alice, "three")

        assertNull(memory.lookup("c1"))
        assertEquals("two", memory.lookup("c2")?.plaintext)
        assertEquals("three", memory.lookup("c3")?.plaintext)
    }
}

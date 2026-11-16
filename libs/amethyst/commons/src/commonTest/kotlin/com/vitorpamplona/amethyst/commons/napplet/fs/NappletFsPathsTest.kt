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
package com.vitorpamplona.amethyst.commons.napplet.fs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletFsPathsTest {
    @Test
    fun pathModel() {
        assertTrue(NappletFsPaths.isValid("/"))
        assertTrue(NappletFsPaths.isValid("/picked/photo.png"))
        assertTrue(NappletFsPaths.isValid("/picked/my photo (2).png"))
        assertFalse(NappletFsPaths.isValid("picked/photo.png"))
        assertFalse(NappletFsPaths.isValid("/picked//photo.png"))
        assertFalse(NappletFsPaths.isValid("/picked/"))
        assertFalse(NappletFsPaths.isValid("/picked/./photo.png"))
        assertFalse(NappletFsPaths.isValid("/picked/../etc/passwd"))
        assertFalse(NappletFsPaths.isValid("/picked\\photo.png"))
        assertFalse(NappletFsPaths.isValid("/picked/a\u0000b"))
        assertFalse(NappletFsPaths.isValid("/picked/gnp.\u202Eexe"))
    }

    @Test
    fun namesBecomeOneSafeSegment() {
        assertEquals("a_b_c.png", NappletFsPaths.sanitizeName("a/b\\c.png"))
        assertEquals("evil_gnp.exe", NappletFsPaths.sanitizeName("evil\u202Egnp.exe"))
        assertEquals("file", NappletFsPaths.sanitizeName(".."))
        assertEquals("file", NappletFsPaths.sanitizeName("  "))
        val long = NappletFsPaths.sanitizeName("x".repeat(300) + ".png")
        assertEquals(NappletFsPaths.MAX_NAME_LENGTH, long.length)
        assertTrue(long.endsWith(".png"))
        assertTrue(NappletFsPaths.isValid("/picked/" + NappletFsPaths.sanitizeName("a/\u0007\u2066b")))
    }

    @Test
    fun repeatedNamesGetNumbered() {
        val taken = setOf("/picked/photo.png", "/picked/Photo (2).png")
        assertEquals("/picked/photo (3).png", NappletFsPaths.pickedPath("photo.png", taken))
        assertEquals("/picked/notes", NappletFsPaths.pickedPath("notes", emptySet()))
        assertEquals("/picked/notes (2)", NappletFsPaths.pickedPath("notes", setOf("/picked/notes")))
    }
}

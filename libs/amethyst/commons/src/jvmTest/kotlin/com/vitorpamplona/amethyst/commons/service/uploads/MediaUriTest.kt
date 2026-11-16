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
package com.vitorpamplona.amethyst.commons.service.uploads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MediaUriTest {
    @Test
    fun lastSegmentOfPathsAndUrls() {
        assertEquals("song.mp3", StringMediaUri("/home/me/music/song.mp3").lastPathSegmentOrNull())
        assertEquals("My Song.mp3", StringMediaUri("file:///home/me/My%20Song.mp3").lastPathSegmentOrNull())
        assertEquals("café.png", StringMediaUri("https://host/a/caf%C3%A9.png?x=1#y").lastPathSegmentOrNull())
        assertEquals("clip.mp4", StringMediaUri("C:\\Users\\me\\clip.mp4").lastPathSegmentOrNull())
        assertEquals("dir", StringMediaUri("https://host/dir/").lastPathSegmentOrNull())
        assertEquals("100%", StringMediaUri("/tmp/100%").lastPathSegmentOrNull())
    }

    @Test
    fun noPathIsNull() {
        assertNull(StringMediaUri("content://host").lastPathSegmentOrNull())
        assertNull(StringMediaUri("https://host/").lastPathSegmentOrNull())
        assertNull(StringMediaUri("").lastPathSegmentOrNull())
    }

    @Test
    fun comparesByValue() {
        assertEquals(StringMediaUri("/a/b.png"), StringMediaUri("/a/b.png"))
        assertEquals(StringMediaUri("/a/b.png").hashCode(), StringMediaUri("/a/b.png").hashCode())
        assertNotEquals(StringMediaUri("/a/b.png"), StringMediaUri("/a/c.png"))
    }
}

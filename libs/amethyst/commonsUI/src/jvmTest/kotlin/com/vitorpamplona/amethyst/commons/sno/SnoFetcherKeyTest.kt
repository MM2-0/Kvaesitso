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
package com.vitorpamplona.amethyst.commons.sno

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What Coil's memory cache is allowed to treat as the same picture.
 *
 * The interesting case is the one DECK-0003 §1.3a creates: an object whose
 * `colors` index a palette in another event is drawn against the built-in until
 * that event arrives (§1.3b), so a single event id produces two different
 * pictures. A key that names only the id would serve the first of them forever.
 */
class SnoFetcherKeyTest {
    private fun payload(colors: String): SnoPayload =
        SnoParser
            .parse(
                """{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":$colors,"faces":[[0,1,2]]}""",
            ).payloadOrNull()!!

    private fun render(payload: SnoPayload) = SnoObjectToRender(payload, eventId = "abc", size = 96)

    @Test
    fun theSameObjectInTheSameViewIsTheSamePicture() {
        assertEquals(render(payload("[238,238,238]")).cacheKey, render(payload("[238,238,238]")).cacheKey)
    }

    @Test
    fun repaintingTheSameEventInAnotherPaletteIsADifferentPicture() {
        assertNotEquals(
            render(payload("[238,238,238]")).cacheKey,
            render(payload("[225,225,225]")).cacheKey,
            "the same id in different colours must not be served from one cache entry",
        )
    }

    @Test
    fun aColourOnAFaceCountsToo() {
        // §1.4a's facecolors replace the fill without touching `colors`, so a
        // key built from the vertex colours alone would miss them entirely.
        val plain = payload("[238,238,238]")
        val flat =
            SnoParser
                .parse(
                    """{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,1,2]],"facecolors":[225]}""",
                ).payloadOrNull()!!
        assertNotEquals(render(plain).cacheKey, render(flat).cacheKey)
    }

    @Test
    fun theViewAndTheSizeStillSeparatePictures() {
        val p = payload("[238,238,238]")
        assertNotEquals(render(p).cacheKey, SnoObjectToRender(p, "abc", 192).cacheKey)
        assertNotEquals(render(p).cacheKey, SnoObjectToRender(p, "abc", 96, yawDegrees = 90f).cacheKey)
        assertNotEquals(render(p).cacheKey, SnoObjectToRender(p, "def", 96).cacheKey)
    }
}

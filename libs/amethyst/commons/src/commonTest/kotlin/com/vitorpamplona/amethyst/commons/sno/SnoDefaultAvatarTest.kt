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

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoMode
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The placeholder an avatar with no shape is drawn as.
 *
 * Asserted as geometry rather than as a picture, because a hand-written face
 * list is exactly the kind of thing that comes out looking almost right: an
 * icosahedron is twelve corners, thirty edges and twenty faces, every corner on
 * five of them and every edge on two, and a transposed index breaks one of
 * those without breaking the others.
 */
class SnoDefaultAvatarTest {
    private val payload = SnoDefaultAvatar.payload

    @Test
    fun itIsAnIcosahedron() {
        assertEquals(12, payload.vertexCount)
        assertEquals(20, payload.faceCount)
        assertEquals(SnoMode.LINES, payload.mode, "a placeholder should read as a wireframe, not as something somebody made")
    }

    @Test
    fun everyEdgeJoinsTwoFacesAndEveryCornerFive() {
        val onEdge = HashMap<Long, Int>()
        val onCorner = IntArray(payload.vertexCount)
        for (face in 0 until payload.faceCount) {
            val corners = intArrayOf(payload.faces[face * 3], payload.faces[face * 3 + 1], payload.faces[face * 3 + 2])
            for (corner in corners) onCorner[corner]++
            for (i in 0..2) {
                val a = corners[i]
                val b = corners[(i + 1) % 3]
                val key = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()
                onEdge[key] = (onEdge[key] ?: 0) + 1
            }
        }
        assertEquals(30, onEdge.size, "an icosahedron has thirty edges")
        assertTrue(onEdge.values.all { it == 2 }, "every edge should join exactly two faces")
        assertTrue(onCorner.all { it == 5 }, "every corner should be on five faces")
    }

    @Test
    fun everyEdgeIsTheSameLength() {
        // What actually makes it regular. The corners are on the lattice and
        // the golden ratio is not, so the edges differ by the rounding and by
        // nothing else: 194 ticks against 1.618034 units is 0.02% out.
        var shortest = Double.MAX_VALUE
        var longest = 0.0
        for (face in 0 until payload.faceCount) {
            for (i in 0..2) {
                val a = payload.faces[face * 3 + i]
                val b = payload.faces[face * 3 + (i + 1) % 3]
                var square = 0.0
                for (axis in 0..2) {
                    val d = (payload.tickAt(a, axis) - payload.tickAt(b, axis)).toDouble()
                    square += d * d
                }
                val length = sqrt(square)
                if (length < shortest) shortest = length
                if (length > longest) longest = length
            }
        }
        assertTrue(longest / shortest < 1.001, "edges ran $shortest to $longest ticks")
        // And the edge of an icosahedron of short radius 1 is 2 units.
        assertTrue(abs(shortest - 2.0 * SnoPayload.TICKS_PER_UNIT) < 1.0, "an edge should be two units, was $shortest ticks")
    }

    @Test
    fun itDrawsSomethingAndFitsTheFrame() {
        val size = 64
        val pixels = SnoRasterizer.render(payload, size, size)
        assertTrue(pixels.count { it != 0 } > 100, "a wireframe icosahedron should light a good many pixels")
        // Nothing may spill past the margin the projection leaves.
        for (x in 0 until size) {
            assertEquals(0, pixels[x], "the top row should be clear")
            assertEquals(0, pixels[(size - 1) * size + x], "the bottom row should be clear")
        }
    }
}

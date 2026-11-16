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
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inspection drawing DECK-0003 §4 permits: "A client MAY light an object
 * instead, and many will."
 *
 * Two things have to hold for that to be worth doing. Every face must wind
 * outward first, or a stamped block comes out half black; and the light must be
 * per face and flat, from the triangle's own corners, which §4 requires in as
 * many words.
 */
class SnoLightingTest {
    private val red = 0xFFFF0000.toInt()

    /** Three numbers per row, written out rather than nested, so the shape reads. */
    private fun numbers(text: String) =
        text
            .trim()
            .split(WHITESPACE)
            .map { it.toInt() }
            .toIntArray()

    /**
     * A box on the lattice, as twelve triangles wound outward — except for the
     * ones named in [reversed], which are handed back inward, the way half of a
     * stamped block's triangles arrive.
     */
    private fun box(
        bottom: Int = -1,
        top: Int = 1,
        reversed: Set<Int> = emptySet(),
    ): SnoPayload {
        val t = SnoPayload.TICKS_PER_UNIT
        // The eight corners of the unit cube, as 0 or 1 on each axis.
        val cube = numbers("0 0 0  1 0 0  1 1 0  0 1 0  0 0 1  1 0 1  1 1 1  0 1 1")
        val positions =
            IntArray(cube.size) {
                when (it % 3) {
                    1 -> if (cube[it] == 0) bottom else top
                    else -> if (cube[it] == 0) -1 else 1
                } * t
            }
        val faces =
            numbers(
                """
                4 5 6   4 6 7
                0 3 2   0 2 1
                1 2 6   1 6 5
                0 4 7   0 7 3
                3 7 6   3 6 2
                0 1 5   0 5 4
                """,
            )
        for (face in reversed) {
            val swap = faces[face * 3 + 1]
            faces[face * 3 + 1] = faces[face * 3 + 2]
            faces[face * 3 + 2] = swap
        }
        return payload("box", positions, faces, extent = 8)
    }

    private fun payload(
        name: String,
        positions: IntArray,
        faces: IntArray,
        extent: Int,
    ) = SnoPayload(
        version = 2,
        name = name,
        unit = 0,
        extent = extent,
        mode = SnoMode.SOLID,
        positions = positions,
        colors = IntArray(positions.size / 3) { red },
        faces = faces,
        faceColors = null,
        paletteRef = SnoPaletteRef.BuiltIn,
        up = false,
        spin = 0,
    )

    /** The face's normal, taken from its own three corners as §4 requires. */
    private fun normalOf(
        payload: SnoPayload,
        faces: IntArray,
        face: Int,
    ): FloatArray {
        val a = faces[face * 3] * 3
        val b = faces[face * 3 + 1] * 3
        val c = faces[face * 3 + 2] * 3
        val abx = (payload.positions[b] - payload.positions[a]).toFloat()
        val aby = (payload.positions[b + 1] - payload.positions[a + 1]).toFloat()
        val abz = (payload.positions[b + 2] - payload.positions[a + 2]).toFloat()
        val acx = (payload.positions[c] - payload.positions[a]).toFloat()
        val acy = (payload.positions[c + 1] - payload.positions[a + 1]).toFloat()
        val acz = (payload.positions[c + 2] - payload.positions[a + 2]).toFloat()
        return floatArrayOf(aby * acz - abz * acy, abz * acx - abx * acz, abx * acy - aby * acx)
    }

    /** Whether this face turns its back on the middle of the object. */
    private fun pointsOutward(
        payload: SnoPayload,
        faces: IntArray,
        face: Int,
    ): Boolean {
        val normal = normalOf(payload, faces, face)
        var away = 0f
        for (axis in 0..2) {
            var middle = 0f
            for (corner in 0..2) middle += payload.positions[faces[face * 3 + corner] * 3 + axis].toFloat()
            away += normal[axis] * (middle / 3f)
        }
        // These objects sit on the origin, so a face's middle is the direction
        // out of the object at that face.
        return away > 0f
    }

    @Test
    fun aBoxWhoseTrianglesDisagreeIsWoundOutward() {
        // Half of a stamped block's triangles wind inward, which never showed
        // while nothing was lit. Every one of them comes back outward.
        val payload = box(reversed = setOf(0, 3, 4, 7, 9, 11))
        val winding = SnoFaceWinding.of(payload)
        for (face in 0 until payload.faceCount) {
            assertTrue(pointsOutward(payload, winding.faces, face), "face $face should face out of the box")
        }
    }

    @Test
    fun aBoxAlreadyWoundOutwardIsLeftAlone() {
        val payload = box()
        val winding = SnoFaceWinding.of(payload)
        assertTrue(winding.faces.contentEquals(payload.faces), "nothing needed turning")
        assertTrue(winding.interior.none { it }, "a lone box has no face buried in a join")
        // And the payload's own array is untouched, whatever the pass decides.
        assertEquals(4, payload.faces[0])
    }

    @Test
    fun aLitBoxOfOneColourIsNoLongerAFlatHexagon() {
        // The whole reason the switch exists: unlit, a box of a single colour is
        // one flat shape however far you turn it, because every pixel of it is
        // that colour.
        val payload = box()
        val flat = SnoRasterizer.render(payload, 64, 64)
        val lit = SnoRasterizer.render(payload, 64, 64, lighting = SnoLighting.of(payload))

        val flatShades = flat.filter { it ushr 24 == 0xFF }.toSet()
        val litShades = lit.filter { it ushr 24 == 0xFF }.toSet()
        assertEquals(1, flatShades.size, "unlit, every pixel of a one-colour box is the same colour")
        assertTrue(litShades.size >= 3, "lit, each of the three visible faces takes its own shade: $litShades")
    }

    @Test
    fun theLightNeverClipsAndNeverGoesOut() {
        // The three lights are scaled so that a face square-on to the key shows
        // its colour exactly: nothing washes out to white, and the darkest face
        // keeps enough of its colour to read.
        val payload = box(reversed = setOf(1, 2, 5, 8))
        val lighting = SnoLighting.of(payload)
        for (face in 0 until payload.faceCount) {
            assertTrue(lighting.outside[face] > 0.3f, "face $face outside went dark: ${lighting.outside[face]}")
            assertTrue(lighting.outside[face] <= 1f, "face $face outside clipped: ${lighting.outside[face]}")
            assertTrue(
                lighting.inside[face] < lighting.outside[face],
                "the inside of face $face should be the darker side: ${lighting.inside[face]} against ${lighting.outside[face]}",
            )
        }
    }

    @Test
    fun theSquareInsideAJoinIsNotDrawn() {
        // A block stacked on a block leaves a square between them with faces on
        // both sides of it. Lit, it would only fight the face it sits against.
        val stacked = merge(box(bottom = -2, top = 0), box(bottom = 0, top = 2))
        val winding = SnoFaceWinding.of(stacked)

        val buried = (0 until stacked.faceCount).filter { winding.interior[it] }
        assertEquals(4, buried.size, "the join is two triangles from each block: $buried")
        for (face in buried) {
            for (corner in 0..2) {
                assertEquals(0, stacked.positions[winding.faces[face * 3 + corner] * 3 + 1], "the join lies at y = 0")
            }
        }
    }

    @Test
    fun aFlatPlateFarFromTheOriginStillFacesUp() {
        // A patch whose rays cross nothing has no vote, and falls back to its
        // signed volume and then, when that is zero too, to facing up. The
        // volume is the one the divergence theorem gives, and that is only
        // independent of where it is measured from for a *closed* surface: for
        // an open plate measured from the origin it is the volume of the cone
        // between the two, which is large and carries the sign of whichever
        // side of the origin the plate lies on. A plate would then be lit on
        // its upper face above the origin and on its lower face below it — the
        // same object drawn two ways, decided by where §1.8 let the author put
        // it. Measured about the patch's own middle that cone is flat, the
        // volume is zero, and the plate falls through to facing up wherever it
        // lies.
        val t = SnoPayload.TICKS_PER_UNIT
        val plate =
            payload(
                "plate",
                // A two-unit square lying flat, in the far corner of the lattice
                // and below the origin, wound so that it starts out facing down.
                IntArray(12) { numbers("60 -62 60   62 -62 60   62 -62 62   60 -62 62")[it] * t },
                numbers("0 1 2   0 2 3"),
                extent = 64,
            )
        val winding = SnoFaceWinding.of(plate)
        for (face in 0 until plate.faceCount) {
            assertTrue(normalOf(plate, winding.faces, face)[1] > 0f, "face $face of a plate should end up facing up")
        }
        assertTrue(winding.interior.none { it }, "a plate has nothing buried in it")
    }

    /** Two objects in one payload, as a bag of stamps would be. */
    private fun merge(
        first: SnoPayload,
        second: SnoPayload,
    ) = payload(
        "stack",
        first.positions + second.positions,
        first.faces + IntArray(second.faces.size) { second.faces[it] + first.vertexCount },
        extent = 8,
    )

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

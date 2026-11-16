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

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.math.sqrt

/**
 * How much light falls on each side of each face of an object, for the
 * inspection drawing.
 *
 * DECK-0003 §4 makes **unlit** the default reading of an SNO and that is what a
 * feed shows, so an object looks the same everywhere it is quoted. The same
 * section then says a client "MAY light an object instead, and many will,
 * because a lit object sits better in a lit scene", on two conditions: normals
 * are per face and flat, from the triangle's own vertices, and no reader
 * synthesises smooth ones by averaging across a shared vertex. Both are met
 * here — there is one number per face and it comes from that face's cross
 * product — and no vertex moves, so §4's "MUST NOT invent geometry" is intact.
 *
 * This is the shading the reference workshop's bench uses while a shard is
 * being built, and it exists for the same reason: unlit, a cube of one colour
 * is a flat hexagon, and an object you are turning over to understand is the
 * one case where the light is the information. Two things make that legible:
 *
 * - **Every face is wound outward first** ([SnoFaceWinding]), because half the
 *   triangles of a stamped block wind inward and lighting them by the normal
 *   they arrived with would make a solid block half black.
 * - **The inside of a face is drawn darker** ([INSIDE]), lit by its own reversed
 *   normal, so an open shape shows that it is open and a missing face reads as
 *   a hole rather than as a slightly odd colour.
 *
 * Nothing here depends on the viewing angle: the lights are fixed to the object
 * the way the bench's are fixed in its world, so turning the object moves the
 * light across it — which is the whole point — and the shade of a given face is
 * computed once and reused for every frame of a turn.
 */
class SnoLighting(
    /** Three vertex indices per face, wound outward. */
    val faces: IntArray,
    /** Faces buried inside a join, which the drawing leaves out. */
    val interior: BooleanArray,
    /** What to multiply a face's colour by when its outside is the side you see. */
    val outside: FloatArray,
    /** The same for its inside, already darkened by [INSIDE]. */
    val inside: FloatArray,
) {
    companion object {
        /**
         * The bench's three lights — `ambientLight 0.4`, a key at `0.9` and a
         * dim fill from behind at `0.6` — divided by the 1.3 a face square-on
         * to the key receives, so that face shows its own colour exactly and
         * no face anywhere on the sphere clips to white. The brightest a normal
         * can reach with both lights is 0.948, the dimmest 0.308.
         */
        private const val AMBIENT = 0.4f / 1.3f
        private const val KEY = 0.9f / 1.3f
        private const val FILL = 0.6f / 1.3f

        /**
         * The reference's two light positions carried over as directions, not
         * as coordinates: they are stated in the bench's render space and
         * relative to *its* opening camera, so what is reproduced here is where
         * they sit relative to the view — the key a little above the line of
         * sight and slightly to the right, the fill high behind the left
         * shoulder — planted in model space at this renderer's own opening
         * angle ([SnoRasterizer.DEFAULT_YAW_DEGREES], `DEFAULT_PITCH_DEGREES`).
         * Copying the raw positions instead would have put the key behind the
         * object, because the two viewers do not open on the same side of it.
         */
        private val KEY_DIRECTION = floatArrayOf(-0.3667f, 0.0175f, 0.9302f)
        private val FILL_DIRECTION = floatArrayOf(-0.2573f, 0.7696f, -0.5844f)

        /**
         * How much of its colour a face keeps when you are looking at its
         * inside: the `#6a6a6a` the reference multiplies the inside mesh by.
         */
        private const val INSIDE = 0x6A / 255f

        fun of(payload: SnoPayload): SnoLighting {
            val winding = SnoFaceWinding.of(payload)
            val faceCount = payload.faceCount
            val outside = FloatArray(faceCount)
            val inside = FloatArray(faceCount)

            for (face in 0 until faceCount) {
                val a = winding.faces[face * 3] * 3
                val b = winding.faces[face * 3 + 1] * 3
                val c = winding.faces[face * 3 + 2] * 3
                // The one conversion from the exact lattice to floats (§1.2),
                // and flat per face as §4 requires: this triangle's own corners
                // and nothing averaged in from a neighbour.
                val abx = (payload.positions[b] - payload.positions[a]).toFloat()
                val aby = (payload.positions[b + 1] - payload.positions[a + 1]).toFloat()
                val abz = (payload.positions[b + 2] - payload.positions[a + 2]).toFloat()
                val acx = (payload.positions[c] - payload.positions[a]).toFloat()
                val acy = (payload.positions[c + 1] - payload.positions[a + 1]).toFloat()
                val acz = (payload.positions[c + 2] - payload.positions[a + 2]).toFloat()

                var nx = aby * acz - abz * acy
                var ny = abz * acx - abx * acz
                var nz = abx * acy - aby * acx
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                if (length > 0f) {
                    nx /= length
                    ny /= length
                    nz /= length
                } else {
                    ny = 1f
                }

                outside[face] = shade(nx, ny, nz)
                inside[face] = shade(-nx, -ny, -nz) * INSIDE
            }

            return SnoLighting(winding.faces, winding.interior, outside, inside)
        }

        /** Lambert: the ambient, plus each light by how squarely the face meets it. */
        private fun shade(
            nx: Float,
            ny: Float,
            nz: Float,
        ): Float {
            val key = nx * KEY_DIRECTION[0] + ny * KEY_DIRECTION[1] + nz * KEY_DIRECTION[2]
            val fill = nx * FILL_DIRECTION[0] + ny * FILL_DIRECTION[1] + nz * FILL_DIRECTION[2]
            val light = AMBIENT + KEY * maxOf(key, 0f) + FILL * maxOf(fill, 0f)
            return if (light > 1f) 1f else light
        }
    }
}

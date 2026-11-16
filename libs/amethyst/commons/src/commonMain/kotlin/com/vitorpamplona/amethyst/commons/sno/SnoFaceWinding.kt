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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A copy of an object's faces wound so that every one turns its outside out,
 * plus which of them are buried inside a join.
 *
 * Nothing the wire carries says which way round a face is: DECK-0003 §1.4 makes
 * winding order meaningless and §4 draws a face two-sided, so an object whose
 * triangles disagree is indistinguishable from one whose triangles agree — as
 * long as nothing is lit. The moment a reader lights the object (§4 permits it,
 * "and many will") the disagreement becomes the whole picture: half the faces of
 * a stamped block wind inward, and a lit block with half its faces dark is not a
 * block. So lighting needs this pass first.
 *
 * The method is the one `sno-core`'s `orient.ts` arrives at (MIT; ported, not
 * copied), and it is in two halves because one is not enough:
 *
 * 1. **Faces that share a clean edge are made to agree.** Two consistently wound
 *    faces run their shared edge in opposite directions, so a face reached
 *    across an edge it runs the *same* way is flipped. That groups the faces
 *    into patches without deciding which way a patch faces. An edge carrying
 *    three or more faces is where solids touch and carries no agreement across
 *    it — passing agreement through one turns a whole side of a shape inward,
 *    depending only on which face the walk happened to reach first.
 * 2. **Each patch is then turned as a whole by what its faces can see.** A ray
 *    from a face's middle along its normal crosses the rest of the object an
 *    even number of times when the normal points out of a closed solid and an
 *    odd number when it points in. The patch goes whichever way most of its
 *    area votes; a patch whose rays cross nothing — a flat plate, an open
 *    shell — falls back to its signed volume, and failing that to facing up.
 *
 * A face whose rays cross an odd number of faces *both* ways is inside the
 * solid: the square between a block stacked on a block. It is reported in
 * [interior] so a lit drawing can leave it out, where it would only fight the
 * face it sits against.
 *
 * Positions are read straight from the integer lattice into floats here, which
 * is the one conversion §1.2 allows, and the object is never mirrored on the
 * way, so the handedness this decides is the handedness the rasteriser draws.
 */
class SnoFaceWinding(
    /** Three vertex indices per face, rewound; the payload's own array is untouched. */
    val faces: IntArray,
    /** One flag per face: true when it sits inside the solid with faces on both sides. */
    val interior: BooleanArray,
) {
    companion object {
        /** Below this a triangle has no area and its normal is noise. */
        private const val EPSILON = 1e-9f

        /** A ray that meets a face nearer than this started on it. */
        private const val SELF = 1e-5f

        /**
         * How small a signed volume has to be, against the patch's area times
         * its reach, before the patch counts as open rather than closed.
         */
        private const val FLAT = 1e-3f

        /**
         * A slant added to every ray, so that on the axis-aligned shapes an
         * author actually builds a ray crosses faces rather than running down
         * the edge between two of them, where a hit is a coin toss.
         */
        private const val SLANT_X = 0.0173f
        private const val SLANT_Y = 0.0311f
        private const val SLANT_Z = 0.0237f

        fun of(payload: SnoPayload): SnoFaceWinding {
            val faceCount = payload.faceCount
            val faces = payload.faces.copyOf()
            val interior = BooleanArray(faceCount)
            if (faceCount == 0) return SnoFaceWinding(faces, interior)

            val points = FloatArray(payload.vertexCount * 3)
            for (i in points.indices) points[i] = payload.positions[i].toFloat()

            val normals = FloatArray(faceCount * 3)
            val areas = FloatArray(faceCount)
            val forward = IntArray(faceCount)
            val backward = IntArray(faceCount)

            for (face in 0 until faceCount) {
                measure(points, faces, face, normals, areas)
                val dx = normals[face * 3] + SLANT_X
                val dy = normals[face * 3 + 1] + SLANT_Y
                val dz = normals[face * 3 + 2] + SLANT_Z
                val mx = middle(points, faces, face, 0)
                val my = middle(points, faces, face, 1)
                val mz = middle(points, faces, face, 2)
                forward[face] = crossings(points, faces, faceCount, face, mx, my, mz, dx, dy, dz)
                backward[face] = crossings(points, faces, faceCount, face, mx, my, mz, -dx, -dy, -dz)
                interior[face] = forward[face] % 2 == 1 && backward[face] % 2 == 1
            }

            turnPatches(points, payload.faces, faces, faceCount, areas, forward, backward, interior)
            return SnoFaceWinding(faces, interior)
        }

        /** The face's unit normal into [normals] and half its length into [areas]. */
        private fun measure(
            points: FloatArray,
            faces: IntArray,
            face: Int,
            normals: FloatArray,
            areas: FloatArray,
        ) {
            val a = faces[face * 3] * 3
            val b = faces[face * 3 + 1] * 3
            val c = faces[face * 3 + 2] * 3
            val abx = points[b] - points[a]
            val aby = points[b + 1] - points[a + 1]
            val abz = points[b + 2] - points[a + 2]
            val acx = points[c] - points[a]
            val acy = points[c + 1] - points[a + 1]
            val acz = points[c + 2] - points[a + 2]
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            areas[face] = length * 0.5f
            if (length > 0f) {
                normals[face * 3] = nx / length
                normals[face * 3 + 1] = ny / length
                normals[face * 3 + 2] = nz / length
            } else {
                normals[face * 3 + 1] = 1f
            }
        }

        private fun middle(
            points: FloatArray,
            faces: IntArray,
            face: Int,
            axis: Int,
        ): Float =
            (
                points[faces[face * 3] * 3 + axis] +
                    points[faces[face * 3 + 1] * 3 + axis] +
                    points[faces[face * 3 + 2] * 3 + axis]
            ) / 3f

        /** How many other faces a ray from this point along this direction crosses. */
        @Suppress("detekt.LongParameterList")
        private fun crossings(
            points: FloatArray,
            faces: IntArray,
            faceCount: Int,
            skip: Int,
            ox: Float,
            oy: Float,
            oz: Float,
            dx: Float,
            dy: Float,
            dz: Float,
        ): Int {
            var hits = 0
            for (face in 0 until faceCount) {
                if (face == skip) continue
                if (hit(points, faces, face, ox, oy, oz, dx, dy, dz)) hits++
            }
            return hits
        }

        /**
         * Möller–Trumbore: whether the ray meets this face ahead of its start.
         */
        @Suppress("detekt.LongParameterList")
        private fun hit(
            points: FloatArray,
            faces: IntArray,
            face: Int,
            ox: Float,
            oy: Float,
            oz: Float,
            dx: Float,
            dy: Float,
            dz: Float,
        ): Boolean {
            val a = faces[face * 3] * 3
            val b = faces[face * 3 + 1] * 3
            val c = faces[face * 3 + 2] * 3
            val e1x = points[b] - points[a]
            val e1y = points[b + 1] - points[a + 1]
            val e1z = points[b + 2] - points[a + 2]
            val e2x = points[c] - points[a]
            val e2y = points[c + 1] - points[a + 1]
            val e2z = points[c + 2] - points[a + 2]

            val px = dy * e2z - dz * e2y
            val py = dz * e2x - dx * e2z
            val pz = dx * e2y - dy * e2x
            val determinant = e1x * px + e1y * py + e1z * pz
            if (abs(determinant) < EPSILON) return false
            val inverse = 1f / determinant

            val tx = ox - points[a]
            val ty = oy - points[a + 1]
            val tz = oz - points[a + 2]
            val u = (tx * px + ty * py + tz * pz) * inverse
            if (u < 0f || u > 1f) return false

            val qx = ty * e1z - tz * e1y
            val qy = tz * e1x - tx * e1z
            val qz = tx * e1y - ty * e1x
            val v = (dx * qx + dy * qy + dz * qz) * inverse
            if (v < 0f || u + v > 1f) return false

            return (e2x * qx + e2y * qy + e2z * qz) * inverse > SELF
        }

        /**
         * Group the faces into patches across their clean edges, then turn each
         * patch as a whole.
         */
        @Suppress("detekt.LongParameterList")
        private fun turnPatches(
            points: FloatArray,
            original: IntArray,
            faces: IntArray,
            faceCount: Int,
            areas: FloatArray,
            forward: IntArray,
            backward: IntArray,
            interior: BooleanArray,
        ) {
            val byEdge = HashMap<Long, MutableList<Int>>(faceCount * 3)
            for (face in 0 until faceCount) {
                forEachEdge(faces, face) { from, to ->
                    byEdge.getOrPut(edgeKey(from, to)) { ArrayList(2) }.add(face)
                }
            }

            val seen = BooleanArray(faceCount)
            val patch = ArrayList<Int>()
            val queue = ArrayDeque<Int>()
            for (start in 0 until faceCount) {
                if (seen[start]) continue
                seen[start] = true
                patch.clear()
                queue.clear()
                queue.addLast(start)
                while (queue.isNotEmpty()) {
                    val face = queue.removeFirst()
                    patch.add(face)
                    forEachEdge(faces, face) { from, to ->
                        val on = byEdge[edgeKey(from, to)]
                        // Only an edge with exactly two faces on it carries
                        // agreement; three or more is where solids touch.
                        if (on != null && on.size == 2) {
                            for (other in on) {
                                if (other == face || seen[other]) continue
                                // Two faces that agree run a shared edge in
                                // opposite directions, so one that runs it the
                                // same way is turned over.
                                if (runsEdge(faces, other, from, to)) flip(faces, other)
                                seen[other] = true
                                queue.addLast(other)
                            }
                        }
                    }
                }
                if (facesInward(points, original, faces, patch, areas, forward, backward, interior)) {
                    for (face in patch) flip(faces, face)
                }
            }
        }

        /**
         * Whether this patch, as the walk left it, has its back to the outside.
         *
         * A face's crossings were counted as it was wound to begin with, so a
         * face the walk turned over votes the other way from how it was
         * counted; the votes are weighted by area, since a patch is decided by
         * most of its surface rather than most of its triangles.
         */
        @Suppress("detekt.LongParameterList")
        private fun facesInward(
            points: FloatArray,
            original: IntArray,
            faces: IntArray,
            patch: List<Int>,
            areas: FloatArray,
            forward: IntArray,
            backward: IntArray,
            interior: BooleanArray,
        ): Boolean {
            var vote = 0f
            for (face in patch) {
                if (interior[face]) continue
                val turned = if (runsEdge(faces, face, original[face * 3], original[face * 3 + 1])) 1f else -1f
                val outward =
                    if (forward[face] % 2 == 0 && backward[face] % 2 == 1) {
                        1f
                    } else if (forward[face] % 2 == 1 && backward[face] % 2 == 0) {
                        -1f
                    } else {
                        0f
                    }
                vote += turned * outward * areas[face]
            }
            if (vote != 0f) return vote < 0f

            // Nothing to see: closed by its own volume, or flat and facing up.
            //
            // The volume is summed about the patch's own middle rather than
            // about the origin. A lattice runs to 7680 ticks out and the terms
            // of this sum are cubes of that, so a flat plate far from the
            // origin leaves a cancellation residue in a float larger than any
            // absolute threshold could tell from a real volume; measured about
            // the middle, the terms are the size of the patch and the residue
            // goes with them. A closed surface's signed volume does not depend
            // on where it is measured from, so nothing is given up.
            var middleX = 0f
            var middleY = 0f
            var middleZ = 0f
            for (face in patch) {
                for (corner in 0..2) {
                    val v = faces[face * 3 + corner] * 3
                    middleX += points[v]
                    middleY += points[v + 1]
                    middleZ += points[v + 2]
                }
            }
            val corners = patch.size * 3
            middleX /= corners
            middleY /= corners
            middleZ /= corners

            var volume = 0f
            var nx = 0f
            var ny = 0f
            var nz = 0f
            var reach = 0f
            var twiceArea = 0f
            for (face in patch) {
                val a = faces[face * 3] * 3
                val b = faces[face * 3 + 1] * 3
                val c = faces[face * 3 + 2] * 3
                val aX = points[a] - middleX
                val aY = points[a + 1] - middleY
                val aZ = points[a + 2] - middleZ
                val bX = points[b] - middleX
                val bY = points[b + 1] - middleY
                val bZ = points[b + 2] - middleZ
                val cX = points[c] - middleX
                val cY = points[c + 1] - middleY
                val cZ = points[c + 2] - middleZ
                volume += (aX * (bY * cZ - bZ * cY) - aY * (bX * cZ - bZ * cX) + aZ * (bX * cY - bY * cX)) / 6f
                val abx = bX - aX
                val aby = bY - aY
                val abz = bZ - aZ
                val acx = cX - aX
                val acy = cY - aY
                val acz = cZ - aZ
                val fx = aby * acz - abz * acy
                val fy = abz * acx - abx * acz
                val fz = abx * acy - aby * acx
                nx += fx
                ny += fy
                nz += fz
                twiceArea += sqrt(fx * fx + fy * fy + fz * fz)
                reach = maxOf(reach, abs(aX), abs(aY), abs(aZ))
            }
            // A real volume is the patch's area times its reach; what survives
            // the cancellation in an open one is that times float's own
            // precision. The threshold sits between the two, far from both.
            if (abs(volume) > FLAT * twiceArea * reach) return volume < 0f
            val ax = abs(nx)
            val ay = abs(ny)
            val az = abs(nz)
            return if (ay >= ax && ay >= az) {
                ny < 0f
            } else if (ax >= az) {
                nx < 0f
            } else {
                nz < 0f
            }
        }

        private inline fun forEachEdge(
            faces: IntArray,
            face: Int,
            action: (Int, Int) -> Unit,
        ) {
            val a = faces[face * 3]
            val b = faces[face * 3 + 1]
            val c = faces[face * 3 + 2]
            action(a, b)
            action(b, c)
            action(c, a)
        }

        /** Whether this face runs the edge from -> to in that direction. */
        private fun runsEdge(
            faces: IntArray,
            face: Int,
            from: Int,
            to: Int,
        ): Boolean {
            val a = faces[face * 3]
            val b = faces[face * 3 + 1]
            val c = faces[face * 3 + 2]
            return (a == from && b == to) || (b == from && c == to) || (c == from && a == to)
        }

        private fun flip(
            faces: IntArray,
            face: Int,
        ) {
            val swap = faces[face * 3 + 1]
            faces[face * 3 + 1] = faces[face * 3 + 2]
            faces[face * 3 + 2] = swap
        }

        /** An undirected edge as one number; vertex indices are below 512. */
        private fun edgeKey(
            a: Int,
            b: Int,
        ): Long {
            val low = if (a < b) a else b
            val high = if (a < b) b else a
            return (low.toLong() shl 32) or high.toLong()
        }
    }
}

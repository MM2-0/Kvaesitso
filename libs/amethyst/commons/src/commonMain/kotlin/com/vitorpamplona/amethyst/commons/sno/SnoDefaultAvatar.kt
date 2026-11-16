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

/**
 * The shape an avatar has when its author has not given it one.
 *
 * `CYBERSPACE_V2.md` §8.10 makes a `kind 11333` with empty content the default
 * avatar: it carries no geometry, owes no proof of work, and is what everyone
 * starts as. Drawing nothing for it is technically correct and tells a reader
 * nothing — the note simply vanishes from the feed, which reads as a bug. The
 * reference puts a wireframe icosahedron in its place wherever an avatar is
 * drawn (`scene/AvatarShape.tsx`), so this is that icosahedron, built as a
 * payload so it goes through the same renderer as everything else and needs no
 * second drawing path.
 *
 * **Not an event and not from the wire.** Nothing here was published by
 * anybody, so §1.2's obligation to keep the lattice exact does not apply to how
 * it was derived: the twelve corners of an icosahedron are `(0, ±1, ±φ)`
 * and its rotations, `φ` is irrational, and 194 ticks is the nearest the
 * lattice comes to `φ` units. What §1.2 does still govern is everything
 * downstream, and once these are ticks they are as exact as any other object's.
 *
 * `lines`, because the reference draws the edges rather than the solid: a
 * placeholder should not look like something somebody made.
 */
object SnoDefaultAvatar {
    /** One unit, in ticks — the icosahedron's short radius. */
    private const val ONE = SnoPayload.TICKS_PER_UNIT

    /** The golden ratio in ticks, to the nearest one: `1.618034 * 120`. */
    private const val PHI = 194

    private const val WHITE = 0xFFFFFFFF.toInt()

    val payload: SnoPayload by lazy { build() }

    private fun build(): SnoPayload {
        // The three mutually perpendicular golden rectangles whose corners are
        // an icosahedron: (0, ±1, ±φ) and its two cyclic rotations.
        val positions =
            intArrayOf(
                0,
                ONE,
                PHI,
                0,
                ONE,
                -PHI,
                0,
                -ONE,
                PHI,
                0,
                -ONE,
                -PHI,
                ONE,
                PHI,
                0,
                ONE,
                -PHI,
                0,
                -ONE,
                PHI,
                0,
                -ONE,
                -PHI,
                0,
                PHI,
                0,
                ONE,
                -PHI,
                0,
                ONE,
                PHI,
                0,
                -ONE,
                -PHI,
                0,
                -ONE,
            )
        // The twenty faces: every triple of corners that are one edge apart,
        // each wound so its normal points out of the solid. Derived rather than
        // remembered — a hand-written icosahedron comes out looking almost
        // right, and the test next door is what says whether it did.
        val faces =
            intArrayOf(
                0,
                2,
                8,
                0,
                9,
                2,
                0,
                4,
                6,
                0,
                8,
                4,
                0,
                6,
                9,
                1,
                10,
                3,
                1,
                3,
                11,
                1,
                6,
                4,
                1,
                4,
                10,
                1,
                11,
                6,
                2,
                7,
                5,
                2,
                5,
                8,
                2,
                9,
                7,
                3,
                5,
                7,
                3,
                10,
                5,
                3,
                7,
                11,
                4,
                8,
                10,
                5,
                10,
                8,
                6,
                11,
                9,
                7,
                9,
                11,
            )
        return SnoPayload(
            version = 2,
            name = "",
            // One gibson: the cell an avatar occupies, which is what the
            // reference draws it at.
            unit = 0,
            extent = 2,
            mode = SnoMode.LINES,
            positions = positions,
            colors = IntArray(positions.size / 3) { WHITE },
            faces = faces,
            faceColors = null,
            paletteRef = SnoPaletteRef.BuiltIn,
            up = false,
            spin = 0,
        )
    }
}

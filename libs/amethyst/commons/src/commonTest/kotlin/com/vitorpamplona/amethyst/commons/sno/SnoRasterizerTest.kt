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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rendering decisions DECK-0003 §4 makes, asserted on pixels.
 *
 * §4 decides shading rather than leaving it to taste precisely so that the same
 * object does not look like two objects, so each of its rules gets a test here
 * rather than a comment.
 */
class SnoRasterizerTest {
    private val dim = 64

    private fun parse(json: String): SnoPayload {
        val payload = SnoParser.parse(json).payloadOrNull()
        assertNotNull(payload, "fixture did not parse: $json")
        return payload
    }

    /** A single triangle filling most of the frame, facing the viewer. */
    private fun triangle(
        colors: String = "[238,238,238]",
        extra: String = "",
        mode: String = "solid",
    ) = parse(
        """{"v":2,"name":"t","unit":0,"mode":"$mode","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":$colors,"faces":[[0,1,2]]$extra}""",
    )

    private fun IntArray.at(
        x: Int,
        y: Int,
    ) = this[y * dim + x]

    private fun IntArray.litCount() = count { it != 0 }

    @Test
    fun aSolidTriangleFillsItsMiddleAndLeavesTheCornersAlone() {
        val pixels = SnoRasterizer.render(triangle(), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertEquals(0xFFFF0000.toInt(), pixels.at(dim / 2, dim / 2), "the centre should be the face")
        assertEquals(0, pixels.at(0, 0), "a corner outside the triangle stays transparent")
        assertEquals(0, pixels.at(dim - 1, 0))
    }

    @Test
    fun theBackgroundIsHonoured() {
        val pixels = SnoRasterizer.render(triangle(), dim, dim, yawDegrees = 0f, pitchDegrees = 0f, background = 0xFF101010.toInt())
        assertEquals(0xFF101010.toInt(), pixels.at(0, 0))
        assertEquals(0xFFFF0000.toInt(), pixels.at(dim / 2, dim / 2))
    }

    @Test
    fun vertexColoursInterpolateAcrossAFace() {
        // §4: the default reading is unlit, and a face takes its colour by
        // interpolating its three vertices.
        val pixels = SnoRasterizer.render(triangle(colors = "[238,235,239]"), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val middle = pixels.at(dim / 2, dim / 2)
        val r = middle shr 16 and 0xFF
        val g = middle shr 8 and 0xFF
        val b = middle and 0xFF
        assertTrue(r in 1..254 && g in 1..254 && b in 1..254, "the centre of a red/green/blue triangle should be a mix, was $r,$g,$b")
    }

    @Test
    fun aFaceColourFillsFlatlyAndItsVerticesContributeNothing() {
        // §1.4a: when a face has a colour, that colour fills the whole triangle.
        // The seam between two such faces is exact, which is the point of it.
        val pixels =
            SnoRasterizer.render(
                triangle(colors = "[238,235,239]", extra = ""","facecolors":[225]"""),
                dim,
                dim,
                yawDegrees = 0f,
                pitchDegrees = 0f,
            )
        val lit = pixels.filter { it != 0 }
        assertTrue(lit.isNotEmpty())
        assertEquals(0xFFFFFFFF.toInt(), pixels.at(dim / 2, dim / 2), "the fill should be the face colour, opaque")
        // Colour only: the vertex dots §1.5 lays over every mode feather at
        // their rim, so a pixel at the edge of one carries partial alpha. What
        // §1.4a decides is the colour, and no pixel anywhere shows a trace of
        // the red, green and blue this object's vertices carry.
        assertTrue(lit.all { it and 0xFFFFFF == 0xFFFFFF }, "every covered pixel should be the face colour, flat")
    }

    @Test
    fun bothSidesOfAFaceAreDrawn() {
        // §1.4: a face has no front and no back, and a reader MUST NOT cull on
        // the basis of winding.
        val clockwise = parse("""{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,1,2]]}""")
        val counter = parse("""{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,2,1]]}""")

        val a = SnoRasterizer.render(clockwise, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val b = SnoRasterizer.render(counter, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(a.contentEquals(b), "reversing a face's winding must not change what is drawn")
        assertTrue(a.litCount() > 100)
    }

    @Test
    fun theNearerFaceWinsWhicheverOrderItIsListedIn() {
        // This is what the depth buffer buys over a painter's-algorithm
        // fallback: two coplanar-in-screen triangles at different depths sort
        // correctly no matter how the payload orders them.
        val nearLast =
            parse(
                """{"v":2,"name":"z","unit":0,"mode":"solid",
                   "vertices":[[-4,-4,-2],[4,-4,-2],[0,4,-2],[-4,-4,2],[4,-4,2],[0,4,2]],
                   "colors":[238,238,238,235,235,235],"faces":[[0,1,2],[3,4,5]]}""",
            )
        val nearFirst =
            parse(
                """{"v":2,"name":"z","unit":0,"mode":"solid",
                   "vertices":[[-4,-4,2],[4,-4,2],[0,4,2],[-4,-4,-2],[4,-4,-2],[0,4,-2]],
                   "colors":[235,235,235,238,238,238],"faces":[[0,1,2],[3,4,5]]}""",
            )

        val a = SnoRasterizer.render(nearLast, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val b = SnoRasterizer.render(nearFirst, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertEquals(0xFF00FF00.toInt(), a.at(dim / 2, dim / 2), "the nearer (green) face should win")
        assertEquals(0xFF00FF00.toInt(), b.at(dim / 2, dim / 2), "...in either listing order")
    }

    @Test
    fun pointsModeDrawsTheVerticesAndNotTheTriangle() {
        val pixels = SnoRasterizer.render(triangle(mode = "points"), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        // Three round dots a couple of pixels across, and nothing between them:
        // the whole triangle would be some 1400 pixels.
        assertTrue(pixels.litCount() in 3..60, "three vertices should light three small dots, lit ${pixels.litCount()}")
        assertEquals(0, pixels.at(dim / 2, dim / 2), "points mode must not fill the triangle")
    }

    @Test
    fun theVerticesAreDrawnInEveryMode() {
        // §1.5: "A client SHOULD also draw the vertices as points in every
        // mode". Under a solid they are a hint rather than a second shape —
        // small, and laid over the fill at part alpha — so what shows they are
        // there is that the dot on the apex reaches a little past it, into
        // pixels no fill of this triangle can cover.
        val wide = 512
        val pixels = SnoRasterizer.render(triangle(), wide, wide, yawDegrees = 0f, pitchDegrees = 0f)
        // The apex of the triangle lands just under a thirteenth of the way down.
        val apexRow = (wide * 0.08f).toInt()
        assertEquals(0, pixels[(apexRow - 10) * wide + wide / 2], "well above the apex is empty")
        assertTrue(pixels[apexRow * wide + wide / 2] != 0, "the apex's own vertex dot reaches above the fill")
    }

    @Test
    fun aFaceColourMarksItsVerticesInThatColourToo() {
        // The other half of §1.4a: where a face carries its own colour, that is
        // what the object shows, and the vertex dots over it are that colour as
        // well rather than the three the payload's `colors` still carries.
        val pixels =
            SnoRasterizer.render(
                triangle(colors = "[238,235,239]", extra = ""","facecolors":[225]"""),
                dim,
                dim,
                yawDegrees = 0f,
                pitchDegrees = 0f,
            )
        assertTrue(pixels.filter { it != 0 }.all { it and 0xFFFFFF == 0xFFFFFF }, "no dot should show a vertex colour")
    }

    @Test
    fun linesModeDrawsEachSharedEdgeOnce() {
        // A closed pair of triangles sharing an edge: the outline is drawn, the
        // interior is not, and the shared edge costs one line rather than two.
        val quad =
            parse(
                """{"v":2,"name":"q","unit":0,"mode":"lines",
                   "vertices":[[-4,-4,0],[4,-4,0],[4,4,0],[-4,4,0]],
                   "colors":[225,225,225,225],"faces":[[0,1,2],[0,2,3]]}""",
            )
        val lines = SnoRasterizer.render(quad, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val solid = SnoRasterizer.render(parse(solidQuadJson), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)

        assertTrue(lines.litCount() > 0)
        assertTrue(lines.litCount() * 4 < solid.litCount(), "lines mode must not fill: lit ${lines.litCount()} against a solid ${solid.litCount()}")

        // A point inside the lower triangle and off every edge stays empty...
        assertEquals(0, lines.at(dim / 2 + 13, dim / 2 + 13), "lines mode must not fill")

        // ...while the edge the two triangles share is drawn. In the wire format
        // a face IS a triangle, so that diagonal is a real edge of both faces and
        // belongs in the outline; it is drawn once rather than twice, which is
        // what the shared-edge rule of §1.5 buys.
        assertTrue(lines.at(dim / 2, dim / 2) != 0, "the shared edge should be drawn")
    }

    private val solidQuadJson =
        """{"v":2,"name":"q","unit":0,"mode":"solid",
           "vertices":[[-4,-4,0],[4,-4,0],[4,4,0],[-4,4,0]],
           "colors":[225,225,225,225],"faces":[[0,1,2],[0,2,3]]}"""

    @Test
    fun linesWithNoFacesIsOnePolylineThroughTheVerticesInOrder() {
        val path =
            parse(
                """{"v":2,"name":"p","unit":0,"mode":"lines",
                   "vertices":[[-4,-4,0],[0,4,0],[4,-4,0]],"colors":[225,225,225],"faces":[]}""",
            )
        val pixels = SnoRasterizer.render(path, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(pixels.litCount() > 20, "a polyline of two segments should light a run of pixels")
    }

    @Test
    fun anObjectTooSmallForATriangleIsStillVisible() {
        // §1.5: a client SHOULD also draw the vertices as points in every mode,
        // so that an object remains visible when it is smaller on screen than a
        // triangle.
        val pixels = SnoRasterizer.render(triangle(), 2, 2, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(pixels.any { it != 0 }, "a two-pixel raster should still show something")
    }

    @Test
    fun nothingIsDrawnOutsideTheBuffer() {
        // The limits are the defence, and the buffer is sized from the raster
        // rather than from anything the payload says.
        val pixels = SnoRasterizer.render(triangle(), 17, 5, yawDegrees = 37f, pitchDegrees = -61f)
        assertEquals(17 * 5, pixels.size)
    }

    @Test
    fun aSingleVertexDoesNotCrash() {
        val dot = parse("""{"v":2,"name":"d","unit":0,"mode":"points","vertices":[[0,0,0]],"colors":[225],"faces":[]}""")
        val pixels = SnoRasterizer.render(dot, dim, dim)
        // One vertex is the whole object, so it is drawn at the size the shape
        // profile allows and centred in the frame.
        assertTrue(pixels.litCount() in 1..160, "one vertex, lit ${pixels.litCount()}")
        assertTrue(pixels.at(dim / 2, dim / 2) != 0, "the one vertex should be in the middle")
    }

    @Test
    fun coincidentVerticesDoNotDivideByZero() {
        val degenerate =
            parse(
                """{"v":2,"name":"d","unit":0,"mode":"solid","vertices":[[1,1,1],[1,1,1],[1,1,1]],"colors":[225,225,225],"faces":[[0,1,2]]}""",
            )
        val pixels = SnoRasterizer.render(degenerate, dim, dim)
        assertEquals(dim * dim, pixels.size)
    }

    @Test
    fun theSameObjectRendersIdenticallyEveryTime() {
        val payload = triangle(colors = "[238,235,239]")
        val a = SnoRasterizer.render(payload, dim, dim)
        val b = SnoRasterizer.render(payload, dim, dim)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun rotatingChangesTheDrawingButNotTheModel() {
        val payload = triangle()
        val front = SnoRasterizer.render(payload, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val turned = SnoRasterizer.render(payload, dim, dim, yawDegrees = 60f, pitchDegrees = 0f)
        assertTrue(!front.contentEquals(turned), "a yaw should change the raster")

        // No invented geometry, and nothing moved: the positions are untouched.
        assertEquals(-480, payload.tickAt(0, 0))
        assertEquals(480, payload.tickAt(1, 0))
    }

    /**
     * Reused buffers must draw the same picture as fresh ones.
     *
     * The whole risk of [SnoRasterScratch] is that a frame inherits something
     * the last one left behind — a pixel the background fill missed, a depth
     * still holding the previous angle's surface in front of this one. So this
     * turns the object through a sequence of angles twice, once allocating and
     * once reusing, and every frame has to match: a leak would show up on the
     * second angle onward, never on the first.
     */
    @Test
    fun aReusedBufferDrawsWhatAFreshOneDraws() {
        val payload = triangle(colors = "[238,235,239]")
        val scratch = SnoRasterScratch()
        val background = 0xFF101010.toInt()

        for (yaw in floatArrayOf(0f, 37f, 180f, -95f, 0f)) {
            val fresh = SnoRasterizer.render(payload, dim, dim, yawDegrees = yaw, background = background)
            val reused =
                SnoRasterizer.render(payload, dim, dim, yawDegrees = yaw, background = background, scratch = scratch)
            assertTrue(fresh.contentEquals(reused), "a reused buffer differs from a fresh one at yaw $yaw")
        }
    }

    /** A drag ends and the raster goes back to full size; the buffers must follow. */
    @Test
    fun aReusedBufferFollowsAChangeOfSize() {
        val payload = triangle()
        val scratch = SnoRasterScratch()

        for (size in intArrayOf(dim, dim * 2, 3, dim)) {
            val reused = SnoRasterizer.render(payload, size, size, scratch = scratch)
            assertEquals(size * size, reused.size, "the buffer did not resize to $size")
            assertTrue(reused.contentEquals(SnoRasterizer.render(payload, size, size)))
        }
    }

    /** A lit frame must not leave its shading in the buffer for an unlit one. */
    @Test
    fun aReusedBufferDoesNotCarryLightingOver() {
        val payload = parse(solidQuadJson)
        val scratch = SnoRasterScratch()

        SnoRasterizer.render(payload, dim, dim, lighting = SnoLighting.of(payload), scratch = scratch)
        val unlitAfterLit = SnoRasterizer.render(payload, dim, dim, scratch = scratch).copyOf()

        assertTrue(unlitAfterLit.contentEquals(SnoRasterizer.render(payload, dim, dim)))
    }
}

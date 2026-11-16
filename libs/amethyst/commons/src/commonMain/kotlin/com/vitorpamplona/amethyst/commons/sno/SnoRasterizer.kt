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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws a Simple Nostr Object into a pixel buffer, on the CPU.
 *
 * Software rather than `Canvas.drawVertices`, which is common Compose API and
 * would give interpolated triangles for free, because that call is only
 * hardware-accelerated from API 29 and Amethyst's `minSdk` is 26 — on Android 8
 * and 9 it silently draws nothing. Doing it here also buys a real depth buffer,
 * which a painter's-algorithm fallback does not have and which lattice geometry
 * needs the moment two objects are authored to share an edge, and it makes the
 * whole renderer testable headless, with no device and no screenshot harness.
 *
 * The shading is what DECK-0003 §4 decides, so that the same object does not
 * look like two objects:
 * - **Unlit.** A face takes its colour by interpolating its three vertices and
 *   no light in the scene changes it. This is `KHR_materials_unlit` with a
 *   `COLOR_0` attribute, which is the one-sentence bridge to anyone in glTF.
 * - **Two-sided.** A face has no front and no back: winding order carries no
 *   meaning and nothing is culled by it.
 * - **A face that carries its own colour is filled with it flatly**, and there
 *   is nothing to interpolate or average: the seam between two such faces is
 *   exact, which is the whole purpose of `facecolors`.
 * - **No invented geometry.** No subdivision, no smoothing that moves a vertex,
 *   no hole filling, and no synthesised smooth normals. The lattice is exact and
 *   a renderer that moves a vertex has broken the one guarantee it makes.
 *
 * §4 also permits a client to light an object instead, which is what the
 * `lighting` argument does; see [SnoLighting] for what that changes and why it
 * is offered only where a reader is inspecting one object rather than
 * scrolling past many.
 *
 * Positions arrive as exact integers and are converted to floats here, at the
 * boundary, and nowhere earlier (§1.2).
 */
object SnoRasterizer {
    /** A three-quarter view, which is what shows a small object's shape best. */
    const val DEFAULT_YAW_DEGREES = 30f
    const val DEFAULT_PITCH_DEGREES = -20f

    /** Fraction of the shorter side left empty around the object. */
    private const val MARGIN = 0.08f

    /**
     * Below this many pixels across, an object is too small for a triangle to
     * land, and its vertices go back to being the whole drawing (§1.5).
     */
    private const val TOO_SMALL_FOR_TRIANGLES = 3f

    /**
     * §1.5: "A client SHOULD also draw the vertices as points in every mode, so
     * that an object remains visible when it is smaller on screen than a
     * triangle." The reference viewer keeps two profiles for that and so does
     * this one (ONOSENDAI `scene/pointDisc.ts`): where the vertices *are* the
     * shape they carry its size, and under a solid or a wireframe they are a
     * hint that they are there and must never become a second shape competing
     * with the faces. Each diameter is a length in model units, clamped in
     * pixels at both ends, so a vertex is dust on a large object and still a
     * dot on a tiny one.
     */
    private const val SHAPE_POINT_UNITS = 0.3f
    private const val SHAPE_POINT_MIN_PX = 1.6f
    private const val SHAPE_POINT_MAX_PX = 10f
    private const val HINT_DOT_UNITS = 0.07f
    private const val HINT_DOT_MIN_PX = 0.7f
    private const val HINT_DOT_MAX_PX = 2.4f

    /** How solid a hint dot is over the face it sits on. */
    private const val HINT_DOT_ALPHA = 0.55f

    /**
     * Ticks of slack a vertex dot gets against the depth buffer.
     *
     * A vertex lies exactly on the surface of every face that meets there, and
     * whether the triangle's interpolated depth at that pixel lands a hair in
     * front of the vertex or a hair behind it is a matter of float rounding. A
     * 240th of a unit of bias settles it the only way that is ever wanted, and
     * is far below a pixel at any raster size this draws at.
     */
    private const val DOT_DEPTH_BIAS = 0.5f

    private const val TRANSPARENT = 0

    /** Below this a triangle has no area to fill and its reciprocal is noise. */
    private const val MIN_AREA = 1e-6f

    /** Light on a face when the object is drawn unlit: all of it, unchanged. */
    private const val UNLIT = 1f

    /**
     * @param background an opaque ARGB fill, or 0 for a transparent buffer.
     * @param lighting the light falling on each face, which also carries the
     *   faces wound outward; `null` for §4's default unlit reading.
     * @param scratch buffers to draw into instead of allocating a pair, for a
     *   caller that draws the same object many times over; see [SnoRasterScratch]
     *   for why a turn wants this and why one holder serves one render at a time.
     *   With a scratch the returned array is the holder's own and is overwritten
     *   by the next render, so a caller that keeps the pixels must copy them —
     *   both `PlatformImage.create` implementations do.
     * @return `width * height` ARGB pixels, row-major.
     */
    @Suppress("detekt.LongParameterList")
    fun render(
        payload: SnoPayload,
        width: Int,
        height: Int,
        yawDegrees: Float = DEFAULT_YAW_DEGREES,
        pitchDegrees: Float = DEFAULT_PITCH_DEGREES,
        background: Int = TRANSPARENT,
        lighting: SnoLighting? = null,
        scratch: SnoRasterScratch? = null,
    ): IntArray {
        require(width > 0 && height > 0) { "a raster needs a positive size" }

        val area = width * height
        // A fresh array is cheaper to fill than a reused one — allocation hands
        // back zeroed pages — so the lambda form stays where nothing is reused.
        val pixels = scratch?.pixels(area)?.also { it.fill(background) } ?: IntArray(area) { background }
        if (payload.vertexCount == 0) return pixels

        val depth =
            scratch?.depth(area)?.also { it.fill(Float.NEGATIVE_INFINITY) }
                ?: FloatArray(area) { Float.NEGATIVE_INFINITY }
        val screen = project(payload, width, height, yawDegrees, pitchDegrees)

        val drawTriangles = payload.mode == SnoMode.SOLID && payload.faceCount > 0
        if (drawTriangles) {
            drawFaces(payload, screen, pixels, depth, width, height, lighting)
        }
        val drawLines = payload.mode == SnoMode.LINES && payload.vertexCount > 1
        if (drawLines) {
            drawEdges(payload, screen, pixels, depth, width, height)
        }

        // The vertices, in every mode (§1.5). They are the drawing itself
        // wherever nothing else got drawn — `points` mode, a `solid` with no
        // faces, a `lines` with a single vertex — and also when what was drawn
        // came out too small for a triangle to land on a pixel. Everywhere else
        // they are the hint.
        val pointsAreTheShape = !(drawTriangles || drawLines) || screen.spanPixels < TOO_SMALL_FOR_TRIANGLES
        drawPoints(payload, screen, pixels, depth, width, height, pointsAreTheShape)

        return pixels
    }

    /**
     * Every vertex in pixel space, plus how much of the raster the object fills.
     *
     * Orthographic, looking down `-Z`: `+X` is right, `+Y` is up and `+Z` is
     * toward the viewer (§2), so a larger Z is nearer and screen Y runs the
     * other way from model Y. A thumbnail wants no perspective distortion and
     * no near plane to clip against.
     */
    private fun project(
        payload: SnoPayload,
        width: Int,
        height: Int,
        yawDegrees: Float,
        pitchDegrees: Float,
    ): Projection {
        val count = payload.vertexCount
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val zs = FloatArray(count)

        val yaw = yawDegrees * DEG_TO_RAD
        val pitch = pitchDegrees * DEG_TO_RAD
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until count) {
            // The one conversion from the exact lattice to floats.
            val x = payload.tickAt(i, 0).toFloat()
            val y = payload.tickAt(i, 1).toFloat()
            val z = payload.tickAt(i, 2).toFloat()

            val x1 = x * cosYaw + z * sinYaw
            val z1 = -x * sinYaw + z * cosYaw
            val y2 = y * cosPitch - z1 * sinPitch
            val z2 = y * sinPitch + z1 * cosPitch

            xs[i] = x1
            ys[i] = y2
            zs[i] = z2

            if (x1 < minX) minX = x1
            if (x1 > maxX) maxX = x1
            if (y2 < minY) minY = y2
            if (y2 > maxY) maxY = y2
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val usable = min(width, height) * (1f - 2f * MARGIN)
        val largest = max(spanX, spanY)
        val scale = if (largest <= 0f) 1f else usable / largest

        val centreX = (minX + maxX) * 0.5f
        val centreY = (minY + maxY) * 0.5f
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f

        for (i in 0 until count) {
            xs[i] = halfWidth + (xs[i] - centreX) * scale
            // Screen Y grows downward; model +Y is up.
            ys[i] = halfHeight - (ys[i] - centreY) * scale
        }

        return Projection(xs, ys, zs, largest * scale, scale * SnoPayload.TICKS_PER_UNIT)
    }

    private class Projection(
        val xs: FloatArray,
        val ys: FloatArray,
        val zs: FloatArray,
        /** How many pixels across the object turned out to be. */
        val spanPixels: Float,
        /** How many pixels one model unit came out as, for sizing the dots. */
        val pixelsPerUnit: Float,
    )

    @Suppress("detekt.LongParameterList")
    private fun drawFaces(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        lighting: SnoLighting?,
    ) {
        val faceColors = payload.faceColors
        // Lit, the faces are the ones wound outward, since which side of a face
        // you are looking at is the question the light answers.
        val faces = lighting?.faces ?: payload.faces
        for (face in 0 until payload.faceCount) {
            // A face buried inside a join has faces on both sides of it: lit, it
            // would only fight the one it sits against.
            if (lighting != null && lighting.interior[face]) continue
            val a = faces[face * 3]
            val b = faces[face * 3 + 1]
            val c = faces[face * 3 + 2]
            // A face that carries its own colour fills the whole triangle flatly,
            // and its vertices contribute nothing to the fill (§1.4a).
            val flat = faceColors?.get(face)
            fillTriangle(
                screen,
                pixels,
                depth,
                width,
                height,
                a,
                b,
                c,
                flat ?: payload.colors[a],
                flat ?: payload.colors[b],
                flat ?: payload.colors[c],
                lighting?.outside?.get(face) ?: UNLIT,
                lighting?.inside?.get(face) ?: UNLIT,
            )
        }
    }

    /**
     * One triangle, barycentric, both sides drawn.
     *
     * The edge function's sign tells us the winding, and it is used only to
     * normalise the barycentric weights — never to decide whether to draw
     * (§1.4).
     */
    @Suppress("detekt.LongParameterList")
    private fun fillTriangle(
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        ia: Int,
        ib: Int,
        ic: Int,
        colorA: Int,
        colorB: Int,
        colorC: Int,
        shadeOutside: Float,
        shadeInside: Float,
    ) {
        val ax = screen.xs[ia]
        val ay = screen.ys[ia]
        val bx = screen.xs[ib]
        val by = screen.ys[ib]
        val cx = screen.xs[ic]
        val cy = screen.ys[ic]

        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (area > -MIN_AREA && area < MIN_AREA) return

        val left = max(0, floorToInt(min(ax, min(bx, cx))))
        val right = min(width - 1, ceilToInt(max(ax, max(bx, cx))))
        val top = max(0, floorToInt(min(ay, min(by, cy))))
        val bottom = min(height - 1, ceilToInt(max(ay, max(by, cy))))
        if (left > right || top > bottom) return

        // Each edge function is linear in x and y, so it is evaluated once at
        // the corner of the span and then stepped: three adds a pixel instead
        // of six multiplies and four subtractions. The weights still come out
        // barycentric, they are just not recomputed from scratch every time.
        //
        // The sign of the signed area is folded into the coefficients rather
        // than tested per pixel, which is also what draws both sides of a face
        // (§1.4): a triangle wound the other way has its edge functions
        // negated along with its area, so the inside test is the same one.
        val flip = if (area < 0f) -1f else 1f
        val constA = (bx * cy - by * cx) * flip
        val stepAx = (by - cy) * flip
        val stepAy = (cx - bx) * flip
        val constB = (cx * ay - cy * ax) * flip
        val stepBx = (cy - ay) * flip
        val stepBy = (ax - cx) * flip
        val total = area * flip
        val inverseTotal = 1f / total

        // The same sign says which side of the face is turned toward us, once
        // the faces have been wound outward: screen Y runs down, so a triangle
        // whose outside faces the viewer comes out with a negative signed area.
        // Unlit both shades are 1 and the question never arises.
        val shade = if (area < 0f) shadeOutside else shadeInside

        // A face whose three corners agree has nothing to interpolate, and that
        // is the common case: a stamped block is one colour, and a face that
        // carries its own colour (§1.4a) arrives here as all three. Its colour
        // under its light is therefore settled once for the whole triangle
        // instead of being rebuilt at every pixel.
        val interpolate = !(colorA == colorB && colorB == colorC)
        val solid =
            if (interpolate) {
                0
            } else if (shade == UNLIT) {
                colorA
            } else {
                darken(colorA, shade)
            }

        val az = screen.zs[ia]
        val bz = screen.zs[ib]
        val cz = screen.zs[ic]

        val startX = left + 0.5f
        var rowA = constA + startX * stepAx + (top + 0.5f) * stepAy
        var rowB = constB + startX * stepBx + (top + 0.5f) * stepBy

        for (py in top..bottom) {
            var edgeA = rowA
            var edgeB = rowB
            var at = py * width + left
            for (px in left..right) {
                val edgeC = total - edgeA - edgeB
                if (edgeA >= 0f && edgeB >= 0f && edgeC >= 0f) {
                    val wA = edgeA * inverseTotal
                    val wB = edgeB * inverseTotal
                    val wC = edgeC * inverseTotal
                    val z = wA * az + wB * bz + wC * cz
                    if (z > depth[at]) {
                        depth[at] = z
                        pixels[at] = if (interpolate) blend(colorA, colorB, colorC, wA, wB, wC, shade) else solid
                    }
                }
                edgeA += stepAx
                edgeB += stepBx
                at++
            }
            rowA += stepAy
            rowB += stepBy
        }
    }

    /**
     * The edges of the faces, each drawn once (§1.5); with no faces, one
     * polyline through the vertices in order.
     */
    private fun drawEdges(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
    ) {
        if (payload.faceCount == 0) {
            for (i in 0 until payload.vertexCount - 1) {
                drawLine(screen, pixels, depth, width, height, i, i + 1, payload.colors[i], payload.colors[i + 1])
            }
            return
        }

        val seen = HashSet<Long>(payload.faceCount * 3)
        for (face in 0 until payload.faceCount) {
            val a = payload.faces[face * 3]
            val b = payload.faces[face * 3 + 1]
            val c = payload.faces[face * 3 + 2]
            edge(seen, a, b)?.let { drawLine(screen, pixels, depth, width, height, a, b, payload.colors[a], payload.colors[b]) }
            edge(seen, b, c)?.let { drawLine(screen, pixels, depth, width, height, b, c, payload.colors[b], payload.colors[c]) }
            edge(seen, a, c)?.let { drawLine(screen, pixels, depth, width, height, a, c, payload.colors[a], payload.colors[c]) }
        }
    }

    /** Null when this edge has already been drawn; the key otherwise. */
    private fun edge(
        seen: HashSet<Long>,
        a: Int,
        b: Int,
    ): Long? {
        val low = min(a, b).toLong()
        val high = max(a, b).toLong()
        val key = (low shl 32) or high
        return if (seen.add(key)) key else null
    }

    private fun drawLine(
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        from: Int,
        to: Int,
        colorFrom: Int,
        colorTo: Int,
    ) {
        val x0 = screen.xs[from]
        val y0 = screen.ys[from]
        val x1 = screen.xs[to]
        val y1 = screen.ys[to]
        val steps = max(abs(x1 - x0), abs(y1 - y0)).roundToInt()
        if (steps <= 0) {
            plot(pixels, depth, width, height, x0.roundToInt(), y0.roundToInt(), screen.zs[from], colorFrom)
            return
        }
        val z0 = screen.zs[from]
        val z1 = screen.zs[to]
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            plot(
                pixels,
                depth,
                width,
                height,
                (x0 + (x1 - x0) * t).roundToInt(),
                (y0 + (y1 - y0) * t).roundToInt(),
                z0 + (z1 - z0) * t,
                mix(colorFrom, colorTo, t),
            )
        }
    }

    /**
     * The vertices, as round dots at one of the two sizes §1.5 asks for.
     *
     * @param asShape true where the dots are the drawing, false where something
     *   is already drawn and they are the hint laid over it.
     */
    @Suppress("detekt.LongParameterList")
    private fun drawPoints(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        asShape: Boolean,
    ) {
        val perUnit = screen.pixelsPerUnit
        val diameter =
            if (asShape) {
                (SHAPE_POINT_UNITS * perUnit).coerceIn(SHAPE_POINT_MIN_PX, SHAPE_POINT_MAX_PX)
            } else {
                (HINT_DOT_UNITS * perUnit).coerceIn(HINT_DOT_MIN_PX, HINT_DOT_MAX_PX)
            }
        // A dot thinner than a pixel cannot be drawn any smaller, so it is drawn
        // fainter instead, which is how a pixel says "less than one of me".
        val alpha = (if (asShape) 1f else HINT_DOT_ALPHA) * min(1f, diameter)
        val radius = diameter * 0.5f
        val colors = dotColors(payload)

        for (i in 0 until payload.vertexCount) {
            drawDot(pixels, depth, width, height, screen.xs[i], screen.ys[i], screen.zs[i], colors[i], radius, alpha)
        }
    }

    /**
     * The colour each vertex is marked in.
     *
     * Its own, except on a filled object whose faces carry their own colours:
     * there §1.4a has already decided that the vertex colours are not what this
     * object shows anywhere, and a dot in a colour that appears nowhere else
     * would be the format contradicting itself on the reader's screen. The
     * reference viewer reaches the same place from the other end — it splits
     * every coloured face into three corners of its own before it draws
     * anything, so its points inherit the face colour too — and where a vertex
     * is shared by faces of different colours it draws one dot per face and the
     * last one wins, which is exactly the vertex's last face, as here.
     */
    private fun dotColors(payload: SnoPayload): IntArray {
        val faceColors = payload.faceColors
        if (faceColors == null || payload.mode != SnoMode.SOLID) return payload.colors
        val resolved = payload.colors.copyOf()
        for (face in 0 until payload.faceCount) {
            val color = faceColors[face]
            resolved[payload.faces[face * 3]] = color
            resolved[payload.faces[face * 3 + 1]] = color
            resolved[payload.faces[face * 3 + 2]] = color
        }
        return resolved
    }

    /**
     * One round dot, its rim softened by how much of each pixel it covers.
     *
     * It reads the depth buffer and does not write it, as the reference's point
     * material does (`depthWrite: false`): a vertex on the far side of an object
     * stays hidden behind the faces in front of it, while two dots that overlap
     * both land instead of one clipping the other. [DOT_DEPTH_BIAS] is what
     * lets a vertex sitting exactly on the faces that meet there win against
     * them.
     */
    @Suppress("detekt.LongParameterList")
    private fun drawDot(
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        centreX: Float,
        centreY: Float,
        z: Float,
        color: Int,
        radius: Float,
        alpha: Float,
    ) {
        val near = z + DOT_DEPTH_BIAS
        val reach = radius + 0.5f
        val left = max(0, floorToInt(centreX - reach))
        val right = min(width - 1, ceilToInt(centreX + reach))
        val top = max(0, floorToInt(centreY - reach))
        val bottom = min(height - 1, ceilToInt(centreY + reach))
        if (left > right || top > bottom) return

        for (py in top..bottom) {
            val dy = py + 0.5f - centreY
            var at = py * width + left
            for (px in left..right) {
                val dx = px + 0.5f - centreX
                val coverage = reach - sqrt(dx * dx + dy * dy)
                if (coverage > 0f && near >= depth[at]) {
                    over(pixels, at, color, alpha * min(coverage, 1f))
                }
                at++
            }
        }
    }

    /**
     * Source-over compositing of an opaque colour at [alpha] into the
     * non-premultiplied ARGB buffer, which may itself be transparent where the
     * background is.
     */
    private fun over(
        pixels: IntArray,
        at: Int,
        color: Int,
        alpha: Float,
    ) {
        val source = min(alpha, 1f)
        if (source <= 0f) return
        val destination = pixels[at]
        val kept = (destination ushr 24) * (1f / 255f) * (1f - source)
        val total = source + kept
        if (total <= 0f) return
        val inverse = 1f / total
        val r = ((color shr 16 and 0xFF) * source + (destination shr 16 and 0xFF) * kept) * inverse
        val g = ((color shr 8 and 0xFF) * source + (destination shr 8 and 0xFF) * kept) * inverse
        val b = ((color and 0xFF) * source + (destination and 0xFF) * kept) * inverse
        pixels[at] =
            (clamp255((total * 255f).roundToInt()) shl 24) or
            (clamp255(r.roundToInt()) shl 16) or
            (clamp255(g.roundToInt()) shl 8) or
            clamp255(b.roundToInt())
    }

    private fun plot(
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        z: Float,
        color: Int,
    ) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val at = y * width + x
        if (z < depth[at]) return
        depth[at] = z
        pixels[at] = color
    }

    /**
     * Barycentric interpolation between three opaque colours, times the light.
     * The three are never all equal here; [fillTriangle] settles that case once
     * for the whole triangle.
     */
    @Suppress("detekt.LongParameterList")
    private fun blend(
        a: Int,
        b: Int,
        c: Int,
        wA: Float,
        wB: Float,
        wC: Float,
        shade: Float,
    ): Int {
        val r = (((a shr 16 and 0xFF) * wA + (b shr 16 and 0xFF) * wB + (c shr 16 and 0xFF) * wC) * shade).roundToInt()
        val g = (((a shr 8 and 0xFF) * wA + (b shr 8 and 0xFF) * wB + (c shr 8 and 0xFF) * wC) * shade).roundToInt()
        val bl = (((a and 0xFF) * wA + (b and 0xFF) * wB + (c and 0xFF) * wC) * shade).roundToInt()
        return (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(bl)
    }

    /** An opaque colour under a light that is less than all of it. */
    private fun darken(
        color: Int,
        shade: Float,
    ): Int =
        (0xFF shl 24) or
            (clamp255(((color shr 16 and 0xFF) * shade).roundToInt()) shl 16) or
            (clamp255(((color shr 8 and 0xFF) * shade).roundToInt()) shl 8) or
            clamp255(((color and 0xFF) * shade).roundToInt())

    private fun mix(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        if (from == to) return from
        val r = ((from shr 16 and 0xFF) + ((to shr 16 and 0xFF) - (from shr 16 and 0xFF)) * t).roundToInt()
        val g = ((from shr 8 and 0xFF) + ((to shr 8 and 0xFF) - (from shr 8 and 0xFF)) * t).roundToInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
    }

    private fun clamp255(v: Int) =
        if (v < 0) {
            0
        } else if (v > 255) {
            255
        } else {
            v
        }

    private fun floorToInt(v: Float): Int {
        val i = v.toInt()
        return if (v < 0f && v != i.toFloat()) i - 1 else i
    }

    private fun ceilToInt(v: Float): Int {
        val i = v.toInt()
        return if (v > 0f && v != i.toFloat()) i + 1 else i
    }

    private const val DEG_TO_RAD = 0.017453292f
}

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
package com.vitorpamplona.amethyst.commons.prodbench

import com.vitorpamplona.amethyst.commons.sno.SnoLighting
import com.vitorpamplona.amethyst.commons.sno.SnoRasterizer
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What it costs to draw an object, and a floor under it.
 *
 * The rasterizer is the one hot path this feature owns: a feed thumbnail pays
 * it once per object and the viewer pays it per frame of a drag. Its inner loop
 * evaluates the edge functions incrementally, which is worth about 4x over
 * recomputing them per pixel, and this is here so that a change which quietly
 * gives that back shows up as a number rather than as a slow phone.
 *
 * Two shapes, because they cost very differently:
 * - **the largest object actually on the network**, 118 vertices and 94 faces;
 * - **an adversarial one at the format's ceiling**, 512 vertices and 1004 faces
 *   scattered at random so that every triangle spans much of the frame. Nothing
 *   an author would build, but §1.8's limits permit it and a stranger can
 *   publish it, so it is the number the viewer's off-thread raster and its
 *   512px cap exist to survive.
 *
 * The assertion is deliberately loose — a hundredfold over the measured cost —
 * so it catches an order-of-magnitude regression without flaking on a busy CI box.
 */
class SnoRasterBenchmark {
    private val real =
        SnoParser
            .parse("{\"v\":2,\"name\":\"first object\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-1,0,1],[3,0,1],[3,0,-3],[-1,0,-3],[1,4,-1],[-5,0,1],[-1,0,1],[-1,0,-3],[-5,0,-3],[-3,4,-1],[-5,0,5],[-1,0,5],[-1,0,1],[-5,0,1],[-3,4,3],[-1,0,2],[3,0,2],[3,0,-2],[-1,0,-2],[1,4,0],[-3,0,2],[1,0,2],[1,0,-2],[-3,0,-2],[-1,4,0],[-4,0,3],[0,0,3],[0,0,-1],[-4,0,-1],[-2,4,1],[-4,0,-2],[0,0,-2],[0,0,-6],[-4,0,-6],[-2,4,-4],[-8,0,1],[-6,0,1],[-6,0,-1],[-8,0,-1],[-8,2,1],[-8,2,-1],[-8,0,-2],[-6,0,-2],[-6,0,-4],[-8,0,-4],[-8,2,-2],[-8,2,-4],[-8,0,-4],[-6,0,-4],[-6,0,-6],[-8,0,-6],[-8,2,-4],[-8,2,-6],[-8,0,2],[-6,0,2],[-6,0,0],[-8,0,0],[-8,2,2],[-8,2,0],[2,0,7],[4,0,7],[4,0,5],[2,0,5],[2,2,7],[2,2,5],[-2,0,7],[0,0,7],[0,0,5],[-2,0,5],[-2,2,7],[-2,2,5]],\"ticks\":[-118],\"colors\":[226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226],\"faces\":[[43,42,46],[44,43,46],[45,44,46],[42,45,46],[42,43,44],[42,44,45],[48,47,51],[49,48,51],[50,49,51],[47,50,51],[47,48,49],[47,49,50],[53,52,56],[54,53,56],[55,54,56],[52,55,56],[52,53,54],[52,54,55],[58,57,61],[59,58,61],[60,59,61],[57,60,61],[57,58,59],[57,59,60],[63,62,66],[64,63,66],[65,64,66],[62,65,66],[62,63,64],[62,64,65],[68,67,71],[69,68,71],[70,69,71],[67,70,71],[67,68,69],[67,69,70],[73,72,76],[74,73,76],[75,74,76],[72,75,76],[72,73,74],[72,74,75],[78,77,81],[79,78,81],[80,79,81],[77,80,81],[77,78,79],[77,79,80],[82,83,84],[82,84,85],[82,85,87],[82,87,86],[83,86,87],[83,87,84],[83,82,86],[85,84,87],[88,89,90],[88,90,91],[88,91,93],[88,93,92],[89,92,93],[89,93,90],[89,88,92],[94,95,96],[94,96,97],[94,97,99],[94,99,98],[95,98,99],[95,99,96],[97,96,99],[100,101,102],[100,102,103],[100,103,105],[100,105,104],[101,104,105],[101,105,102],[101,100,104],[103,102,105],[106,107,108],[106,108,109],[106,109,111],[106,111,110],[107,110,111],[107,111,108],[107,106,110],[109,108,111],[112,113,114],[112,114,115],[112,115,117],[112,117,116],[113,116,117],[113,117,114],[113,112,116],[115,114,117]]}")
            .payloadOrNull()!!

    private val adversarial: SnoPayload by lazy {
        val rnd = Random(7)
        val vertices = (0 until 512).joinToString(",") { "[${rnd.nextInt(-8, 9)},${rnd.nextInt(-8, 9)},${rnd.nextInt(-8, 9)}]" }
        val colors = (0 until 512).joinToString(",") { (it % 256).toString() }
        val faces = (0 until 1004).joinToString(",") { "[${it % 512},${(it + 1) % 512},${(it + 2) % 512}]" }
        SnoParser
            .parse("""{"v":2,"name":"bench","unit":0,"mode":"solid","vertices":[$vertices],"colors":[$colors],"faces":[$faces]}""")
            .payloadOrNull()!!
    }

    private fun millisPerFrame(
        payload: SnoPayload,
        size: Int,
        lighting: SnoLighting? = null,
    ): Double {
        repeat(WARMUP) { SnoRasterizer.render(payload, size, size, lighting = lighting) }
        return timedBatch(payload, size, lighting)
    }

    private fun timedBatch(
        payload: SnoPayload,
        size: Int,
        lighting: SnoLighting?,
    ): Double {
        val start = System.nanoTime()
        repeat(RUNS) { SnoRasterizer.render(payload, size, size, lighting = lighting) }
        return (System.nanoTime() - start) / 1e6 / RUNS
    }

    private fun millisToLight(payload: SnoPayload): Double {
        repeat(3) { SnoLighting.of(payload) }
        val start = System.nanoTime()
        repeat(5) { SnoLighting.of(payload) }
        return (System.nanoTime() - start) / 1e6 / 5
    }

    @Test
    fun aRealObjectIsCheapToDraw() {
        val thumbnail = millisPerFrame(real, THUMBNAIL_PX)
        val viewer = millisPerFrame(real, VIEWER_PX)
        val full = millisPerFrame(real, 1080)
        val adversarialFull = millisPerFrame(adversarial, 1080)
        println("real (118v/94f): ${THUMBNAIL_PX}px ${fmt(thumbnail)} ms, ${VIEWER_PX}px ${fmt(viewer)} ms, 1080px ${fmt(full)} ms")
        println("adversarial at 1080px: ${fmt(adversarialFull)} ms")

        assertTrue(thumbnail < 5.0, "a feed thumbnail of a real object should be far under a frame, was ${fmt(thumbnail)} ms")
        assertTrue(viewer < 50.0, "a viewer frame of a real object should be well under a drag's budget, was ${fmt(viewer)} ms")
    }

    @Test
    fun theAdversarialCeilingStaysBounded() {
        val viewer = millisPerFrame(adversarial, VIEWER_PX)
        println("adversarial (512v/1004f): ${VIEWER_PX}px ${fmt(viewer)} ms")

        // Sluggish by design rather than frozen: this one is why the viewer
        // rasters off the composition thread and caps its size.
        assertTrue(viewer < 500.0, "the ceiling should stay inside the cap's budget, was ${fmt(viewer)} ms")
    }

    @Test
    fun theLightIsPaidOncePerObjectAndNotPerFrame() {
        // Winding the faces outward walks a ray from every face against every
        // other one, which is quadratic in the face count and by far the most
        // expensive thing this feature does. It depends on the object alone, so
        // the viewer holds it across a turn; what has to stay cheap is the
        // frame, and a lit frame differs from a flat one by three multiplies a
        // pixel.
        val realCost = millisToLight(real)
        val ceiling = millisToLight(adversarial)
        // The two frames are compared with each other, so they are timed in
        // alternating batches and each keeps its best one: a GC pause, a JIT
        // recompile or a noisy neighbour on a CI box lands in a single batch of
        // one side and would otherwise decide the comparison on its own.
        val light = SnoLighting.of(real)
        repeat(WARMUP) {
            SnoRasterizer.render(real, VIEWER_PX, VIEWER_PX)
            SnoRasterizer.render(real, VIEWER_PX, VIEWER_PX, lighting = light)
        }
        var flatFrame = Double.MAX_VALUE
        var litFrame = Double.MAX_VALUE
        repeat(TRIALS) {
            flatFrame = minOf(flatFrame, timedBatch(real, VIEWER_PX, null))
            litFrame = minOf(litFrame, timedBatch(real, VIEWER_PX, light))
        }
        println("winding + light: real (94f) ${fmt(realCost)} ms, adversarial (1004f) ${fmt(ceiling)} ms")
        println("a ${VIEWER_PX}px frame of a real object: flat ${fmt(flatFrame)} ms, lit ${fmt(litFrame)} ms")

        assertTrue(realCost < 50.0, "lighting a real object should be a blink, was ${fmt(realCost)} ms")
        assertTrue(litFrame < flatFrame * 2 + 5, "a lit frame should cost about what a flat one does, was ${fmt(litFrame)} ms")
    }

    private fun fmt(value: Double) = ((value * 100).toLong() / 100.0).toString()

    companion object {
        private const val WARMUP = 10
        private const val RUNS = 20
        private const val TRIALS = 5
        private const val THUMBNAIL_PX = 96
        private const val VIEWER_PX = 512
    }
}

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

/**
 * The pixel and depth buffers of a view that draws the same object over and over.
 *
 * [SnoRasterizer.render] needs a pixel buffer and a depth buffer the size of the
 * raster, and on its own it allocates both every call. That is the right thing
 * for a thumbnail, which draws once. It is the wrong thing for a turn: at 384px
 * the two come to 1.2 MB, a drag asks for about thirty-five frames a second, and
 * on an SM-T220 that 41 MB/s of garbage took 51% of a core and stuttered the
 * turn every few seconds — while the same screen, held still, spent none.
 *
 * A viewer rasters at one size for the whole gesture, so it can hand the same
 * two buffers back on every frame and allocate nothing. The buffers grow to
 * whatever size is asked for and are kept until it changes.
 *
 * **Handed out, not copied, so one holder must never serve two renders at once.**
 * A caller that can overlap its frames has to serialise them; `SnoObjectViewer`
 * conflates its angles through a single collector for exactly this reason. Where
 * a render stands alone, pass nothing and let it allocate.
 */
class SnoRasterScratch {
    private var pixels: IntArray = EMPTY_PIXELS
    private var depth: FloatArray = EMPTY_DEPTH

    internal fun pixels(size: Int): IntArray {
        if (pixels.size != size) pixels = IntArray(size)
        return pixels
    }

    internal fun depth(size: Int): FloatArray {
        if (depth.size != size) depth = FloatArray(size)
        return depth
    }

    private companion object {
        private val EMPTY_PIXELS = IntArray(0)
        private val EMPTY_DEPTH = FloatArray(0)
    }
}

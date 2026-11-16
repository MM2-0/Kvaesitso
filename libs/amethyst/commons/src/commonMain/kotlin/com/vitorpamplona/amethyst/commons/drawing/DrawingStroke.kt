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
package com.vitorpamplona.amethyst.commons.drawing

/** What a stroke is drawn with. The renderer decides how each one looks. */
enum class DrawingTool {
    /** Solid round line; follows stylus pressure. */
    BRUSH,

    /** Thin, slightly see-through line. */
    PENCIL,

    /** A cloud of dots around the path, fixed by the stroke's seed so every redraw matches. */
    SPRAY,

    /** Takes earlier strokes off again, showing the background underneath. */
    ERASER,
}

/** How a [DrawingPaper] is printed. The renderer draws each one. */
enum class PaperPattern {
    PLAIN,

    /** A sticky note's glue strip along the top. */
    GLUE_STRIP,

    /** Ruled lines with a red margin, like a notebook page. */
    LINED,
    GRID,
    DOTS,

    /** A slate with faint eraser smudges. */
    CHALKBOARD,
}

/**
 * The built-in surfaces a drawing can be made on: colour, print and shape.
 *
 * [aspect] is height over width. Most are a 4:5 portrait, the tallest picture
 * most feeds show uncropped; sticky notes are square.
 */
enum class DrawingPaper(
    val background: Int,
    val aspect: Float,
    val pattern: PaperPattern,
) {
    PLAIN(0xFFFFFFFF.toInt(), 1.25f, PaperPattern.PLAIN),
    STICKY_YELLOW(0xFFFFE46B.toInt(), 1f, PaperPattern.GLUE_STRIP),
    STICKY_PINK(0xFFFFA9CB.toInt(), 1f, PaperPattern.GLUE_STRIP),
    STICKY_MINT(0xFFA9ECC6.toInt(), 1f, PaperPattern.GLUE_STRIP),
    STICKY_SKY(0xFFA7D6FF.toInt(), 1f, PaperPattern.GLUE_STRIP),
    NOTEBOOK(0xFFFBF8EF.toInt(), 1.25f, PaperPattern.LINED),
    GRID(0xFFFCFCFA.toInt(), 1.25f, PaperPattern.GRID),
    DOTS(0xFFFBF8EF.toInt(), 1.25f, PaperPattern.DOTS),
    CHALKBOARD(0xFF1F2B25.toInt(), 1.25f, PaperPattern.CHALKBOARD),
    ;

    /** Dark enough that the default black ink would not show. */
    val isDark: Boolean get() = this == CHALKBOARD
}

/** What the strokes are drawn over: one of the built-in papers or the user's own picture. */
sealed class DrawingBackground {
    /** Height over width. */
    abstract val aspect: Float

    data class Paper(
        val paper: DrawingPaper,
    ) : DrawingBackground() {
        override val aspect: Float get() = paper.aspect
    }

    /**
     * A picture the user picked, already cropped to the shape it will be posted
     * in and scaled down to drawing size, as row-major ARGB [pixels].
     */
    class Photo(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    ) : DrawingBackground() {
        override val aspect: Float get() = height.toFloat() / width

        companion object {
            /** The squattest and tallest shapes a photo is cropped to: 4:3 landscape to 2:3 portrait. */
            const val MIN_ASPECT = 0.75f
            const val MAX_ASPECT = 1.5f
        }
    }
}

/**
 * One continuous line of the pen, from touch down to lift.
 *
 * Coordinates are fractions of the canvas width, on both axes, so the same
 * stroke draws at screen size, at GIF size and at any window size: `x` runs
 * `0..1` and `y` runs `0..aspect`. [width] is a fraction of the canvas width too.
 *
 * Points arrive one by one while the finger moves, so they live in growable
 * primitive arrays rather than a list of objects. A stroke only grows while it
 * is the active one; once the pen lifts it is never changed again.
 */
class DrawingStroke(
    val tool: DrawingTool,
    /** Opaque ARGB. Ignored by [DrawingTool.ERASER], which clears instead. */
    val color: Int,
    val width: Float,
    /** Also drawn reflected across the vertical centre line. */
    val mirror: Boolean,
    /** Seeds [DrawingTool.SPRAY]'s dots. */
    val seed: Int,
) {
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var pressures = FloatArray(INITIAL_CAPACITY)
    private var times = LongArray(INITIAL_CAPACITY)

    var size: Int = 0
        private set

    fun add(
        x: Float,
        y: Float,
        timeMs: Long,
        pressure: Float,
    ) {
        if (size == xs.size) grow()
        xs[size] = x
        ys[size] = y
        times[size] = timeMs
        pressures[size] = pressure
        size++
    }

    fun x(i: Int) = xs[i]

    fun y(i: Int) = ys[i]

    fun timeMs(i: Int) = times[i]

    /** `0..1`; [NO_PRESSURE] when the input device does not report any. */
    fun pressure(i: Int) = pressures[i]

    private fun grow() {
        val next = xs.size * 2
        xs = xs.copyOf(next)
        ys = ys.copyOf(next)
        pressures = pressures.copyOf(next)
        times = times.copyOf(next)
    }

    companion object {
        private const val INITIAL_CAPACITY = 64

        /** What a finger or mouse reports: the brush draws at its plain width. */
        const val NO_PRESSURE = 0.5f
    }
}

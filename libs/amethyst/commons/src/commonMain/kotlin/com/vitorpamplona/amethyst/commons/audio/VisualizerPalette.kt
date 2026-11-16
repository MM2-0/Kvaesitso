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
package com.vitorpamplona.amethyst.commons.audio

import androidx.compose.runtime.Immutable

/**
 * Hue anchors (degrees, 0..360) the renderers use to colour the spectrum. Defaults
 * give the cyan→magenta / bass-purple / treble-pink look from the design mockups.
 */
@Immutable
data class VisualizerPalette(
    val lowHue: Float = 280f,
    val midHue: Float = 200f,
    val highHue: Float = 330f,
    val saturation: Float = 0.92f,
    val lightness: Float = 0.6f,
) {
    init {
        require(saturation in 0f..1f) { "saturation must be in 0f..1f, was $saturation" }
        require(lightness in 0f..1f) { "lightness must be in 0f..1f, was $lightness" }
    }

    companion object {
        val DEFAULT = VisualizerPalette()
    }
}

/** Wraps a hue into 0f..360f (handles negatives), for Color.hsl. */
fun Float.wrapHue(): Float = ((this % 360f) + 360f) % 360f

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
package com.vitorpamplona.amethyst.commons.icons.symbols

// Single source of truth for Material Symbols visual axes. Change WEIGHT here to globally
// re-tune line thickness (100 = Thin, 200 = ExtraLight, 300 = Light, 400 = Regular, …, 700 = Bold).
object MaterialSymbolsDefaults {
    const val WEIGHT: Int = 300
    const val FILL: Float = 0f

    // Fully solid. Material Symbols has no separate filled codepoint — `star` and `star_border`
    // are the same glyph — so a solid icon is this axis, not a different symbol.
    const val FILL_ON: Float = 1f
    const val OPTICAL_SIZE: Float = 24f
    const val GRADE: Float = 0f
}

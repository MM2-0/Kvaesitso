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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.material_symbols_outlined
import org.jetbrains.compose.resources.Font

// Held on CompositionLocal so every Material Symbol call site in the tree reuses one FontFamily
// instance. Without this, every Icon() composition allocates a new Font wrapper, breaks its own
// remember cache, and forces a fresh TextMeasurer per call site.
val LocalMaterialSymbolsFontFamily: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

// The same font at FILL=1. Material Symbols expresses fill through a variable axis rather than
// a second codepoint, so a filled star and an outlined one are one glyph drawn from two families.
// Held separately (rather than built per call site) for the same reason as the outline family.
val LocalMaterialSymbolsFilledFontFamily: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

// Shared TextMeasurer so its internal LRU cache is hit by every icon draw in the tree.
val LocalMaterialSymbolsTextMeasurer: ProvidableCompositionLocal<TextMeasurer?> = staticCompositionLocalOf { null }

private const val TEXT_MEASURER_CACHE_SIZE = 64

/**
 * Builds the Material Symbols FontFamily and a shared TextMeasurer once for the subtree and
 * exposes them via CompositionLocal. Wrap app roots (AmethystTheme, desktop MaterialTheme) in this.
 *
 * The optional [weight] override lets callers pick a stroke thickness different from the
 * library default — desktop uses per-OS weights (macOS likes the thinner SF-Symbols-ish
 * look at 200, Windows Fluent icons sit closer to 400) while Android keeps the default.
 */
@Composable
fun ProvideMaterialSymbols(
    weight: Int = MaterialSymbolsDefaults.WEIGHT,
    content: @Composable () -> Unit,
) {
    val adjustment = systemFontWeightAdjustment()
    val font = symbolFont(weight, MaterialSymbolsDefaults.FILL, adjustment)
    val filledFont = symbolFont(weight, MaterialSymbolsDefaults.FILL_ON, adjustment)
    // Keyless remember is safe only when weight is stable; key on weight so a platform-
    // preview override swap actually rebuilds the FontFamily.
    val fontFamily = remember(weight, adjustment) { FontFamily(font) }
    val filledFontFamily = remember(weight, adjustment) { FontFamily(filledFont) }
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_MEASURER_CACHE_SIZE)
    CompositionLocalProvider(
        LocalMaterialSymbolsFontFamily provides fontFamily,
        LocalMaterialSymbolsFilledFontFamily provides filledFontFamily,
        LocalMaterialSymbolsTextMeasurer provides textMeasurer,
        content = content,
    )
}

@Composable
internal fun materialSymbolsFontFamily(): FontFamily = LocalMaterialSymbolsFontFamily.current ?: fontFamilyFallback(MaterialSymbolsDefaults.FILL)

/** The same symbols drawn solid. See [LocalMaterialSymbolsFilledFontFamily]. */
@Composable
internal fun materialSymbolsFilledFontFamily(): FontFamily = LocalMaterialSymbolsFilledFontFamily.current ?: fontFamilyFallback(MaterialSymbolsDefaults.FILL_ON)

@Composable
internal fun materialSymbolsTextMeasurer(): TextMeasurer =
    LocalMaterialSymbolsTextMeasurer.current
        ?: rememberTextMeasurer(cacheSize = TEXT_MEASURER_CACHE_SIZE)

// Android adds the system bold-text [adjustment] to the wght axis when it loads the font, so
// the axis is set that much lower to land on [weight].
@Composable
private fun symbolFont(
    weight: Int,
    fill: Float,
    adjustment: Int,
) = Font(
    resource = Res.font.material_symbols_outlined,
    weight = FontWeight(weight),
    variationSettings =
        FontVariation.Settings(
            FontVariation.weight((weight - adjustment).coerceIn(1, 1000)),
            FontVariation.Setting("FILL", fill),
            FontVariation.Setting("opsz", MaterialSymbolsDefaults.OPTICAL_SIZE),
            FontVariation.Setting("GRAD", MaterialSymbolsDefaults.GRADE),
        ),
)

@Composable
private fun fontFamilyFallback(fill: Float): FontFamily {
    val adjustment = systemFontWeightAdjustment()
    val font = symbolFont(MaterialSymbolsDefaults.WEIGHT, fill, adjustment)
    return remember(fill, adjustment) { FontFamily(font) }
}

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

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amethyst_icons
import org.jetbrains.compose.resources.Font
import androidx.compose.material3.Icon as Material3Icon

/**
 * Amethyst's own icons, built into a font by `tools/icon-font/build_icon_font.py`.
 *
 * Unlike Material Symbols this is a static font — no FILL/opsz/GRAD axes — so it needs no
 * [androidx.compose.ui.text.font.FontVariation] settings. Drawing an icon as a glyph blits from
 * the shared text atlas instead of rasterising an ImageVector's paths into a per-instance cached
 * layer, which is what made the feed re-rasterise the same three glyphs once per card.
 */
val LocalAmethystIconsFontFamily: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

/**
 * Builds the icon FontFamily once for the subtree and exposes it via CompositionLocal, for the
 * same reason [ProvideMaterialSymbols] does: without it every call site allocates its own Font
 * wrapper and breaks its own remember cache. Nest this inside ProvideMaterialSymbols at app roots.
 */
@Composable
fun ProvideAmethystIcons(content: @Composable () -> Unit) {
    val font = Font(resource = Res.font.amethyst_icons)
    val fontFamily = remember { FontFamily(font) }
    CompositionLocalProvider(LocalAmethystIconsFontFamily provides fontFamily, content = content)
}

/**
 * Draws one of Amethyst's own icons as a font glyph. Tint is baked into the painter, so
 * Material3 is told not to tint again.
 */
@Composable
fun AmethystIconGlyph(
    symbol: MaterialSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Material3Icon(
        painter = rememberMaterialSymbolPainter(symbol, tint, amethystIconsFontFamily()),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun amethystIconsFontFamily(): FontFamily =
    LocalAmethystIconsFontFamily.current ?: run {
        val font = Font(resource = Res.font.amethyst_icons)
        remember { FontFamily(font) }
    }

/**
 * Provides both icon fonts for a subtree: Material Symbols and Amethyst's own icons.
 *
 * App roots call this instead of [ProvideMaterialSymbols] so neither font is missed, and so
 * adding the second one does not re-indent the whole root composable.
 */
@Composable
fun ProvideAppIcons(
    weight: Int = MaterialSymbolsDefaults.WEIGHT,
    content: @Composable () -> Unit,
) {
    ProvideMaterialSymbols(weight = weight) {
        ProvideAmethystIcons(content = content)
    }
}

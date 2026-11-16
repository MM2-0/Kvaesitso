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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Cards and dialogs that carry accent-tinted controls (tonal buttons, pills, badges): the key
 * screens, the remote-signer and key-storage prompts, the login card.
 *
 * Material puts these on the surfaceContainer greys. Under lavender buttons, or over the login
 * glow, that mid grey reads as dirty: neither the page nor the accent. These sit at the ends of
 * the neutral ramp instead (white in light, near black in dark) with a hairline edge, and what
 * sits inside them gets a faint wash of the accent rather than a second grey.
 */
object CardSurfaces {
    /** The card itself: white in light, near black in dark. */
    val color: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLowest

    /** The hairline that separates a card from the page, or a dialog from its scrim. */
    val border: BorderStroke
        @Composable @ReadOnlyComposable
        get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (MaterialTheme.colorScheme.isLight) 0.7f else 0.9f))

    /** A panel inside a card (the masked key, an encrypted copy): the accent, barely. */
    val well: Color
        @Composable @ReadOnlyComposable
        get() =
            MaterialTheme.colorScheme.primary
                .copy(alpha = if (MaterialTheme.colorScheme.isLight) 0.05f else 0.12f)
                .compositeOver(color)

    /**
     * Frosted, for a card floating over the logged-off glow: the glow shows through, so the card
     * belongs to the page instead of sitting on it as a grey slab.
     */
    val frosted: Color
        @Composable @ReadOnlyComposable
        get() = color.copy(alpha = if (MaterialTheme.colorScheme.isLight) 0.72f else 0.62f)

    /** The frosted card's edge: light catching it in light, a faint rim in dark. */
    val frostedBorder: BorderStroke
        @Composable @ReadOnlyComposable
        get() = BorderStroke(1.dp, if (MaterialTheme.colorScheme.isLight) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f))

    /**
     * A filled main action that waits on its input (Login before a key, Unlock before a password):
     * a faint wash of the accent rather than Material's grey slab, so it reads as the same button,
     * not ready yet.
     */
    @Composable
    fun mainButtonColors(): ButtonColors =
        ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
}

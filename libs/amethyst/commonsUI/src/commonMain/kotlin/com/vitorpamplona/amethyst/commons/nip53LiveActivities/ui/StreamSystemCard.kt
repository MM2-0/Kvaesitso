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
package com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared rounded, accent-tinted container used by the "system-style" cards
 * rendered inside the live stream chat feed (zaps, raids, clips).
 *
 * [fillWidth] = true (default) stretches the card edge to edge, for content that
 * carries media previews. false hugs the content and centers the card in the
 * feed, matching the centered system-message pill design.
 *
 * Kept platform-neutral so Desktop can consume it alongside Android.
 */
@Composable
fun StreamSystemCard(
    accent: Color = MaterialTheme.colorScheme.primary,
    accentAlpha: Float = 0.12f,
    onClick: (() -> Unit)? = null,
    fillWidth: Boolean = true,
    // 18dp matches the rounding of the redesigned chat bubbles and system pills.
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val base =
        (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(accent.copy(alpha = accentAlpha))

    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base

    val card =
        @Composable {
            Box(
                modifier = clickable.padding(horizontal = 10.dp, vertical = 8.dp),
                content = content,
            )
        }

    if (fillWidth) {
        card()
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            card()
        }
    }
}

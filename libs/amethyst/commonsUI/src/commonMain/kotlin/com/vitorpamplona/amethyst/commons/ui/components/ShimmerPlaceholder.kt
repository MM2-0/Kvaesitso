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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // Kept as a State (no `by`) and read inside drawBehind: reading it in
    // composition recomposed every shimmer — six per NoteCardSkeleton — at the
    // display refresh rate and rebuilt the brush and background modifier each
    // frame, exactly while the feed is busy parsing events.
    val translateAnim =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerTranslate",
        )

    val colorScheme = MaterialTheme.colorScheme
    val shimmerColors =
        remember(colorScheme) {
            listOf(
                colorScheme.surfaceContainerHigh,
                colorScheme.surfaceContainer,
                colorScheme.surfaceContainerHigh,
            )
        }
    val shape = MaterialTheme.shapes.small

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val t = translateAnim.value
                drawRect(
                    Brush.linearGradient(
                        colors = shimmerColors,
                        start = Offset(t - 200f, t - 200f),
                        end = Offset(t, t),
                    ),
                )
            },
    )
}

@Composable
fun NoteCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerPlaceholder(Modifier.size(32.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShimmerPlaceholder(Modifier.width(120.dp).height(12.dp))
                ShimmerPlaceholder(Modifier.width(60.dp).height(10.dp))
            }
        }
        ShimmerPlaceholder(Modifier.fillMaxWidth().height(14.dp))
        ShimmerPlaceholder(Modifier.fillMaxWidth(0.7f).height(14.dp))
        ShimmerPlaceholder(Modifier.fillMaxWidth().height(180.dp))
    }
}

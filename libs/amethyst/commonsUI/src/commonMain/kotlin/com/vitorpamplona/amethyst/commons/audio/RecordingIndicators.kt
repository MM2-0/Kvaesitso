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

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.recording_indicator_description
import com.vitorpamplona.amethyst.commons.resources.recording_indicator_with_time
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatSecondsToTime

/**
 * Animated expanding circles that pulse outward from the recording button
 */
@Composable
fun ExpandingCirclesAnimation(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
) {
    // The composer shows this button all the time. An infinite transition asks for a frame on
    // every vsync for as long as it is composed, so it only exists while recording.
    if (isRecording) {
        ExpandingCircles(modifier, primaryColor)
    }
}

@Composable
private fun ExpandingCircles(
    modifier: Modifier,
    primaryColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expanding_circles")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ExpandingCircle(infiniteTransition, delayMillis = 0, color = primaryColor.copy(alpha = 0.3f), label = "circle1")
        ExpandingCircle(infiniteTransition, delayMillis = 500, color = primaryColor.copy(alpha = 0.2f), label = "circle2")
        ExpandingCircle(infiniteTransition, delayMillis = 1000, color = primaryColor.copy(alpha = 0.1f), label = "circle3")
    }
}

@Composable
private fun BoxScope.ExpandingCircle(
    transition: InfiniteTransition,
    delayMillis: Int,
    color: Color,
    label: String,
) {
    val spec =
        infiniteRepeatable<Float>(
            animation = tween(durationMillis = 1500, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        )
    val scale = transition.animateFloat(initialValue = 0f, targetValue = 2.5f, animationSpec = spec, label = "${label}_scale")
    val alpha = transition.animateFloat(initialValue = 1f, targetValue = 0f, animationSpec = spec, label = "${label}_alpha")

    // Read in the draw phase so each frame redraws the layer instead of recomposing.
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }.background(color, CircleShape),
    )
}

/**
 * Floating recording indicator showing elapsed time
 */
@Composable
fun FloatingRecordingIndicator(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    elapsedSeconds: Int,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (!isRecording) return

    val recordingLabel = stringRes(id = Res.string.recording_indicator_description)
    val recordingWithTime =
        if (isCompact) {
            formatSecondsToTime(elapsedSeconds)
        } else {
            stringRes(id = Res.string.recording_indicator_with_time, formatSecondsToTime(elapsedSeconds))
        }
    val horizontalPadding = if (isCompact) 8.dp else 16.dp
    val innerPadding = if (isCompact) 6.dp else 12.dp
    val textSize = if (isCompact) 12.sp else 14.sp

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = horizontalPadding)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = innerPadding),
        ) {
            // Pulsing stop square
            val infiniteTransition = rememberInfiniteTransition(label = "recording_stop")
            val dotAlpha =
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.5f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1000),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "dot_alpha",
                )

            Icon(
                symbol = MaterialSymbols.Stop,
                contentDescription = recordingLabel,
                tint = Color.White,
                modifier =
                    Modifier
                        .graphicsLayer { alpha = dotAlpha.value }
                        .padding(end = 8.dp),
            )

            Text(
                text = recordingWithTime,
                color = Color.White,
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

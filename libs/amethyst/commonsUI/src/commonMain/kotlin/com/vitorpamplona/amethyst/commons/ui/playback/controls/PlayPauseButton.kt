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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.PlayIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun AnimatedPlayButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            AnimatedPlayPauseButton(
                controllerVisible = remember { mutableStateOf(true) },
                isPlaying = true,
                modifier = Modifier,
            ) { }
        }
    }
}

@Preview
@Composable
fun AnimatedPauseButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            AnimatedPlayPauseButton(
                controllerVisible = remember { mutableStateOf(true) },
                isPlaying = false,
                modifier = Modifier,
            ) { }
        }
    }
}

@Composable
fun AnimatedPlayPauseButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        PlayPauseButton(isPlaying, enabled, onClick)
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(modifier = PlayIconSize, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .background(MaterialTheme.colorScheme.background),
        )

        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(80.dp),
        ) {
            if (!isPlaying) {
                Icon(
                    symbol = MaterialSymbols.PlayArrow,
                    modifier = Size50Modifier,
                    contentDescription = stringRes(Res.string.play),
                )
            } else {
                Icon(
                    symbol = MaterialSymbols.Pause,
                    modifier = Size50Modifier,
                    contentDescription = stringRes(Res.string.pause),
                )
            }
        }
    }
}

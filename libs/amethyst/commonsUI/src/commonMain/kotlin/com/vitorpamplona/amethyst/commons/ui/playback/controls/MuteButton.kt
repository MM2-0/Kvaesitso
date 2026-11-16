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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.mute_button
import com.vitorpamplona.amethyst.commons.resources.muted_button
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.VolumeBottomIconSize
import kotlinx.coroutines.delay

@Preview
@Composable
fun MuteButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            MuteButton(
                controllerVisible = remember { mutableStateOf(true) },
                startingMuteState = true,
                modifier = Modifier,
            ) {}
        }
    }
}

@Composable
fun MuteButton(
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    modifier: Modifier = Modifier,
    toggle: (Boolean) -> Unit,
) {
    val holdOn =
        remember {
            mutableStateOf(
                true,
            )
        }

    // LaunchedEffect already runs on Main, and delay() suspends without holding a thread, so
    // the previous launch(Dispatchers.IO) was just unnecessary dispatcher hopping for a state
    // mutation that's also fine on Main.
    LaunchedEffect(key1 = controllerVisible) {
        delay(2000)
        holdOn.value = false
    }

    val mutedInstance = remember(startingMuteState) { mutableStateOf(startingMuteState) }

    AnimatedVisibility(
        visible = holdOn.value || controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = VolumeBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.7f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = {
                    mutedInstance.value = !mutedInstance.value
                    toggle(mutedInstance.value)
                },
                modifier = Size50Modifier,
            ) {
                if (mutedInstance.value) {
                    MutedIcon()
                } else {
                    MuteIcon()
                }
            }
        }
    }
}

@Composable
fun MutedIcon() {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.VolumeOff,
        contentDescription = stringRes(id = Res.string.muted_button),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Size30Modifier,
    )
}

@Composable
fun MuteIcon() {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.VolumeUp,
        contentDescription = stringRes(id = Res.string.mute_button),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Size30Modifier,
    )
}

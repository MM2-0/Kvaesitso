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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlaybackSpeedState
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Preview
@Composable
fun PlaybackSpeedPopUpButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            PlaybackSpeedPopUpButton(
                playbackSpeed = 0.50f,
                updatePlaybackSpeed = {},
                modifier = Modifier,
            )
        }
    }
}

@Preview
@Composable
fun BottomDialogOfChoicesBodyPreview() {
    ThemeComparisonRow {
        Box(Modifier.background(BitcoinOrange)) {
            BottomDialogOfChoicesBody(
                currentSpeed = 0.50f,
                choices = persistentListOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
                onDismissRequest = {},
                onSelectChoice = {},
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackSpeedPopUpButton(
    player: Player,
    modifier: Modifier = Modifier,
    speedSelection: ImmutableList<Float> = persistentListOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
) {
    val state = rememberPlaybackSpeedState(player)

    if (state.isEnabled) {
        PlaybackSpeedPopUpButton(
            state.playbackSpeed,
            state::updatePlaybackSpeed,
            modifier,
            speedSelection,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackSpeedPopUpButton(
    playbackSpeed: Float,
    updatePlaybackSpeed: (speed: Float) -> Unit,
    modifier: Modifier = Modifier,
    speedSelection: ImmutableList<Float> = persistentListOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
) {
    var openDialog by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "%.1fx".format(playbackSpeed),
            color = Color.White,
            modifier = modifier.clickable(onClick = { openDialog = true }),
            style = MaterialTheme.typography.labelLarge,
        )

        if (openDialog) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { openDialog = false },
                properties = PopupProperties(focusable = true),
            ) {
                BottomDialogOfChoicesBody(
                    currentSpeed = playbackSpeed,
                    choices = speedSelection,
                    onDismissRequest = { openDialog = false },
                    onSelectChoice = updatePlaybackSpeed,
                )
            }
        }
    }
}

@Composable
private fun BottomDialogOfChoicesBody(
    currentSpeed: Float,
    choices: ImmutableList<Float>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Float) -> Unit,
) {
    val colors =
        ButtonDefaults.textButtonColors().copy(
            contentColor = MaterialTheme.colorScheme.onBackground,
        )

    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        choices.forEach { speed ->
            TextButton(
                colors = colors,
                onClick = {
                    onSelectChoice(speed)
                    onDismissRequest()
                },
            ) {
                var fontWeight = FontWeight(400)
                if (speed == currentSpeed) {
                    fontWeight = FontWeight(1000)
                }
                Text("%.1fx".format(speed), fontWeight = fontWeight)
            }
        }
    }
}

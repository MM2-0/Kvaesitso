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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.VideoPlayerAction
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_settings_video_quality
import com.vitorpamplona.amethyst.commons.resources.download_to_phone
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.mute_button
import com.vitorpamplona.amethyst.commons.resources.muted_button
import com.vitorpamplona.amethyst.commons.resources.picture_in_picture
import com.vitorpamplona.amethyst.commons.resources.playback_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.share_or_save
import com.vitorpamplona.amethyst.commons.resources.video_player_settings_action_fullscreen
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.VolumeBottomIconSize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val FadeIn = fadeIn()
private val FadeOut = fadeOut()

@Preview
@Composable
fun OverflowMenuButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            OverflowMenuButton(
                actions = persistentListOf(VideoPlayerAction.Share, VideoPlayerAction.Download, VideoPlayerAction.PictureInPicture),
                startingMuteState = false,
                onFullscreenClick = {},
                onMuteClick = {},
                onQualityClick = {},
                onShareClick = {},
                onSaveClick = {},
                onPipClick = {},
                onCastClick = {},
                castIcon = MaterialSymbols.Cast,
                castContentDescription = "",
                onCaptionsClick = {},
                captionsIcon = MaterialSymbols.ClosedCaption,
                captionsContentDescription = "",
            )
        }
    }
}

@Composable
fun AnimatedOverflowMenuButton(
    controllerVisible: State<Boolean>,
    actions: ImmutableList<VideoPlayerAction>,
    startingMuteState: Boolean,
    onFullscreenClick: (() -> Unit)?,
    onMuteClick: () -> Unit,
    onQualityClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onPipClick: () -> Unit,
    onCastClick: () -> Unit,
    castIcon: MaterialSymbol,
    castContentDescription: String,
    onCaptionsClick: () -> Unit,
    captionsIcon: MaterialSymbol,
    captionsContentDescription: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = FadeIn,
        exit = FadeOut,
    ) {
        OverflowMenuButton(
            actions = actions,
            startingMuteState = startingMuteState,
            onFullscreenClick = onFullscreenClick,
            onMuteClick = onMuteClick,
            onQualityClick = onQualityClick,
            onShareClick = onShareClick,
            onSaveClick = onSaveClick,
            onPipClick = onPipClick,
            onCastClick = onCastClick,
            castIcon = castIcon,
            castContentDescription = castContentDescription,
            onCaptionsClick = onCaptionsClick,
            captionsIcon = captionsIcon,
            captionsContentDescription = captionsContentDescription,
        )
    }
}

@Composable
fun OverflowMenuButton(
    actions: ImmutableList<VideoPlayerAction>,
    startingMuteState: Boolean,
    onFullscreenClick: (() -> Unit)?,
    onMuteClick: () -> Unit,
    onQualityClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onPipClick: () -> Unit,
    onCastClick: () -> Unit,
    castIcon: MaterialSymbol,
    castContentDescription: String,
    onCaptionsClick: () -> Unit,
    captionsIcon: MaterialSymbol,
    captionsContentDescription: String,
) {
    val menuExpanded = remember { mutableStateOf(false) }

    Box(modifier = VolumeBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.7f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )

        IconButton(
            onClick = { menuExpanded.value = true },
            modifier = Size50Modifier,
        ) {
            Icon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = stringRes(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Size20Modifier,
            )
        }
    }

    if (menuExpanded.value) {
        M3ActionDialog(
            title = stringRes(Res.string.playback_actions_dialog_title),
            onDismiss = { menuExpanded.value = false },
        ) {
            M3ActionSection {
                actions.forEach { action ->
                    when (action) {
                        VideoPlayerAction.Fullscreen -> {
                            onFullscreenClick?.let { zoom ->
                                M3ActionRow(
                                    icon = MaterialSymbols.ZoomOutMap,
                                    text = stringRes(Res.string.video_player_settings_action_fullscreen),
                                ) {
                                    menuExpanded.value = false
                                    zoom()
                                }
                            }
                        }

                        VideoPlayerAction.Mute -> {
                            M3ActionRow(
                                icon = if (startingMuteState) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                                text = if (startingMuteState) stringRes(Res.string.muted_button) else stringRes(Res.string.mute_button),
                            ) {
                                menuExpanded.value = false
                                onMuteClick()
                            }
                        }

                        VideoPlayerAction.Quality -> {
                            M3ActionRow(
                                icon = MaterialSymbols.Settings,
                                text = stringRes(Res.string.call_settings_video_quality),
                            ) {
                                menuExpanded.value = false
                                onQualityClick()
                            }
                        }

                        VideoPlayerAction.Share -> {
                            M3ActionRow(
                                icon = MaterialSymbols.Share,
                                text = stringRes(Res.string.share_or_save),
                            ) {
                                menuExpanded.value = false
                                onShareClick()
                            }
                        }

                        VideoPlayerAction.Download -> {
                            M3ActionRow(
                                icon = MaterialSymbols.SaveAlt,
                                text = stringRes(Res.string.download_to_phone),
                            ) {
                                menuExpanded.value = false
                                onSaveClick()
                            }
                        }

                        VideoPlayerAction.PictureInPicture -> {
                            M3ActionRow(
                                icon = MaterialSymbols.PictureInPicture,
                                text = stringRes(Res.string.picture_in_picture),
                            ) {
                                menuExpanded.value = false
                                onPipClick()
                            }
                        }

                        VideoPlayerAction.Cast -> {
                            M3ActionRow(
                                icon = castIcon,
                                text = castContentDescription,
                            ) {
                                menuExpanded.value = false
                                onCastClick()
                            }
                        }

                        VideoPlayerAction.Captions -> {
                            M3ActionRow(
                                icon = captionsIcon,
                                text = captionsContentDescription,
                            ) {
                                menuExpanded.value = false
                                onCaptionsClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

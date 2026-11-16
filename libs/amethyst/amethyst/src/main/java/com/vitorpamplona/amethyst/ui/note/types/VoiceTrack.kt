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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.playback.controls.AnimatedShareButton
import com.vitorpamplona.amethyst.commons.ui.playback.controls.MuteButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size75Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.VoiceHeightModifier
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.controls.AnimatedSaveButton
import com.vitorpamplona.amethyst.service.playback.composable.controls.PictureInPictureButton
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.saveMediaToGallery
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags

/**
 * Shared composable for rendering audio with waveform visualization.
 * Android's NotePlatform.RenderAudioWithWaveform: the shared VoiceHeader (for BaseVoiceEvent) and
 * RenderAudioFromIMeta (for other events with audio IMeta) both draw through it.
 */
@Composable
fun RenderAudioWithWaveform(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    val callbackUri = remember(note) { note.toNostrUri() }

    Column(modifier = MaxWidthPaddingTop5dp, horizontalAlignment = Alignment.CenterHorizontally) {
        RenderAudioWaveformPlayer(
            mediaUrl = mediaUrl,
            title = title,
            mimeType = mimeType,
            waveform = waveform,
            authorName = note.author?.toBestDisplayName(),
            callbackUri = callbackUri,
            accountViewModel = accountViewModel,
        )

        if (noteEvent.hasHashtags()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DisplayUncitedHashtags(noteEvent, callbackUri, accountViewModel, nav)
            }
        }
    }
}

/**
 * The voice player on its own, with no [Note] behind it.
 *
 * Separated because a chat that carries audio does not necessarily have a Note
 * to hand: cordn messages never enter LocalCache, so the only way they can show
 * the same player as every other chat is for the player not to demand one. The
 * Note-shaped overload above is this plus the hashtag row, which is the only
 * part that genuinely needs the event.
 *
 * [waveform] may be null. Nothing that arrives as an encrypted blob carries one
 * today, and the player then shows its transport controls without the bars —
 * still the voice UI, which is the point, rather than a video surface with no
 * picture in it.
 */
@Composable
fun RenderAudioWaveformPlayer(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    authorName: String?,
    callbackUri: String?,
    accountViewModel: AccountViewModel,
) {
    Row(
        Modifier.fillMaxWidth().height(100.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GetMediaItem(
            videoUri = mediaUrl,
            title = title,
            artworkUri = null,
            authorName = authorName,
            callbackUri = callbackUri,
            mimeType = mimeType,
            aspectRatio = null,
            proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(mediaUrl),
            keepPlaying = false,
            waveformData = waveform,
        ) { mediaItem ->
            GetVideoController(
                mediaItem = mediaItem,
                muted = false,
            ) { controller ->
                PauseControllerWhenInBackground(controller)
                RenderVoicePlayer(
                    mediaItem = mediaItem,
                    controllerState = controller,
                    waveform = waveform,
                    borderModifier = MaterialTheme.colorScheme.imageModifier,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

/**
 * The bars drawn when a track carries no amplitudes of its own.
 *
 * A voice note with no waveform used to render as a play button on an empty
 * black bar, which reads as a broken video rather than as audio. Every
 * encrypted format can hit this: MIP-04 and encrypted-media-v2 define no
 * waveform field at all, and a cordn message only carries one if the sender's
 * client wrote the hint.
 *
 * **It has to vary.** `AudioWaveformReadOnly` normalises the amplitudes onto
 * the bar height, so a constant list has no range to normalise and every bar
 * collapses to the minimum — a flat 0.35f placeholder drew a dotted line, not
 * bars. The shape below is a short pattern tiled across the width: enough
 * relief to read as audio, regular enough that nobody mistakes it for a
 * measurement of their recording.
 *
 * What it buys beyond looking right is somewhere for the progress fill to run,
 * so position stays legible while it plays.
 */
private val PlaceholderPattern = listOf(0.35f, 0.62f, 0.45f, 0.85f, 0.5f, 0.72f, 0.4f, 0.58f)

private val PlaceholderWaveform = WaveformData(List(48) { PlaceholderPattern[it % PlaceholderPattern.size] })

@Composable
@OptIn(UnstableApi::class)
fun RenderVoicePlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    waveform: WaveformData? = null,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val controllerVisible = remember(controllerState) { mutableStateOf(false) }

    Box(modifier = borderModifier) {
        ContentFrame(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        controllerState.visibility.setBounds(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt(),
                        )
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // to prevent the ripple from the tap
                    ) { controllerVisible.value = !controllerVisible.value },
            player = controllerState.controller,
            keepContentOnReset = true,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW, // texture view is better inside lazy layouts.
        )

        Row(VoiceHeightModifier, verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(controllerState)
            Waveform(waveform ?: PlaceholderWaveform, controllerState, Modifier)
        }

        RenderTopButtonsForVoice(
            mediaData = mediaItem.src,
            controllerState = controllerState,
            controllerVisible = controllerVisible,
            modifier = Modifier.align(Alignment.TopEnd),
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayPauseButton(controllerState: MediaControllerState) {
    val state = rememberPlayPauseButtonState(controllerState.controller)

    // Play/Pause Button
    IconButton(
        onClick = {
            if (controllerState.controller.isPlaying) {
                controllerState.controller.pause()
            } else {
                controllerState.controller.play()
            }
        },
        modifier = Size75Modifier,
    ) {
        Icon(
            symbol = if (state.showPlay) MaterialSymbols.PlayCircleOutline else MaterialSymbols.PauseCircleOutline,
            contentDescription = if (state.showPlay) stringRes(Res.string.play) else stringRes(Res.string.pause),
            modifier = Size50Modifier,
            tint = Color.White,
        )
    }
}

@Composable
fun RenderTopButtonsForVoice(
    mediaData: MediaItemData,
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current.getActivity()

    RenderTopButtonsForVoice(
        mediaData = mediaData,
        controllerVisible = controllerVisible,
        startingMuteState = controllerState.controller.volume < 0.001,
        onMuteClick = { mute ->
            // makes the new setting the default for new creations.
            DEFAULT_MUTED_SETTING.value = mute
            controllerState.controller.volume = if (mute) 0f else 1f
        },
        onPictureInPictureClick = {
            PipVideoActivity.callIn(mediaData, controllerState.visibility.bounds, context)
        },
        modifier = modifier,
        accountViewModel = accountViewModel,
    )
}

@Composable
fun RenderTopButtonsForVoice(
    mediaData: MediaItemData,
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    onMuteClick: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    Row(modifier) {
        if (!isHlsMedia(mediaData.videoUri, mediaData.mimeType)) {
            AnimatedShareButton(controllerVisible) { popupExpanded, toggle ->
                ShareMediaAction(
                    popupExpanded = popupExpanded,
                    videoUri = mediaData.videoUri,
                    postNostrUri = mediaData.callbackUri,
                    blurhash = null,
                    dim = null,
                    hash = null,
                    mimeType = mediaData.mimeType,
                    onDismiss = toggle,
                    content = MediaUrlVideo(url = mediaData.videoUri, mimeType = mediaData.mimeType, artworkUri = mediaData.artworkUri, authorName = mediaData.authorName, description = mediaData.title, uri = mediaData.callbackUri),
                    accountViewModel = accountViewModel,
                )
            }

            AnimatedSaveButton(controllerVisible) { context ->
                accountViewModel.saveMediaToGallery(mediaData.videoUri, mediaData.mimeType, context)
            }
        } else {
            AnimatedShareButton(controllerVisible) { popupExpanded, toggle ->
                ShareMediaAction(
                    popupExpanded = popupExpanded,
                    videoUri = mediaData.videoUri,
                    postNostrUri = mediaData.callbackUri,
                    blurhash = null,
                    dim = null,
                    hash = null,
                    mimeType = mediaData.mimeType,
                    onDismiss = toggle,
                    content = MediaUrlVideo(url = mediaData.videoUri, mimeType = mediaData.mimeType, artworkUri = mediaData.artworkUri, authorName = mediaData.authorName, description = mediaData.title, uri = mediaData.callbackUri),
                    accountViewModel = accountViewModel,
                )
            }
        }

        PictureInPictureButton(
            controllerVisible = controllerVisible,
            onClick = onPictureInPictureClick,
        )

        MuteButton(
            controllerVisible = controllerVisible,
            startingMuteState = startingMuteState,
            toggle = onMuteClick,
        )
    }
}

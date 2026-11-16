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

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.VideoButtonLocation
import com.vitorpamplona.amethyst.commons.model.VideoPlayerAction
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.captions_turn_off
import com.vitorpamplona.amethyst.commons.resources.captions_turn_on
import com.vitorpamplona.amethyst.commons.resources.cast_stop_casting
import com.vitorpamplona.amethyst.commons.resources.cast_to_device
import com.vitorpamplona.amethyst.commons.resources.download_to_phone
import com.vitorpamplona.amethyst.commons.resources.picture_in_picture
import com.vitorpamplona.amethyst.commons.resources.share_or_save
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.playback.controls.AnimatedOverflowMenuButton
import com.vitorpamplona.amethyst.commons.ui.playback.controls.FullScreenButton
import com.vitorpamplona.amethyst.commons.ui.playback.controls.MuteButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.cast.resolveCastLiveness
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.diskCache.HlsLivenessCache
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.cast.CastDevicePickerDialog
import com.vitorpamplona.amethyst.ui.cast.rememberCastWithLocalNetworkPermission
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.saveMediaToGallery
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Preview
@Composable
fun RenderTopButtonsPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            RenderTopButtons(
                mediaData = MediaItemData("https://test.mp4"),
                hasMultipleQualities = false,
                qualityButton = {},
                controllerVisible = remember { mutableStateOf(true) },
                startingMuteState = false,
                isLive = false,
                pipSupported = true,
                onMuteClick = {},
                onPictureInPictureClick = {},
                onZoomClick = {},
                onOverflowQualityClick = {},
                captionsEnabled = true,
                onCaptionsClick = {},
                modifier = Modifier,
                accountViewModel = mockAccountViewModel(),
            )
        }
    }
}

@Composable
fun RenderTopButtons(
    mediaData: MediaItemData,
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    onZoomClick: (() -> Unit)?,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val isLive = remember(mediaData.videoUri, mediaData.mimeType) { isHlsMedia(mediaData.videoUri, mediaData.mimeType) }
    val pipSupported =
        remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }

    val player = controllerState.controller
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var isMuted by remember(player) { mutableStateOf(player.volume < 0.001) }
    DisposableEffect(player) {
        tracks = player.currentTracks
        isMuted = player.volume < 0.001
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }

                override fun onVolumeChanged(volume: Float) {
                    isMuted = volume < 0.001
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val videoGroup = getVideoTrackGroup(tracks)
    val hasMultipleQualities = videoGroup != null && videoGroup.length > 1

    // Captions are side-loaded from the event's `text-track` tags (MediaItemCache flags the first
    // one default). The account setting is the source of truth, not the player: a player comes
    // out of the warm pool carrying whatever track selection the previous video left on it, so
    // reading the preference off the instance would make the button's state depend on which
    // player this video happened to be handed.
    val captionsEnabled by accountViewModel.captionsEnabledFlow().collectAsStateWithLifecycle()

    // Push the preference onto whichever player is attached, and re-push when either changes.
    // Disabling the whole track type rather than deselecting one group: the type-level switch
    // survives the player later picking a different track.
    LaunchedEffect(player, captionsEnabled) {
        val alreadyDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        if (alreadyDisabled == captionsEnabled) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
                    .build()
        }
    }

    // A video with one track has nothing to choose between, so the button stays a toggle. Only a
    // multi-language video opens a menu — paying a popup for a single "English" row would be a
    // worse answer to the same tap.
    val captionChoices = remember(tracks) { getTextTrackChoices(tracks) }
    val captionsPopupOpen = remember { mutableStateOf(false) }
    val onCaptionsClick =
        remember(captionsEnabled, captionChoices) {
            {
                if (captionChoices.size > 1) {
                    captionsPopupOpen.value = true
                } else {
                    accountViewModel.setCaptionsEnabled(!captionsEnabled)
                }
            }
        }

    val overflowQualityOpen = remember { mutableStateOf(false) }

    // Pause local playback while this video is casting so audio doesn't
    // double up. Has to survive (a) the player instance being recreated when
    // the composable scrolls off-screen and back on, and (b) any later
    // playWhenReady=true flips from auto-play-on-attach or end-of-loading.
    // A one-shot LaunchedEffect would only pause once; a Player.Listener
    // re-pauses every time the player flips back to playing.
    val castSessionStateForLocal by Amethyst.instance.castRegistry.sessionState
        .collectAsStateWithLifecycle()
    val isCastingThisVideo =
        (castSessionStateForLocal as? CastSessionState.Casting)?.request?.url == mediaData.videoUri
    DisposableEffect(player, isCastingThisVideo) {
        if (isCastingThisVideo) {
            player.pause()
            val listener =
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) player.pause()
                    }
                }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        } else {
            onDispose { }
        }
    }
    // Auto-resume local playback only on a genuine cast→no-cast transition
    // (not on cold-mount when nothing is casting). `previousCasting` is keyed
    // on videoUri so it resets when the player switches to a different note.
    val previousCasting = remember(mediaData.videoUri) { mutableStateOf(false) }
    LaunchedEffect(isCastingThisVideo, mediaData.videoUri) {
        if (previousCasting.value && !isCastingThisVideo) {
            player.play()
        }
        previousCasting.value = isCastingThisVideo
    }

    RenderTopButtons(
        mediaData = mediaData,
        hasMultipleQualities = hasMultipleQualities,
        castFormatSummary = castFormatSummary(videoGroup),
        qualityButton = {
            VideoQualityButton(
                player = player,
                controllerVisible = controllerVisible,
            )
        },
        controllerVisible = controllerVisible,
        startingMuteState = isMuted,
        isLive = isLive,
        pipSupported = pipSupported,
        onMuteClick = { mute ->
            DEFAULT_MUTED_SETTING.value = mute
            player.volume = if (mute) 0f else 1f
        },
        onPictureInPictureClick = {
            player.pause()
            PipVideoActivity.callIn(mediaData, controllerState.visibility.bounds, context.getActivity())
        },
        onZoomClick =
            onZoomClick?.let {
                {
                    player.pause()
                    it()
                }
            },
        onOverflowQualityClick = { overflowQualityOpen.value = true },
        captionsEnabled = captionsEnabled,
        onCaptionsClick = onCaptionsClick,
        modifier = modifier,
        accountViewModel = accountViewModel,
    )

    if (captionsPopupOpen.value) {
        CaptionLanguagePopup(
            choices = captionChoices,
            captionsEnabled = captionsEnabled,
            onSelectOff = {
                accountViewModel.setCaptionsEnabled(false)
                captionsPopupOpen.value = false
            },
            onSelectTrack = { choice ->
                // The override goes straight onto the player (it is per-video), while the
                // preference is what persists the fact that captions are wanted at all.
                selectTextTrack(player, choice)
                accountViewModel.setCaptionsEnabled(true)
                captionsPopupOpen.value = false
            },
            onDismiss = { captionsPopupOpen.value = false },
        )
    }

    if (overflowQualityOpen.value && videoGroup != null) {
        VideoQualityPopup(
            player = player,
            videoGroup = videoGroup,
            onDismiss = { overflowQualityOpen.value = false },
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RenderTopButtons(
    mediaData: MediaItemData,
    hasMultipleQualities: Boolean,
    // What the local player decoded this as. Only the Cast failure messages use it: a receiver that
    // refuses a video usually will not say why, so this is the only description of it available.
    castFormatSummary: String? = null,
    qualityButton: @Composable () -> Unit,
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    isLive: Boolean,
    pipSupported: Boolean,
    onMuteClick: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    onZoomClick: (() -> Unit)?,
    onOverflowQualityClick: () -> Unit,
    captionsEnabled: Boolean,
    onCaptionsClick: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val buttonItems by accountViewModel.videoPlayerButtonItemsFlow().collectAsStateWithLifecycle()
    val captionsIcon = if (captionsEnabled) MaterialSymbols.ClosedCaption else MaterialSymbols.ClosedCaptionDisabled
    val captionsContentDescription =
        stringRes(if (captionsEnabled) Res.string.captions_turn_off else Res.string.captions_turn_on)
    val shareDialogVisible = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val castDialogVisible = remember { mutableStateOf(false) }
    val castSessionState by Amethyst.instance.castRegistry.sessionState
        .collectAsStateWithLifecycle()
    val isThisVideoCasting =
        (castSessionState as? CastSessionState.Casting)?.request?.url == mediaData.videoUri
    val castIcon = if (isThisVideoCasting) MaterialSymbols.CastConnected else MaterialSymbols.Cast
    val castContentDescription =
        stringRes(if (isThisVideoCasting) Res.string.cast_stop_casting else Res.string.cast_to_device)
    // Android 17 Local Network Protection blocks Cast device discovery until the
    // user grants ACCESS_LOCAL_NETWORK, so gate the picker behind the request.
    val showCastPicker = remember { { castDialogVisible.value = true } }
    val openCastPicker = rememberCastWithLocalNetworkPermission(context, showCastPicker)
    val onCastButtonClick =
        remember(isThisVideoCasting, openCastPicker) {
            {
                if (isThisVideoCasting) {
                    Amethyst.instance.applicationIOScope.launch {
                        Amethyst.instance.castRegistry.stopCasting()
                    }
                } else {
                    openCastPicker()
                }
                Unit
            }
        }
    val saveAction =
        rememberSaveMediaAction { context ->
            accountViewModel.saveMediaToGallery(mediaData.videoUri, mediaData.mimeType, context)
        }

    fun isAvailable(action: VideoPlayerAction): Boolean =
        when (action) {
            VideoPlayerAction.Fullscreen -> {
                onZoomClick != null
            }

            VideoPlayerAction.Mute -> {
                true
            }

            VideoPlayerAction.Quality -> {
                hasMultipleQualities
            }

            VideoPlayerAction.Share -> {
                true
            }

            VideoPlayerAction.Download -> {
                !isLive
            }

            VideoPlayerAction.PictureInPicture -> {
                pipSupported
            }

            VideoPlayerAction.Cast -> {
                BuildConfig.IS_CASTING_AVAILABLE && mediaData.videoUri.startsWith("http", ignoreCase = true)
            }

            // A video with no `text-track` has nothing to toggle, so the button stays out of the
            // row entirely rather than sitting there inert.
            VideoPlayerAction.Captions -> {
                mediaData.captions.isNotEmpty()
            }
        }

    val canFullscreen = onZoomClick != null
    val hasCaptions = mediaData.captions.isNotEmpty()
    // ImmutableList so Compose can treat the action lists as stable parameters when they're
    // passed through to AnimatedOverflowMenuButton — a plain List is unstable and forces the
    // overflow tree to recompose whenever any unrelated parent state ticks.
    val topBarActions =
        remember(buttonItems, canFullscreen, hasMultipleQualities, isLive, pipSupported, hasCaptions) {
            buttonItems
                .filter { it.location == VideoButtonLocation.TopBar && isAvailable(it.action) }
                .map { it.action }
                .toImmutableList()
        }
    val overflowActions =
        remember(buttonItems, canFullscreen, hasMultipleQualities, isLive, pipSupported, hasCaptions) {
            buttonItems
                .filter { it.location == VideoButtonLocation.OverflowMenu && isAvailable(it.action) }
                .map { it.action }
                .toImmutableList()
        }

    Row(modifier) {
        topBarActions.forEach { action ->
            when (action) {
                VideoPlayerAction.Fullscreen -> {
                    onZoomClick?.let {
                        FullScreenButton(
                            controllerVisible = controllerVisible,
                            onClick = it,
                        )
                    }
                }

                VideoPlayerAction.Mute -> {
                    MuteButton(
                        controllerVisible = controllerVisible,
                        startingMuteState = startingMuteState,
                        toggle = onMuteClick,
                    )
                }

                VideoPlayerAction.Quality -> {
                    qualityButton()
                }

                VideoPlayerAction.Share -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.Share,
                        contentDescription = stringRes(Res.string.share_or_save),
                        onClick = { shareDialogVisible.value = true },
                    )
                }

                VideoPlayerAction.Download -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.SaveAlt,
                        contentDescription = stringRes(Res.string.download_to_phone),
                        onClick = saveAction,
                    )
                }

                VideoPlayerAction.PictureInPicture -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.PictureInPicture,
                        contentDescription = stringRes(Res.string.picture_in_picture),
                        onClick = onPictureInPictureClick,
                    )
                }

                VideoPlayerAction.Cast -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = castIcon,
                        contentDescription = castContentDescription,
                        onClick = onCastButtonClick,
                    )
                }

                VideoPlayerAction.Captions -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = captionsIcon,
                        contentDescription = captionsContentDescription,
                        onClick = onCaptionsClick,
                    )
                }
            }
        }

        if (overflowActions.isNotEmpty()) {
            AnimatedOverflowMenuButton(
                controllerVisible = controllerVisible,
                actions = overflowActions,
                onFullscreenClick = onZoomClick,
                onMuteClick = { onMuteClick(!startingMuteState) },
                startingMuteState = startingMuteState,
                onQualityClick = onOverflowQualityClick,
                onShareClick = { shareDialogVisible.value = true },
                onSaveClick = saveAction,
                onPipClick = onPictureInPictureClick,
                onCastClick = onCastButtonClick,
                castIcon = castIcon,
                castContentDescription = castContentDescription,
                onCaptionsClick = onCaptionsClick,
                captionsIcon = captionsIcon,
                captionsContentDescription = captionsContentDescription,
            )
        }

        if (castDialogVisible.value) {
            CastDevicePickerDialog(
                request =
                    CastRequest(
                        url = mediaData.videoUri,
                        mimeType = mediaData.mimeType,
                        title = mediaData.title,
                        artworkUri = mediaData.artworkUri,
                        // By the time the cast button is reachable the player has already parsed the
                        // playlist, so the learned verdict is normally available here.
                        isLive =
                            resolveCastLiveness(
                                learned = HlsLivenessCache.verdict(mediaData.videoUri),
                                metadataFlag = mediaData.isLiveStream,
                            ),
                        // The local player has already decoded this, so it knows what the receiver
                        // is about to be handed — the only description of the media available when
                        // the receiver refuses it without saying why.
                        formatSummary = castFormatSummary,
                    ),
                onDismiss = { castDialogVisible.value = false },
            )
        }

        if (shareDialogVisible.value) {
            ShareMediaAction(
                popupExpanded = shareDialogVisible,
                videoUri = mediaData.videoUri,
                postNostrUri = mediaData.callbackUri,
                blurhash = mediaData.blurhash,
                dim = mediaData.dim,
                hash = mediaData.hash,
                mimeType = mediaData.mimeType,
                onDismiss = { shareDialogVisible.value = false },
                content =
                    MediaUrlVideo(
                        url = mediaData.videoUri,
                        mimeType = mediaData.mimeType,
                        artworkUri = mediaData.artworkUri,
                        authorName = mediaData.authorName,
                        description = mediaData.title,
                        uri = mediaData.callbackUri,
                        blurhash = mediaData.blurhash,
                        dim = mediaData.dim,
                        hash = mediaData.hash,
                        thumbhash = mediaData.thumbhash,
                    ),
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
internal fun AnimatedTopBarIconButton(
    controllerVisible: State<Boolean>,
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.7f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = onClick,
                modifier = Size50Modifier,
            ) {
                Icon(
                    symbol = symbol,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Size20Modifier,
                )
            }
        }
    }
}

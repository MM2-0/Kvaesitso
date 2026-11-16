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
package com.vitorpamplona.amethyst.ui.components

import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.collection.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.mediaFormatLabel
import com.vitorpamplona.amethyst.commons.audio.player.AudioCardInfo
import com.vitorpamplona.amethyst.commons.audio.player.AudioPlaybackUi
import com.vitorpamplona.amethyst.commons.audio.player.PlayableMediaCard
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled_no_format
import com.vitorpamplona.amethyst.commons.resources.playable_media_untitled
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.components.rememberMediaSize
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.rememberAudioPlaybackUi
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.note.types.RenderTopButtonsForVoice

/**
 * What the player found when it probed a URL: true when every track is audio, false when there is a
 * video track. Remembered per URL so a post scrolled back into view takes its probed shape at once
 * instead of starting from its declaration and reflowing again.
 */
object PlayableMediaProbeCache {
    private val cache = LruCache<String, Boolean>(500)

    fun get(url: String): Boolean? = cache[url]

    fun put(
        url: String,
        audioOnly: Boolean,
    ) {
        cache.put(url, audioOnly)
    }
}

/** Audio at least this long gets a picture-in-picture button on its card: it will outlast the post on screen. */
private const val LONG_AUDIO_MS = 10 * 60 * 1000L

/**
 * A playable file inside a post that is (or may be) audio: the shared [PlayableMediaCard] for [layout],
 * driven by the same pooled player as every other inline media.
 *
 * Unlike a feed video it never autoplays — audio playing muted is pointless and playing it aloud
 * unasked is worse — and it does not loop. With automatic media loading off (data saver), nothing is
 * fetched until play is pressed. Once the player knows the tracks, [onProbed] reports whether they are
 * all audio, which is how an undecided file finds its real card and how a mislabelled video leaves for
 * the video player.
 *
 * Known audio that runs [LONG_AUDIO_MS] or more (by the player, else the imeta `duration`) shows a
 * picture-in-picture button, so a podcast can keep playing in a floating window while the feed scrolls.
 */
@Composable
fun PlayableAudioView(
    content: MediaUrlVideo,
    layout: PlayableLayout,
    onProbed: (audioOnly: Boolean) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val authorName =
        remember(content.authorName, content.authorPubKey) {
            content.authorName ?: content.authorPubKey?.let { accountViewModel.getUserIfExists(it)?.toBestDisplayName() }
        }

    // The card is drawn whatever the setting says; ask the host for a size only while files are held back.
    val size = rememberMediaSize(content, accountViewModel, held = !accountViewModel.settings.startVideoPlayback())

    val info =
        remember(content, authorName, size) {
            AudioCardInfo(
                title = null,
                artist = authorName,
                format = mediaFormatLabel(content.mimeType, content.url),
                sizeBytes = size,
                seed = content.hash ?: content.url,
                waveform = content.waveform,
                artworkUrl = content.artworkUri,
                declaredDurationMs = content.durationSeconds?.let { (it * 1000).toLong() },
            )
        }

    // The name the system media notification shows.
    val mediaTitle =
        when {
            layout == PlayableLayout.UNDECIDED -> stringRes(Res.string.playable_media_untitled)
            info.format != null -> stringRes(Res.string.audio_card_untitled, info.format)
            else -> stringRes(Res.string.audio_card_untitled_no_format)
        }

    val mediaData =
        remember(content, mediaTitle, authorName) {
            MediaItemData(
                videoUri = content.url,
                authorName = authorName,
                title = mediaTitle,
                artworkUri = content.artworkUri,
                callbackUri = content.uri,
                mimeType = content.mimeType,
                proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(content.url),
                keepPlaying = false,
                isLiveStream = content.isLiveStream,
                hash = content.hash,
            )
        }

    var load by remember(content.url) { mutableStateOf(accountViewModel.settings.startVideoPlayback()) }
    var playWhenConnected by remember(content.url) { mutableStateOf(false) }
    var active by remember(content.url) { mutableStateOf<MediaControllerState?>(null) }
    val controllerVisible = remember(content.url) { mutableStateOf(false) }
    // Where picture-in-picture animates from.
    val bounds = remember(content.url) { arrayOf<Rect?>(null) }
    val pipSupported = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    // Hands playback to the PiP window: it attaches to the same pooled player (same URL), so pausing
    // here first keeps the two from playing over each other, and it resumes from this position.
    val startPictureInPicture = {
        active?.controller?.pause()
        PipVideoActivity.callIn(
            mediaData.copy(
                // Square window, and the audio view instead of an empty video surface.
                aspectRatio = 1f,
                isAudio = true,
                waveformData = WaveformData(info.waveform ?: syntheticWaveformFor(info.seed).wave),
            ),
            bounds[0],
            context.getActivity(),
        )
    }

    if (load) {
        GetMediaItem(mediaData) { mediaItem ->
            GetVideoController(mediaItem = mediaItem, muted = false) { controller ->
                PauseControllerWhenInBackground(controller)
                ReportProbe(controller.controller, onProbed)

                DisposableEffect(controller) {
                    active = controller
                    onDispose {
                        if (active === controller) active = null
                    }
                }

                // Play was pressed before the controller connected.
                LaunchedEffect(controller) {
                    if (playWhenConnected) {
                        playWhenConnected = false
                        controller.controller.play()
                    }
                }
            }
        }
    }

    val trackBounds =
        Modifier.onGloballyPositioned { coordinates ->
            val b = coordinates.boundsInWindow()
            bounds[0] = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
        }

    Box {
        val controller = active
        if (controller != null) {
            val playback by rememberAudioPlaybackUi(controller.controller)
            val totalMs = playback.durationMs ?: info.declaredDurationMs
            PlayableMediaCard(
                layout = layout,
                info = info,
                playback = playback,
                onPlayPause = { playOrPause(controller.controller) },
                onSeek = { fraction -> playback.durationMs?.let { controller.controller.seekTo((it * fraction).toLong()) } },
                onClick = { controllerVisible.value = !controllerVisible.value },
                onPictureInPicture = startPictureInPicture.takeIf { pipSupported && layout != PlayableLayout.UNDECIDED && totalMs != null && totalMs >= LONG_AUDIO_MS },
                modifier = trackBounds,
                overlay = {
                    RenderTopButtonsForVoice(
                        mediaData = mediaData,
                        controllerVisible = controllerVisible,
                        startingMuteState = controller.controller.volume < 0.001,
                        onMuteClick = { mute ->
                            DEFAULT_MUTED_SETTING.value = mute
                            controller.controller.volume = if (mute) 0f else 1f
                        },
                        onPictureInPictureClick = startPictureInPicture,
                        modifier = Modifier.align(Alignment.TopEnd),
                        accountViewModel = accountViewModel,
                    )
                },
            )
        } else {
            val totalMs = info.declaredDurationMs
            PlayableMediaCard(
                layout = layout,
                info = info,
                playback = AudioPlaybackUi.Idle,
                onPlayPause = {
                    playWhenConnected = true
                    load = true
                },
                onSeek = {},
                onPictureInPicture = startPictureInPicture.takeIf { pipSupported && layout != PlayableLayout.UNDECIDED && totalMs != null && totalMs >= LONG_AUDIO_MS },
                modifier = trackBounds,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun playOrPause(player: Player) {
    // Handles the idle (re-prepare) and ended (restart) states, not just play/pause.
    Util.handlePlayPauseButtonAction(player)
}

/** True when the tracks are all audio, false when one is video, null while the player hasn't found any. */
internal fun Tracks.audioOnlyOrNull(): Boolean? {
    if (groups.isEmpty()) return null
    if (groups.any { it.type == C.TRACK_TYPE_VIDEO }) return false
    return if (groups.any { it.type == C.TRACK_TYPE_AUDIO }) true else null
}

@Composable
private fun ReportProbe(
    player: Player,
    onProbed: (Boolean) -> Unit,
) {
    val currentOnProbed by rememberUpdatedState(onProbed)
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    tracks.audioOnlyOrNull()?.let { currentOnProbed(it) }
                }
            }
        player.addListener(listener)
        player.currentTracks.audioOnlyOrNull()?.let { currentOnProbed(it) }
        onDispose { player.removeListener(listener) }
    }
}

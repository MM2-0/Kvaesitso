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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.re_record
import com.vitorpamplona.amethyst.commons.resources.record_a_message
import com.vitorpamplona.amethyst.commons.resources.recording_indicator_description
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.components.AudioWaveformReadOnly
import com.vitorpamplona.amethyst.commons.ui.note.platform.MAX_VOICE_RECORD_SECONDS
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatSecondsToTime
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okio.Path

@Composable
fun VoiceMessagePreview(
    voiceMetadata: AudioMeta,
    localFile: Path? = null,
    onRemove: () -> Unit,
    onReRecord: ((RecordingResult) -> Unit)? = null,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    ManageMediaPlayer(
        voiceMetadata = voiceMetadata,
        localFile = localFile,
        onCompletion = {
            isPlaying = false
            progress = 0f
        },
        onPlayerChanged = { mediaPlayer = it },
        onRelease = { isPlaying = false },
    )

    TrackPlaybackProgress(
        mediaPlayer = mediaPlayer,
        isPlaying = isPlaying,
        onProgressUpdate = { progress = it },
        onInvalidState = { isPlaying = false },
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Play/Pause Button
                IconButton(
                    onClick = {
                        handlePlayPauseClick(
                            mediaPlayer = mediaPlayer,
                            isPlaying = isPlaying,
                            progress = progress,
                            onProgressReset = { progress = 0f },
                            onPlayingChanged = { isPlaying = it },
                        )
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
                        contentDescription = if (isPlaying) stringRes(Res.string.pause) else stringRes(Res.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Waveform and Duration
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AudioWaveformReadOnly(
                        amplitudes = voiceMetadata.waveform ?: emptyList(),
                        progress = progress,
                        waveformBrush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)),
                        progressBrush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                        onProgressChange = { newProgress ->
                            handleWaveformScrub(
                                newProgress = newProgress,
                                mediaPlayer = mediaPlayer,
                                onProgressChanged = { progress = it },
                            )
                        },
                    )

                    Text(
                        text = formatSecondsToTime(voiceMetadata.duration ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Remove Button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onReRecord != null) {
                Spacer(modifier = Modifier.size(8.dp))
                ReRecordButton(
                    isUploading = isUploading,
                    isPlaying = isPlaying,
                    onRecordTaken = onReRecord,
                )
            }
        }
    }
}

@Composable
private fun ReRecordButton(
    isUploading: Boolean,
    isPlaying: Boolean,
    onRecordTaken: (RecordingResult) -> Unit,
) {
    if (isUploading || isPlaying) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Mic,
                contentDescription = stringRes(id = Res.string.record_a_message),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringRes(id = Res.string.re_record),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    RecordAudioBox(
        modifier = Modifier,
        onRecordTaken = onRecordTaken,
        maxDurationSeconds = MAX_VOICE_RECORD_SECONDS,
    ) { isRecording, elapsedSeconds, _ ->
        val contentColor =
            if (isRecording) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        val icon =
            if (isRecording) {
                MaterialSymbols.Stop
            } else {
                MaterialSymbols.Mic
            }
        val label =
            if (isRecording) {
                formatSecondsToTime(elapsedSeconds)
            } else {
                stringRes(id = Res.string.re_record)
            }
        val iconDescription =
            if (isRecording) {
                stringRes(id = Res.string.recording_indicator_description)
            } else {
                stringRes(id = Res.string.record_a_message)
            }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = iconDescription,
                tint = contentColor,
            )
            Text(
                text = label,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ManageMediaPlayer(
    voiceMetadata: AudioMeta,
    localFile: Path?,
    onCompletion: () -> Unit,
    onPlayerChanged: (MediaPlayer?) -> Unit,
    onRelease: () -> Unit,
) {
    DisposableEffect(voiceMetadata.url, localFile) {
        val player = createMediaPlayer(voiceMetadata.url, localFile)
        player?.setOnCompletionListener { onCompletion() }
        onPlayerChanged(player)

        onDispose {
            try {
                player?.stop()
            } catch (e: IllegalStateException) {
                Log.d("VoiceMessagePreview", "MediaPlayer stop failed (already stopped)", e)
            }
            player?.release()
            onPlayerChanged(null)
            onRelease()
        }
    }
}

@Composable
private fun TrackPlaybackProgress(
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    onProgressUpdate: (Float) -> Unit,
    onInvalidState: () -> Unit,
) {
    LaunchedEffect(mediaPlayer, isPlaying) {
        val player = mediaPlayer ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            try {
                if (!player.isPlaying) break
                calculateProgress(player.currentPosition.toFloat(), player.duration.toFloat())?.let { onProgressUpdate(it) }
            } catch (e: IllegalStateException) {
                Log.w("VoiceMessagePreview", "MediaPlayer in invalid state during progress tracking", e)
                onInvalidState()
                break
            }
            delay(100)
        }
    }
}

private fun calculateProgress(
    current: Float,
    duration: Float,
): Float? {
    val progress =
        if (duration > 0 && current >= 0) {
            (current / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
    return progress.takeIf { it.isFinite() }
}

private fun handlePlayPauseClick(
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    progress: Float,
    onProgressReset: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
) {
    val player = mediaPlayer ?: return
    try {
        if (isPlaying) {
            player.pause()
            onPlayingChanged(false)
            return
        }
        if (progress.isFinite() && progress >= 1f) {
            player.seekTo(0)
            onProgressReset()
        }
        player.start()
        onPlayingChanged(true)
    } catch (e: IllegalStateException) {
        Log.w("VoiceMessagePreview", "MediaPlayer operation failed in onClick handler", e)
        onPlayingChanged(false)
    }
}

private fun handleWaveformScrub(
    newProgress: Float,
    mediaPlayer: MediaPlayer?,
    onProgressChanged: (Float) -> Unit,
) {
    if (!newProgress.isFinite() || newProgress < 0f || newProgress > 1f) return
    val player = mediaPlayer ?: return
    try {
        val duration = player.duration
        if (duration > 0) {
            val newPosition = (newProgress * duration).toInt()
            player.seekTo(newPosition)
            onProgressChanged(newProgress)
        }
    } catch (e: IllegalStateException) {
        Log.w("VoiceMessagePreview", "MediaPlayer seek failed in onProgressChange", e)
    }
}

private fun createMediaPlayer(
    url: String,
    localFile: Path?,
): MediaPlayer? =
    try {
        MediaPlayer().apply {
            if (localFile != null && localFile.toFile().exists()) {
                setDataSource(localFile.toString())
            } else {
                setDataSource(url)
            }
            prepare()
        }
    } catch (e: Exception) {
        Log.w("VoiceMessagePreview", "Failed to create MediaPlayer", e)
        null
    }

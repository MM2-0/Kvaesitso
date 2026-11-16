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
package com.vitorpamplona.amethyst.commons.audio.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled_no_format
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.picture_in_picture
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.playable_media_untitled
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import kotlin.math.abs

/*
 * The cards a playable file inside a post renders as, instead of a video player with no picture.
 * Which one is [com.vitorpamplona.amethyst.commons.audio.playableLayout]'s call:
 *  - known audio, no artwork  -> [AudioWaveformCard]: the waveform is the seek bar (the file's own
 *    waveform, else the synthetic one every other audio renderer draws);
 *  - known audio with artwork -> [AudioCoverCard]: the artwork with the same scrubber over its foot;
 *  - audio or video, can't tell -> [UndecidedMediaCard], until the player's probe settles it.
 *
 * Stateless: the platform owns the player and feeds [AudioPlaybackUi] in, taking play/pause and seeks
 * back out. Everything in [AudioCardInfo] comes from the imeta, so each card has its final shape before
 * the player has loaded a byte.
 */

/** What a card shows about the file. All of it is known from the event, before the player loads anything. */
@Immutable
class AudioCardInfo(
    /** The track's name, when something names it. Null shows "MP3 audio" (or the like). */
    val title: String?,
    /** Usually the post's author. */
    val artist: String?,
    /** "MP3", "FLAC", "HLS"… from the declared MIME type or the URL. */
    val format: String?,
    val sizeBytes: Long?,
    /** The blob's sha256 (`x`), or the URL: seeds the synthetic waveform and the generated cover. */
    val seed: String,
    /** The file's own amplitude envelope (a `waveform` tag), any scale. Null draws the synthetic one. */
    val waveform: List<Float>? = null,
    /** The imeta `image`: the artwork [AudioCoverCard] shows. */
    val artworkUrl: String? = null,
    /** The imeta `duration`: shown until the player knows the real one. */
    val declaredDurationMs: Long? = null,
) {
    /** "MP3 · 4.8 MB" — known from the imeta alone. */
    val fileFacts: String
        get() = listOfNotNull(format, sizeBytes?.let(::formatFileSize)).joinToString(" · ")
}

/** What the player is doing. [durationMs] is null until it has probed the file. */
@Immutable
data class AudioPlaybackUi(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
) {
    val progress: Float
        get() = durationMs?.takeIf { it > 0 }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    companion object {
        val Idle = AudioPlaybackUi()
    }
}

/** The corners every card is drawn with, unless a host that already shapes it asks for others. */
private val CardShape = RoundedCornerShape(16.dp)

/**
 * Renders whichever card [layout] names. [PlayableLayout.VIDEO] is the video player's job and draws nothing here.
 * [shape] is the card's outline: a host that clips the card itself (a strip joined to a cover above it) passes
 * `RectangleShape` so the card's own corners don't show inside its clip.
 */
@Composable
fun PlayableMediaCard(
    layout: PlayableLayout,
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onPictureInPicture: (() -> Unit)? = null,
    shape: Shape = CardShape,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    when (layout) {
        PlayableLayout.AUDIO_WAVEFORM -> AudioWaveformCard(info, playback, onPlayPause, onSeek, modifier, onClick, onPictureInPicture, shape, overlay)
        PlayableLayout.AUDIO_COVER -> AudioCoverCard(info, playback, onPlayPause, onSeek, modifier, onClick, onPictureInPicture, shape, overlay)
        PlayableLayout.UNDECIDED -> UndecidedMediaCard(info, playback, onPlayPause, modifier, onClick, overlay)
        PlayableLayout.VIDEO -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// Known audio: the waveform is the seek bar.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioWaveformCard(
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Non-null shows the picture-in-picture button: meant for long audio, where the post will scroll away. */
    onPictureInPicture: (() -> Unit)? = null,
    shape: Shape = CardShape,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val bars = rememberBars(info)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickableWithoutRipple(onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayPauseButton(playback.isPlaying, size = 44.dp, onClick = onPlayPause)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                WaveformBars(
                    bars = bars,
                    progress = playback.progress,
                    played = MaterialTheme.colorScheme.primary,
                    unplayed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    onSeek = onSeek.takeIf { playback.durationMs != null },
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (playback.durationMs != null || info.declaredDurationMs != null) formatClock(playback.positionMs) else audioTitle(info),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        trailingFacts(info, playback),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (onPictureInPicture != null) {
                Spacer(Modifier.width(4.dp))
                PictureInPictureButton(onPictureInPicture, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        overlay()
    }
}

// ---------------------------------------------------------------------------------------------
// Known audio with artwork: the artwork, with the same scrubber over its foot.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioCoverCard(
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Non-null shows the picture-in-picture button: meant for long audio, where the post will scroll away. */
    onPictureInPicture: (() -> Unit)? = null,
    shape: Shape = CardShape,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val bars = rememberBars(info)
    // Cap the WIDTH, not the height: with a full width wider than the cap (a tablet), a height cap
    // left aspectRatio drawing a full-width square inside a 400dp-tall slot, and the overflow
    // spilled over the author row above and the reaction row below.
    Box(
        modifier =
            modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .clickableWithoutRipple(onClick),
    ) {
        // The generated cover sits under the artwork, so a slow or failed load still reads as a cover.
        GeneratedCover(info.seed, Modifier.fillMaxSize(), icon = null, record = info.artworkUrl == null)
        if (info.artworkUrl != null) {
            AsyncImage(
                model = info.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom scrim so the title and the waveform read on any cover.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        audioTitle(info),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    info.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                PlayPauseButton(playback.isPlaying, size = 52.dp, onClick = onPlayPause, container = Color.White, content = Color.Black)
            }
            Spacer(Modifier.height(12.dp))
            WaveformBars(
                bars = bars,
                progress = playback.progress,
                played = Color.White,
                unplayed = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                onSeek = onSeek.takeIf { playback.durationMs != null },
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    playback.durationMs?.let { formatClock(playback.positionMs) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    trailingFacts(info, playback),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
        if (onPictureInPicture != null) {
            PictureInPictureButton(
                onPictureInPicture,
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
            )
        }
        overlay()
    }
}

// ---------------------------------------------------------------------------------------------
// Picture-in-picture: what the small floating window shows for audio. Square, display-only (a PiP
// window takes no touches; the system draws play/pause over it), the cover behind the waveform.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioPipContent(
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
    modifier: Modifier = Modifier,
) {
    val bars = rememberBars(info)
    Box(modifier.fillMaxSize()) {
        GeneratedCover(info.seed, Modifier.fillMaxSize(), icon = null, record = info.artworkUrl == null)
        if (info.artworkUrl != null) {
            AsyncImage(
                model = info.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(
                audioTitle(info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info.artist?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            WaveformBars(
                bars = bars,
                progress = playback.progress,
                played = Color.White,
                unplayed = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(24.dp),
                barWidth = 2.dp,
                gap = 2.dp,
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                (playback.durationMs ?: info.declaredDurationMs)?.let {
                    Text(formatClock(it), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Audio or video, can't tell yet: commits to neither shape (no waveform, no picture box) and gives way
// to the waveform, the cover or the video player once the probe answers.
// ---------------------------------------------------------------------------------------------

@Composable
fun UndecidedMediaCard(
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickableWithoutRipple(onClick),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.Podcasts, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info.title ?: stringRes(Res.string.playable_media_untitled),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    info.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        trailingFacts(info, playback),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(playback.isPlaying, size = 48.dp, onClick = onPlayPause)
            }
            ThinProgress(playback.progress, Modifier.fillMaxWidth().height(3.dp))
        }
        overlay()
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

/** The file's own waveform when it has one, else the synthetic one every other audio renderer already uses. */
@Composable
private fun rememberBars(info: AudioCardInfo): List<Float> = remember(info.seed, info.waveform) { info.waveform?.let(::normalizeWave) ?: syntheticWaveformFor(info.seed).wave }

/** Scales a raw amplitude list (NIP-A0 sends 0..100, others 0..1 or raw RMS) onto 0..1. Null when it has no range. */
internal fun normalizeWave(wave: List<Float>): List<Float>? {
    val max = wave.maxOrNull() ?: return null
    if (max <= 0f || !max.isFinite()) return null
    return wave.map { (it / max).coerceIn(0f, 1f) }
}

/** The track's name, or "MP3 audio" when nothing names it. */
@Composable
private fun audioTitle(info: AudioCardInfo): String =
    info.title
        ?: info.format?.let { stringRes(Res.string.audio_card_untitled, it) }
        ?: stringRes(Res.string.audio_card_untitled_no_format)

/** "MP3 · 4.8 MB · 3:28" once the duration is known; the file facts before that. */
private fun trailingFacts(
    info: AudioCardInfo,
    playback: AudioPlaybackUi,
): String = listOfNotNull(info.fileFacts.ifEmpty { null }, (playback.durationMs ?: info.declaredDurationMs)?.let(::formatClock)).joinToString(" · ")

private fun Modifier.clickableWithoutRipple(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) {
        this
    } else {
        this.then(Modifier.clickable(interactionSource = null, indication = null, onClick = onClick))
    }

@Composable
private fun PictureInPictureButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MaterialSymbols.PictureInPicture, stringRes(Res.string.picture_in_picture), Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    size: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(container)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
            contentDescription = stringRes(if (isPlaying) Res.string.pause else Res.string.play),
            modifier = Modifier.size(size * 0.6f),
            tint = content,
            filled = true,
        )
    }
}

/** A cover generated from the file hash: a two-tone gradient, optionally a record, optionally a glyph. */
@Composable
fun GeneratedCover(
    seed: String,
    modifier: Modifier = Modifier,
    icon: MaterialSymbol?,
    iconSize: Dp = 28.dp,
    record: Boolean = false,
) {
    val (top, bottom) = remember(seed) { seedColors(seed) }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (record) RecordArt()
        if (icon != null) {
            Icon(icon, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.9f), filled = true)
        }
    }
}

@Composable
private fun BoxScope.RecordArt() {
    Canvas(Modifier.matchParentSize()) {
        val center = Offset(size.width * 0.62f, size.height * 0.34f)
        val radius = size.minDimension * 0.4f
        drawCircle(Color.Black.copy(alpha = 0.55f), radius, center)
        var r = radius * 0.95f
        while (r > radius * 0.4f) {
            drawCircle(Color.White.copy(alpha = 0.06f), r, center, style = Stroke(width = 1.5f))
            r -= radius * 0.06f
        }
        drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.3f, center)
        drawCircle(Color.Black.copy(alpha = 0.8f), radius * 0.04f, center)
    }
}

@Composable
private fun ThinProgress(
    progress: Float,
    modifier: Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * progress, size.height))
    }
}

/**
 * Centred bars, coloured [played] up to [progress] and [unplayed] after it. With [onSeek], a tap or a
 * horizontal drag seeks: the drag previews its position and seeks once, on release.
 */
@Composable
fun WaveformBars(
    bars: List<Float>,
    progress: Float,
    played: Color,
    unplayed: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.dp,
    onSeek: ((Float) -> Unit)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val seekModifier =
        if (onSeek == null) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnSeek?.invoke((offset.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { currentOnSeek?.invoke(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
        }

    Canvas(modifier.then(seekModifier)) {
        if (bars.isEmpty()) return@Canvas
        val shown = dragFraction ?: progress
        val step = (barWidth + gap).toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val w = barWidth.toPx()
        for (i in 0 until count) {
            val amp = bars[(i * bars.size) / count].coerceIn(0.08f, 1f)
            val h = amp * size.height
            val x = i * step
            drawRoundRect(
                color = if (x / size.width < shown) played else unplayed,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

/** Two colours derived from the hash, so every file gets its own stable cover. */
fun seedColors(seed: String): Pair<Color, Color> {
    val n = seed.take(6).toIntOrNull(16) ?: seed.hashCode()
    val hue = (abs(n) % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.62f) to Color.hsv((hue + 48f) % 360f, 0.75f, 0.32f)
}

fun formatClock(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secs" else "$minutes:$secs"
}

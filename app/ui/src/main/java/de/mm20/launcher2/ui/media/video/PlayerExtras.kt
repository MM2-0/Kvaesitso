package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * Gestures of the player (as in Next Player and mpv-android): swipe left or right to seek, swipe up
 * or down on the left half for brightness and on the right half for volume, double tap on a side
 * to jump 10 seconds, press and hold for double speed.
 */
internal fun attachGestures(
    view: PlayerView,
    player: Player,
    activity: Activity?,
    userSpeed: () -> Float,
    onHint: (String?) -> Unit,
    onLevels: (brightness: Float?, volume: Float?, active: Boolean) -> Unit = { _, _, _ -> },
) {
    val context = view.context
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var mode = 0 // 1 seek, 2 brightness, 3 volume
    var startPosition = 0L
    var seekTarget = 0L
    var volumeAccumulator = 0f
    var longPressed = false
    var doubleTapped = false

    fun time(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // PlayerView fills the window, including portrait letterbox areas.
            // PlayerView.performClick owns its controller toggle and accessibility event.
            view.performClick()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null || view.width == 0 || view.height == 0) return false
            if (mode == 0) {
                mode = when {
                    abs(e2.x - e1.x) > abs(e2.y - e1.y) -> 1
                    e1.x < view.width / 2f -> 2
                    else -> 3
                }
                startPosition = player.currentPosition
                volumeAccumulator = 0f
            }
            when (mode) {
                1 -> {
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    val delta = ((e2.x - e1.x) / view.width * 90_000f).toLong()
                    seekTarget = (startPosition + delta).coerceIn(0, duration)
                    onHint("${time(seekTarget)}  (${if (delta >= 0) "+" else "-"}${time(abs(delta))})")
                }
                2 -> {
                    val window = activity?.window ?: return true
                    val attrs = window.attributes
                    val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                    attrs.screenBrightness = (current + distanceY / view.height).coerceIn(0.02f, 1f)
                    window.attributes = attrs
                    onLevels(attrs.screenBrightness, null, true)
                    onHint(context.getString(I18nR.string.au_video_brightness, (attrs.screenBrightness * 100).toInt()))
                }
                3 -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    volumeAccumulator += distanceY / view.height * max * 1.5f
                    if (abs(volumeAccumulator) >= 1f) {
                        val step = volumeAccumulator.toInt()
                        volumeAccumulator -= step
                        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (now + step).coerceIn(0, max), 0)
                    }
                    val vol = if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
                    onLevels(null, vol, true)
                    onHint(context.getString(I18nR.string.au_video_volume, if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0))
                }
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTapped = true
            val forward = e.x > view.width / 2f
            player.seekTo((player.currentPosition + if (forward) 10_000 else -10_000).coerceAtLeast(0))
            onHint(context.getString(if (forward) I18nR.string.au_video_skip_forward else I18nR.string.au_video_skip_back))
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            longPressed = true
            player.setPlaybackSpeed(2f)
            onHint("2×")
        }
    })

    view.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) doubleTapped = false
        detector.onTouchEvent(event)
        var consumed = mode != 0 || longPressed || doubleTapped
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (mode == 1) player.seekTo(seekTarget)
            if (longPressed) player.setPlaybackSpeed(userSpeed())
            val wasGesture = consumed
            if (mode == 2 || mode == 3) onLevels(null, null, false)
            mode = 0
            longPressed = false
            onHint(null)
            consumed = wasGesture
        }
        // Own the whole surface gesture sequence so PlayerView cannot also
        // toggle on ACTION_UP before onSingleTapConfirmed handles the tap.
        true
    }
}

/** Speed, picture size, audio and subtitle tracks, loop and a sleep timer. */
@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackMenu(
    player: Player,
    playerView: PlayerView?,
    speed: Float,
    onSpeed: (Float) -> Unit,
    sleepMinutes: Int,
    onSleep: (Int) -> Unit,
    subDelayMs: Long,
    onSubDelay: (Long) -> Unit,
    hasExternalSub: Boolean,
    subStyle: de.mm20.launcher2.comms.media.video.VideoPrefs.SubtitleStyle,
    onSubStyle: (de.mm20.launcher2.comms.media.video.VideoPrefs.SubtitleStyle) -> Unit,
    matchFps: Boolean,
    onMatchFps: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var aspect by remember { mutableIntStateOf(playerView?.resizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var loop by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var refresh by remember { mutableIntStateOf(0) }
    val groups = remember(refresh) { player.currentTracks.groups }
    val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    fun label(g: androidx.media3.common.Tracks.Group, fallback: String): String {
        val f = g.getTrackFormat(0)
        return listOfNotNull(f.label, f.language?.uppercase(), f.codecs?.takeIf { g.type == C.TRACK_TYPE_AUDIO }, f.channelCount.takeIf { it > 0 && g.type == C.TRACK_TYPE_AUDIO }?.let { context.getString(I18nR.string.au_video_channels, it) })
            .joinToString(" · ").ifBlank { fallback }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_playback))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(I18nR.string.hc_speed), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f).forEach { v ->
                        FilterChip(selected = speed == v, onClick = { onSpeed(v) }, label = { Text("${v}×".replace(".0×", "×")) })
                    }
                }

                Text("Frame Stepping (NeriPlayer)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { player.seekTo((player.currentPosition - 41L).coerceAtLeast(0L)) }) {
                        Text("⏮ -1 Frame")
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { player.seekTo((player.currentPosition + 41L).coerceAtLeast(0L)) }) {
                        Text("⏭ +1 Frame")
                    }
                }
                Text(stringResource(I18nR.string.hc_picture), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(
                        stringResource(I18nR.string.au_video_fit) to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        stringResource(I18nR.string.au_video_fill) to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                        stringResource(I18nR.string.au_video_zoom) to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        stringResource(I18nR.string.vn_fixed_width) to AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                        stringResource(I18nR.string.vn_fixed_height) to AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
                    ).forEach { (name, mode) ->
                        FilterChip(
                            selected = aspect == mode,
                            onClick = {
                                aspect = mode
                                playerView?.resizeMode = mode
                                de.mm20.launcher2.comms.media.video.VideoPrefs.setResizeMode(context, mode)
                            },
                            label = { Text(name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(I18nR.string.vn_match_frame_rate), modifier = Modifier.weight(1f))
                    Switch(checked = matchFps, onCheckedChange = onMatchFps)
                }
                var resumeAfter by remember { mutableStateOf(de.mm20.launcher2.comms.media.video.VideoPrefs.resumeAfterVideo(context)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(I18nR.string.au9_playback_resume_after_video), modifier = Modifier.weight(1f))
                    Switch(
                        checked = resumeAfter,
                        onCheckedChange = {
                            resumeAfter = it
                            de.mm20.launcher2.comms.media.video.VideoPrefs.setResumeAfterVideo(context, it)
                        },
                    )
                }

                var showAudioFx by remember { mutableStateOf(false) }
                if (showAudioFx) {
                    de.mm20.launcher2.ui.media.music.AudioFxDialog(onDismiss = { showAudioFx = false })
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showAudioFx = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Audio Effects & 200% Boost")
                }

                val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audio.size > 1) {
                    Text(stringResource(I18nR.string.hc_audio), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    audio.forEachIndexed { i, g ->
                        FilterChip(
                            selected = g.isSelected,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                                refresh++
                            },
                            label = { Text(label(g, stringResource(I18nR.string.au_video_track_n, i + 1))) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (text.isNotEmpty()) {
                    Text(stringResource(I18nR.string.hc_subtitles), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    FilterChip(
                        selected = textDisabled,
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            refresh++
                        },
                        label = { Text(stringResource(I18nR.string.hc_off)) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    text.forEachIndexed { i, g ->
                        FilterChip(
                            selected = g.isSelected && !textDisabled,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                                refresh++
                            },
                            label = { Text(label(g, stringResource(I18nR.string.au_video_subtitle_n, i + 1))) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                Text(stringResource(I18nR.string.vn_subtitle_delay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = hasExternalSub, onClick = { onSubDelay(subDelayMs - 500) }) { Text(stringResource(I18nR.string.au_video_seconds_signed, -0.5f)) }
                    TextButton(enabled = hasExternalSub, onClick = { onSubDelay(subDelayMs - 100) }) { Text("-0.1") }
                    Text(stringResource(I18nR.string.au_video_seconds_signed, subDelayMs / 1000f), modifier = Modifier.weight(1f))
                    TextButton(enabled = hasExternalSub, onClick = { onSubDelay(subDelayMs + 100) }) { Text("+0.1") }
                    TextButton(enabled = hasExternalSub, onClick = { onSubDelay(subDelayMs + 500) }) { Text(stringResource(I18nR.string.au_video_seconds_signed, 0.5f)) }
                }
                Text(stringResource(I18nR.string.vn_subtitle_delay_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text(stringResource(I18nR.string.vn_subtitle_size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(
                        I18nR.string.vn_size_small to 0.75f, I18nR.string.vn_size_normal to 1f,
                        I18nR.string.vn_size_large to 1.4f, I18nR.string.vn_size_huge to 1.9f,
                    ).forEach { (name, v) ->
                        FilterChip(selected = subStyle.scale == v, onClick = { onSubStyle(subStyle.copy(scale = v)) }, label = { Text(stringResource(name)) })
                    }
                }
                Text(stringResource(I18nR.string.vn_subtitle_color), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(
                        I18nR.string.vn_color_white to 0xFFFFFFFF.toInt(), I18nR.string.vn_color_yellow to 0xFFFFEB3B.toInt(),
                        I18nR.string.vn_color_green to 0xFF8BC34A.toInt(), I18nR.string.vn_color_cyan to 0xFF4DD0E1.toInt(),
                    ).forEach { (name, c) ->
                        FilterChip(selected = subStyle.color == c, onClick = { onSubStyle(subStyle.copy(color = c)) }, label = { Text(stringResource(name)) })
                    }
                }
                Text(stringResource(I18nR.string.vn_subtitle_edge), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(
                        I18nR.string.vn_edge_none to 0, I18nR.string.vn_edge_outline to 1,
                        I18nR.string.vn_edge_shadow to 2, I18nR.string.vn_edge_box to 3,
                    ).forEach { (name, e) ->
                        FilterChip(selected = subStyle.edge == e, onClick = { onSubStyle(subStyle.copy(edge = e)) }, label = { Text(stringResource(name)) })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(I18nR.string.hc_repeat_this_video), modifier = Modifier.weight(1f))
                    Switch(checked = loop, onCheckedChange = {
                        loop = it
                        player.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    })
                }
                Text(stringResource(I18nR.string.hc_sleep_timer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    listOf(0, 15, 30, 60).forEach { m ->
                        FilterChip(selected = sleepMinutes == m, onClick = { onSleep(m) }, label = { Text(if (m == 0) stringResource(I18nR.string.hc_off) else stringResource(I18nR.string.au_video_minutes, m)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_done)) } },
    )
}

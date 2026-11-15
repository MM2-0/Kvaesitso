package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Shared controls for activity-backed videos and the TV dialog's own window. */
@Composable
internal fun PlayerLevelControls(
    visible: Boolean,
    window: Window?,
    modifier: Modifier = Modifier,
    onAdjusting: (Boolean) -> Unit = {},
    brightnessOverride: Float? = null,
    volumeOverride: Float? = null,
    onVolumeBoost: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
    }
    fun brightness(): Float = window?.attributes?.screenBrightness?.takeIf { it >= 0f }
        ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    var light by remember(window) { mutableFloatStateOf(brightness().coerceIn(0.02f, 1f)) }
    var sound by remember(audio) { mutableFloatStateOf(volume()) }
    DisposableEffect(window) {
        val original = window?.attributes?.screenBrightness
        onDispose {
            if (window != null && original != null) {
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
            onAdjusting(false)
        }
    }
    LaunchedEffect(visible, window, brightnessOverride, volumeOverride) {
        while (visible) {
            val systemVolume = volumeOverride ?: volume()
            if (sound > 1f && systemVolume < 0.99f) onVolumeBoost?.invoke(1f)
            if (sound <= 1f || systemVolume < 0.99f) sound = systemVolume
            light = (brightnessOverride ?: brightness()).coerceIn(0.02f, 1f)
            delay(50)
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 88.dp)) {
            val railHeight = (maxHeight * 0.55f).coerceIn(160.dp, 280.dp)
            LevelRail(
                value = light,
                icon = BaseR.drawable.light_mode_24px,
                label = stringResource(I18nR.string.au_video_brightness, (light * 100).roundToInt()),
                height = railHeight,
                modifier = Modifier.align(Alignment.CenterStart),
                fill = Color(0xFFFFE082),
                onAdjusting = onAdjusting,
                onDelta = { dy, h ->
                    val next = (light - dy / h).coerceIn(0.02f, 1f)
                    window?.let { it.attributes = it.attributes.apply { screenBrightness = next } }
                    light = next
                },
            )
            var showBoostWarning by remember { mutableStateOf(false) }
            val volumeColor = when {
                sound > 1.5f -> Color(0xFFF44336) // Red (>150%)
                sound > 1.0f -> Color(0xFFFF9800) // Orange (>100%)
                else -> Color.White // Normal
            }

            LevelRail(
                value = (sound / (if (onVolumeBoost != null) 2f else 1f)).coerceIn(0f, 1f),
                icon = if (sound == 0f) BaseR.drawable.volume_off_24px else BaseR.drawable.volume_up_24px,
                label = stringResource(I18nR.string.au_video_volume, (sound * 100).roundToInt()),
                height = railHeight,
                modifier = Modifier.align(Alignment.CenterEnd),
                fill = volumeColor,
                onAdjusting = onAdjusting,
                onDelta = { dy, h ->
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val next = (sound - dy / h * (if (onVolumeBoost != null) 2f else 1f)).coerceIn(0f, if (onVolumeBoost != null) 2f else 1f)
                    if (next > 1.0f && sound <= 1.0f) {
                        showBoostWarning = true
                    }
                    sound = next
                    onVolumeBoost?.invoke(next.coerceAtLeast(1f))
                    if (max > 0) {
                        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (next.coerceAtMost(1f) * max).roundToInt().coerceIn(0, max), 0) }
                    }
                },
            )

            if (showBoostWarning) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBoostWarning = false },
                    title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_high_volume_warning_title))  } },
                    text = { Text(stringResource(I18nR.string.hc_high_volume_warning_text)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showBoostWarning = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LevelRail(
    value: Float,
    icon: Int,
    label: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    fill: Color,
    onAdjusting: (Boolean) -> Unit,
    onDelta: (dy: Float, heightPx: Float) -> Unit,
) {
    Column(
        modifier.pointerInput(height) {
            detectVerticalDragGestures(
                onDragStart = { onAdjusting(true) },
                onDragEnd = { onAdjusting(false) },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    onDelta(dy, size.height.toFloat().coerceAtLeast(1f))
                },
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${(value * 100).roundToInt()}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(28.dp)
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Box(
                Modifier
                    .padding(5.dp)
                    .width(18.dp)
                    .height(height * value.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0.7f)))),
            )
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

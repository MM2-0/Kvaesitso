package de.mm20.launcher2.ui.media.trim

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import androidx.core.content.FileProvider
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTrimmerScreen(
    inputUri: Uri,
    isVideo: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var sliderRange by remember { mutableStateOf(0f..100f) }

    var removeAudio by rememberSaveable { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    var isProcessing by remember { mutableStateOf(false) }
    var trimmedFile by remember { mutableStateOf<File?>(null) }
    var extractedAudioFile by remember { mutableStateOf<File?>(null) }

    // Retrieve media duration
    LaunchedEffect(inputUri) {
        val dur = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, inputUri)
                val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                timeStr?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)
        }
        durationMs = dur
        startMs = 0L
        endMs = if (dur > 0L) dur else 30000L
        sliderRange = 0f..(if (dur > 0L) dur.toFloat() else 30000f)
    }

    // Player setup
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(inputUri))
            prepare()
        }
    }

    LaunchedEffect(playbackSpeed) {
        player.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val millis = ms % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", min, sec, millis)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TelosCyberHeading {
                        Text(
                            if (isVideo) "LibreCuts Video Editor" else "LibreCuts Audio Editor",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(de.mm20.launcher2.base.R.drawable.arrow_back_24px),
                            contentDescription = stringResource(I18nR.string.hc_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Player View / Audio Header
            item {
                if (isVideo) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painterResource(de.mm20.launcher2.base.R.drawable.ic_app_music_fg),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Audio Duration: ${formatTime(durationMs)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Speed Control Selector
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Playback Speed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                FilterChip(
                                    selected = playbackSpeed == speed,
                                    onClick = { playbackSpeed = speed },
                                    label = { Text("${speed}x") },
                                )
                            }
                        }
                    }
                }
            }

            // Timeline & Range Slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Start: ${formatTime(startMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "End: ${formatTime(endMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        if (sliderRange.endInclusive > sliderRange.start) {
                            RangeSlider(
                                value = startMs.toFloat()..endMs.toFloat(),
                                onValueChange = { range ->
                                    startMs = range.start.toLong()
                                    endMs = range.endInclusive.toLong()
                                    player.seekTo(startMs)
                                },
                                valueRange = sliderRange,
                            )
                        }

                        Text(
                            "Selected Clip Duration: ${formatTime((endMs - startMs).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            // Video Specific Options (Mute Audio)
            if (isVideo) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mute Audio Track", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Remove background sound from the trimmed video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = removeAudio, onCheckedChange = { removeAudio = it })
                        }
                    }
                }
            }

            // Action Buttons
            item {
                if (isProcessing) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    val outputDir = File(context.cacheDir, "trimmed_clips").apply { mkdirs() }
                                    val ext = if (isVideo) "mp4" else "m4a"
                                    val outFile = File(outputDir, "libre_cut_${System.currentTimeMillis()}.$ext")
                                    val result = MediaTrimmerEngine.trimMedia(
                                        context = context,
                                        inputUri = inputUri,
                                        startMs = startMs,
                                        endMs = endMs,
                                        outputFile = outFile,
                                        removeAudio = removeAudio,
                                    )
                                    isProcessing = false
                                    if (result != null) {
                                        trimmedFile = result
                                        Toast.makeText(context, "Clip trimmed successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to trim media", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Trim & Cut Media")
                        }

                        if (isVideo) {
                            OutlinedButton(
                                onClick = {
                                    isProcessing = true
                                    scope.launch {
                                        val outputDir = File(context.cacheDir, "trimmed_clips").apply { mkdirs() }
                                        val outFile = File(outputDir, "audio_${System.currentTimeMillis()}.m4a")
                                        val result = MediaTrimmerEngine.extractAudio(
                                            context = context,
                                            inputUri = inputUri,
                                            outputFile = outFile,
                                        )
                                        isProcessing = false
                                        if (result != null) {
                                            extractedAudioFile = result
                                            Toast.makeText(context, "Audio extracted!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to extract audio", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Extract Audio (Convert to M4A)")
                            }
                        }

                        val targetFile = trimmedFile ?: extractedAudioFile
                        targetFile?.let { clip ->
                            val isAudioClip = clip.name.endsWith(".m4a") || clip.name.endsWith(".mp3")
                            val mime = if (isAudioClip) "audio/m4a" else "video/mp4"

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val shared = withContext(Dispatchers.IO) {
                                            de.mm20.launcher2.ui.common.share.ShareActions.shareFile(
                                                context,
                                                clip,
                                                mime,
                                                clip.name,
                                            )
                                        }
                                        if (!shared) {
                                            Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Share / Post to Social Media")
                            }

                            if (isAudioClip) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val clipUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", clip)
                                            RingtoneManager.setActualDefaultRingtoneUri(
                                                context,
                                                RingtoneManager.TYPE_RINGTONE,
                                                clipUri,
                                            )
                                            Toast.makeText(context, "Set as Ringtone!", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Permission required to set Ringtone", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Set as Ringtone")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

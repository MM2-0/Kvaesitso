package de.mm20.launcher2.ui.voice

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.search.GreekFold
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.component.virtualAppPopup
import de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.RecordingCrypto
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchTopBar
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.comms.recording.RecordingQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Serializable
data object VoiceRecorderRoute : NavKey

@Serializable
data object VoiceSettingsRoute : NavKey

/** One row of the list: a recording of the voice recorder, or a call recording of Telos Phone */
private data class RecordingItem(
    val file: File,
    val title: String,
    val modified: Long,
    val durationMs: Long,
    val isCall: Boolean,
    val mime: String,
    val voice: VoiceRecording? = null,
)

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Plays one recording at a time, through the loudspeaker or the earpiece */
private class VoicePlayer {
    var current by mutableStateOf<File?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    private var player: MediaPlayer? = null

    fun play(file: File, speaker: Boolean) {
        stop()
        var p: MediaPlayer? = null
        try {
            p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(if (speaker) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(if (speaker) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.setOnCompletionListener {
                playing = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            p.start()
            player = p
            current = file
            durationMs = p.duration.toLong()
            positionMs = 0
            playing = true
        } catch (e: Exception) {
            runCatching { p?.release() }
            stop()
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            playing = false
        } else {
            p.start()
            playing = true
        }
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.toInt())
        positionMs = ms
    }

    fun refresh() {
        player?.let { positionMs = it.currentPosition.toLong() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        current = null
        playing = false
        positionMs = 0
    }
}

/**
 * Telos Voice Recorder. The layout follows the Sound Recorder of OxygenOS (big title, search,
 * menu, filter chips, the list, a red record button) and the look follows the Telos theme.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceRecorderScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val settings = remember { VoiceRecorderSettings(context) }
    val recorder by VoiceRecorderEngine.state.collectAsState()
    val player = remember { VoicePlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    // readable copies of call recordings that were played or shared earlier must not stay in the cache
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { RecordingCrypto.clearSharedCopies(context) } } }

    var tab by rememberSaveable { mutableStateOf(0) } // 0 all, 1 voice, 2 call recordings
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(settings.speaker) }
    var reload by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<RecordingItem?>(null) }
    var deleting by remember { mutableStateOf<RecordingItem?>(null) }
    var selectedForShare by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFontDialog by remember { mutableStateOf(false) }
    val shareSelecting = selectedForShare.isNotEmpty()
    BackHandler(enabled = shareSelecting) { selectedForShare = emptySet() }

    val comms: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val lockCalls by comms.lockCallRecordings.collectAsStateWithLifecycle(true)
    val recordingsUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.recordingsUnlocked.collectAsStateWithLifecycle()
    val items by produceItems(context, reload, recorder.status)
    val shown = remember(items, tab, query, lockCalls, recordingsUnlocked) {
        items.filter {
            val kind = when (tab) {
                1 -> !it.isCall
                2 -> it.isCall
                else -> true
            }
            val hideLockedCalls = it.isCall && lockCalls && !recordingsUnlocked
            kind && !hideLockedCalls && (query.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.title))
        }
    }
    LaunchedEffect(shown) {
        val available = shown.mapTo(HashSet()) { it.file.absolutePath }
        selectedForShare = selectedForShare.intersect(available)
    }
    fun shareSelected() {
        val selectedItems = shown.filter { it.file.absolutePath in selectedForShare }
        scope.launch {
            val shared = withContext(Dispatchers.IO) {
                val readableFiles = selectedItems.map { item ->
                    if (item.isCall) {
                        RecordingCrypto.readableCopy(context, item.file) ?: return@withContext false
                    } else {
                        item.file
                    }
                }
                ShareActions.shareFiles(context, readableFiles)
            }
            if (!shared) {
                Toast.makeText(context, I18nR.string.voice_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.refresh()
            delay(200)
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            player.stop()
            VoiceRecorderService.startRecording(context)
        } else {
            Toast.makeText(context, I18nR.string.voice_permission_needed, Toast.LENGTH_LONG).show()
        }
    }
    fun startRecording() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            player.stop()
            VoiceRecorderService.startRecording(context)
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }
    LaunchedEffect(recorder.error) {
        if (recorder.error) {
            Toast.makeText(context, I18nR.string.voice_start_failed, Toast.LENGTH_LONG).show()
            VoiceRecorderEngine.clearError()
        }
    }

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (recorder.status != VoiceStatus.Idle) {
            RecordingPage(
                state = recorder,
                onPauseResume = {
                    if (recorder.status == VoiceStatus.Paused) VoiceRecorderEngine.resume() else VoiceRecorderEngine.pause()
                },
                onStop = {
                    VoiceRecorderService.stopRecording(context)
                    reload++
                },
                onDiscard = {
                    VoiceRecorderEngine.cancel()
                    reload++
                },
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val scrollPage = maxHeight < (600f * density.fontScale).dp
            val sideBySide = AdaptiveLayoutPolicy.sideBySide(maxWidth.value, maxHeight.value, density.fontScale)
            val listHeight = maxOf(240.dp, maxHeight / 2)
            val neonCyan = Color(0xFF00F0FF)
            val neonMagenta = Color(0xFFFF007F)
            val neonGreen = Color(0xFF00E676)

            @Composable
            fun ListPane(modifier: Modifier = Modifier) {
                Box(modifier.then(if (scrollPage && !sideBySide) Modifier.height(listHeight) else Modifier.fillMaxSize())) {
                    de.mm20.launcher2.ui.component.AuthGate(
                        enabled = tab == 2 && lockCalls,
                        title = stringResource(I18nR.string.auth_gate_recordings),
                        unlocked = recordingsUnlocked,
                        onUnlocked = { de.mm20.launcher2.comms.privacy.PrivacySession.unlockRecordings() },
                        onCancelled = { tab = 0 },
                    ) {
                        if (shown.isEmpty() && query.isNotBlank()) {
                            SearchEmptyState(query)
                        } else if (shown.isEmpty()) {
                            EmptyState(callsOnly = tab == 2)
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(shown, key = { it.file.absolutePath }) { item ->
                                    RecordingRow(
                                        item = item,
                                        selected = player.current?.name == item.file.name,
                                        shareSelected = item.file.absolutePath in selectedForShare,
                                        shareSelecting = shareSelecting,
                                        player = player,
                                        onClick = {
                                            if (player.current?.name == item.file.name) {
                                                player.toggle()
                                            } else {
                                                val readable = if (item.isCall) RecordingCrypto.readableCopy(context, item.file) else item.file
                                                if (readable != null) player.play(readable, speaker)
                                            }
                                        },
                                        onToggleShareSelection = {
                                            val key = item.file.absolutePath
                                            selectedForShare = if (key in selectedForShare) selectedForShare - key else selectedForShare + key
                                        },
                                        onRename = { renaming = item },
                                        onShare = { scope.launch { shareRecording(context, item) } },
                                        onDelete = { deleting = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun HeaderAndFilters(modifier: Modifier = Modifier) {
                Column(modifier) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(I18nR.string.voice_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                TelosDotMatrixText(
                    text = stringResource(
                        when (tab) {
                            1 -> I18nR.string.voice_voice_recordings
                            2 -> I18nR.string.voice_call_recordings
                            else -> I18nR.string.voice_all_recordings
                        },
                    ),
                    fontSize = 10.sp,
                    color = neonCyan,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelosTechPill(
                        text = stringResource(I18nR.string.voice_all_recordings),
                        isSelected = tab == 0,
                        onClick = { tab = 0 },
                        accentColor = neonGreen,
                    )
                    TelosTechPill(
                        text = stringResource(I18nR.string.voice_voice_recordings),
                        isSelected = tab == 1,
                        onClick = { tab = 1 },
                        accentColor = neonCyan,
                        icon = painterResource(BaseR.drawable.ic_glyph_voice_recorder),
                    )
                    TelosTechPill(
                        text = stringResource(I18nR.string.voice_call_recordings),
                        isSelected = tab == 2,
                        onClick = { tab = 2 },
                        accentColor = neonMagenta,
                        icon = painterResource(BaseR.drawable.mic_24px),
                    )
                }
                }
            }

            val chrome: @Composable () -> Unit = {
                if (shareSelecting) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(I18nR.string.au_viewer_items, selectedForShare.size),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = ::shareSelected) {
                            Icon(painterResource(BaseR.drawable.share_24px), contentDescription = stringResource(I18nR.string.hc_share))
                        }
                        IconButton(onClick = { selectedForShare = emptySet() }) {
                            Icon(painterResource(BaseR.drawable.close_24px), contentDescription = stringResource(I18nR.string.hc_cancel))
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .telosTechCard(borderColor = neonMagenta, alpha = 0.35f)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RecordButton(onClick = ::startRecording)
                }
            }

            if (sideBySide) {
                Column(Modifier.fillMaxSize()) {
                    if (showFontDialog) {
                        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                            appKey = "voice",
                            appName = stringResource(I18nR.string.voice_title),
                            onDismiss = { showFontDialog = false },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (searching) {
                            TelosSearchTopBar(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = stringResource(I18nR.string.tsp_search_recordings),
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                autoFocus = true,
                            )
                            IconButton(onClick = { searching = false; query = "" }) {
                                Icon(painterResource(BaseR.drawable.close_24px), contentDescription = stringResource(I18nR.string.close))
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { searching = true }) {
                                Icon(painterResource(BaseR.drawable.search_24px), contentDescription = stringResource(I18nR.string.voice_search))
                            }
                            IconButton(onClick = { showFontDialog = true }) {
                                Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                            }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.voice_more))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = virtualAppPopup()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (speaker) I18nR.string.voice_receiver_mode else I18nR.string.voice_speaker_mode)) },
                                        onClick = {
                                            menu = false
                                            speaker = !speaker
                                            settings.speaker = speaker
                                            player.stop()
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(I18nR.string.voice_settings)) },
                                        onClick = { menu = false; backStack.add(VoiceSettingsRoute) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(0.38f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                            HeaderAndFilters()
                            chrome()
                        }
                        ListPane(Modifier.weight(0.62f).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().then(if (scrollPage) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                if (showFontDialog) {
                    de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                        appKey = "voice",
                        appName = stringResource(I18nR.string.voice_title),
                        onDismiss = { showFontDialog = false },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        TelosSearchTopBar(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(I18nR.string.tsp_search_recordings),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            autoFocus = true,
                        )
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(painterResource(BaseR.drawable.close_24px), contentDescription = stringResource(I18nR.string.close))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(BaseR.drawable.search_24px), contentDescription = stringResource(I18nR.string.voice_search))
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.voice_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = virtualAppPopup()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (speaker) I18nR.string.voice_receiver_mode else I18nR.string.voice_speaker_mode)) },
                                    onClick = {
                                        menu = false
                                        speaker = !speaker
                                        settings.speaker = speaker
                                        player.stop()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(I18nR.string.voice_settings)) },
                                    onClick = { menu = false; backStack.add(VoiceSettingsRoute) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
                    HeaderAndFilters()
                    ListPane(Modifier.then(if (scrollPage) Modifier else Modifier.weight(1f)).fillMaxWidth())
                    chrome()
                }
            }
            }
        }
    }

    renaming?.let { item ->
        var name by remember(item) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.voice_rename))  } },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val voice = item.voice
                    if (voice != null && VoiceRecorderEngine.rename(voice, name) != null) {
                        player.stop()
                        reload++
                    } else {
                        Toast.makeText(context, I18nR.string.voice_rename_failed, Toast.LENGTH_SHORT).show()
                    }
                    renaming = null
                }) { Text(stringResource(I18nR.string.voice_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(I18nR.string.voice_cancel)) } },
        )
    }
    deleting?.let { item ->
        de.mm20.launcher2.ui.trash.UriDeleteDialog(android.net.Uri.fromFile(item.file),
            onDeleted = { player.stop(); reload++ }, onDismiss = { deleting = null })
    }

    }
}

@Composable
private fun produceItems(context: Context, reload: Int, status: VoiceStatus) =
    androidx.compose.runtime.produceState(emptyList<RecordingItem>(), reload, status) {
        value = withContext(Dispatchers.IO) {
            val own = VoiceRecorderEngine.list(context).map {
                RecordingItem(it.file, it.name, it.modified, it.durationMs, false, it.format.mime, it)
            }
            val calls = runCatching { CallAudioRecorder.list(context) }.getOrDefault(emptyList()).map {
                RecordingItem(it.file, it.number, it.file.lastModified(), 0L, true, "audio/mp4")
            }
            (own + calls).sortedByDescending { it.modified }
        }
    }

@Composable
private fun EmptyState(callsOnly: Boolean = false) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // a microphone with a waveform, drawn here so that no picture is needed
        Box(Modifier.size(width = 180.dp, height = 120.dp), contentAlignment = Alignment.Center) {
            val bars = listOf(0.25f, 0.5f, 0.35f, 0.7f, 1f, 0.7f, 0.45f, 0.3f, 0.2f)
            val color = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            Canvas(Modifier.fillMaxSize()) {
                val barWidth = 6.dp.toPx()
                val gap = 4.dp.toPx()
                val total = bars.size * barWidth + (bars.size - 1) * gap
                var x = (size.width - total) / 2
                for (b in bars) {
                    val h = size.height * b
                    drawRoundRect(color, Offset(x, (size.height - h) / 2), Size(barWidth, h), CornerRadius(barWidth / 2))
                    x += barWidth + gap
                }
            }
            Icon(
                painterResource(BaseR.drawable.ic_glyph_voice_recorder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            stringResource(if (callsOnly) I18nR.string.voice_no_call_recordings else I18nR.string.voice_no_recordings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit) {
    val red = Color(0xFFE53935)
    Box(
        Modifier
            .size(72.dp)
            .shadow(20.dp, CircleShape, ambientColor = red, spotColor = red)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), red)))
            .clickable(onClickLabel = stringResource(I18nR.string.au_capture_record), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RecordingRow(
    item: RecordingItem,
    selected: Boolean,
    shareSelected: Boolean,
    shareSelecting: Boolean,
    player: VoicePlayer,
    onClick: () -> Unit,
    onToggleShareSelection: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (shareSelecting) onToggleShareSelection() else onClick() },
                onLongClick = onToggleShareSelection,
            )
            .semantics { selectedSemantics = shareSelected }
            .background(
                when {
                    shareSelected -> MaterialTheme.colorScheme.secondaryContainer
                    selected -> MaterialTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (shareSelecting) {
                Checkbox(checked = shareSelected, onCheckedChange = { onToggleShareSelection() })
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val duration = if (item.durationMs > 0) formatDuration(item.durationMs) + " · " else ""
                Text(
                    duration + format.format(Date(item.modified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                IconButton(onClick = onClick) {
                    Icon(
                        painterResource(if (player.playing) BaseR.drawable.ic_voice_pause else BaseR.drawable.ic_voice_play),
                        contentDescription = stringResource(if (player.playing) I18nR.string.voice_pause else I18nR.string.voice_play),
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.voice_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = virtualAppPopup()) {
                    if (!item.isCall) DropdownMenuItem(text = { Text(stringResource(I18nR.string.voice_rename)) }, onClick = { menu = false; onRename() }, modifier = Modifier.heightIn(min = 48.dp))
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.voice_share)) }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.voice_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(player.positionMs), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = player.positionMs.toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..max(1f, player.durationMs.toFloat()),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(player.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private suspend fun shareRecording(context: Context, item: RecordingItem) {
    val shared = withContext(Dispatchers.IO) {
        val readable = if (item.isCall) RecordingCrypto.readableCopy(context, item.file) ?: return@withContext false else item.file
        ShareActions.shareFile(context, readable, item.mime, item.file.name)
    }
    if (!shared) {
        Toast.makeText(context, I18nR.string.voice_share_failed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RecordingPage(state: VoiceRecorderState, onPauseResume: () -> Unit, onStop: () -> Unit, onDiscard: () -> Unit) {
    val paused = state.status == VoiceStatus.Paused
    val neonMagenta = Color(0xFFFF007F)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        Column(
            Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (!compact) Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (paused) I18nR.string.voice_notification_paused else I18nR.string.voice_notification_recording),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TelosDotMatrixText(
            formatDuration(state.elapsedMs),
            fontSize = 14.sp,
            color = neonMagenta,
            modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
        )
        Text(formatDuration(state.elapsedMs), fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), maxLines = 1, softWrap = false)
        Waveform(state.amplitudes, Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 120.dp))
        Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(56.dp).heightIn(min = 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(painterResource(BaseR.drawable.delete_24px), contentDescription = stringResource(I18nR.string.voice_discard))
            }
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFF7A6E), Color(0xFFE53935))))
                    .clickable(onClickLabel = stringResource(I18nR.string.voice_stop), onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
            }
            IconButton(onClick = onPauseResume, modifier = Modifier.size(56.dp).heightIn(min = 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(
                    painterResource(if (paused) BaseR.drawable.ic_voice_play else BaseR.drawable.ic_voice_pause),
                    contentDescription = stringResource(if (paused) I18nR.string.voice_resume else I18nR.string.voice_pause),
                )
            }
        }
        }
    }
}

@Composable
private fun Waveform(amplitudes: List<Int>, modifier: Modifier) {
    val cyan = Color(0xFF00F2FE)
    val pink = Color(0xFFFF007F)
    Canvas(modifier) {
        val barWidth = 5.dp.toPx()
        val gap = 3.dp.toPx()
        val count = (size.width / (barWidth + gap)).toInt().coerceAtLeast(1)
        val visible = amplitudes.takeLast(count)
        var x = size.width - visible.size * (barWidth + gap)
        for (amp in visible) {
            val h = (amp / 32768f).coerceIn(0.05f, 1f) * size.height
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(pink, cyan)),
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
            x += barWidth + gap
        }
    }
}

/** The settings of Telos Voice Recorder, in the layout of the settings of the OxygenOS Sound Recorder */
@Composable
fun VoiceSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { VoiceRecorderSettings(context) }
    var format by remember { mutableStateOf(settings.format) }
    var mode by remember { mutableStateOf(settings.mode) }
    var quality by remember { mutableStateOf(settings.quality) }
    var speaker by remember { mutableStateOf(settings.speaker) }
    var hideFromMedia by remember { mutableStateOf(settings.hideFromMedia) }
    val comms: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val lockCalls by comms.lockCallRecordings.collectAsStateWithLifecycle(true)
    val hideCallFromMedia by comms.hideCallRecordingsFromMedia.collectAsStateWithLifecycle(false)

    PreferenceScreen(title = stringResource(I18nR.string.voice_settings)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.voice_settings_sound)) {
                ListPreference(
                    title = stringResource(I18nR.string.voice_format),
                    items = VoiceFormat.entries.filter { it.available }.map {
                        ListPreferenceItem(if (it == VoiceFormat.Aac) "AAC (.m4a)" else "Opus (.ogg)", it)
                    },
                    value = format,
                    onValueChanged = { format = it; settings.format = it },
                )
                ListPreference(
                    title = stringResource(I18nR.string.voice_mode),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.voice_mode_standard), VoiceMode.Standard),
                        ListPreferenceItem(stringResource(I18nR.string.voice_mode_voice), VoiceMode.Voice),
                    ),
                    value = mode,
                    onValueChanged = { mode = it; settings.mode = it },
                )
                ListPreference(
                    title = stringResource(I18nR.string.voice_quality),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.voice_quality_compact), RecordingQuality.COMPACT),
                        ListPreferenceItem(stringResource(I18nR.string.voice_quality_balanced), RecordingQuality.BALANCED),
                        ListPreferenceItem(stringResource(I18nR.string.voice_quality_high), RecordingQuality.HIGH),
                    ),
                    value = quality,
                    onValueChanged = { quality = it; settings.quality = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.voice_settings_playback)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.voice_speaker),
                    summary = stringResource(I18nR.string.voice_speaker_summary),
                    value = speaker,
                    onValueChanged = { speaker = it; settings.speaker = it },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.voice_settings_privacy)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.lock_call_recordings),
                    summary = stringResource(I18nR.string.lock_call_recordings_summary),
                    value = lockCalls,
                    onValueChanged = {
                        comms.setLockCallRecordings(it)
                        if (!it) de.mm20.launcher2.comms.privacy.PrivacySession.unlockRecordings()
                        else de.mm20.launcher2.comms.privacy.PrivacySession.lockRecordings()
                    },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.voice_hide_from_media),
                    summary = stringResource(I18nR.string.voice_hide_from_media_summary),
                    value = hideFromMedia,
                    onValueChanged = {
                        hideFromMedia = it
                        settings.hideFromMedia = it
                        settings.applyNomedia(context)
                    },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.call_recordings_hide_from_media),
                    summary = stringResource(I18nR.string.call_recordings_hide_from_media_summary),
                    value = hideCallFromMedia,
                    onValueChanged = {
                        comms.setHideCallRecordingsFromMedia(it)
                        de.mm20.launcher2.comms.recording.CallAudioRecorder.applyNomedia(context, it)
                    },
                )
                Text(
                    stringResource(I18nR.string.voice_privacy_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

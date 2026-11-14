package de.mm20.launcher2.ui.media.akou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.comms.akou.AkouHistory
import de.mm20.launcher2.comms.akou.AkouRecognition
import de.mm20.launcher2.comms.akou.RecognizedSong
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.youtube.telosYouTubeIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data object AkouRoute : NavKey

/** Explicit foreground listening; only a fingerprint is sent for matching. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkouScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val history = remember { AkouHistory(context.applicationContext) }
    var entries by remember { mutableStateOf<List<RecognizedSong>>(emptyList()) }
    LaunchedEffect(history) { entries = withContext(Dispatchers.IO) { history.read() } }
    val prefs = remember {
        context.applicationContext.getSharedPreferences(
            "akou_ui",
            android.content.Context.MODE_PRIVATE,
        )
    }
    var autoListen by remember { mutableStateOf(false) }
    var preferencesLoaded by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(false) }
    var autoAttempted by rememberSaveable { mutableStateOf(false) }
    var songTarget by remember { mutableStateOf<RecognizedSong?>(null) }
    var officialMusicAvailable by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(true) }
    var showAbout by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) {
        val saved =
            withContext(Dispatchers.IO) {
                prefs.getBoolean("show_disclosure", true) to prefs.getBoolean("auto_listen", false)
            }
        showDisclosure = saved.first
        autoListen = saved.second
        preferencesLoaded = true
    }
    var phase by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun start() {
        if (phase != 0) return
        job = scope.launch {
            status = 0
            phase = 1
            var recording: ShortArray? = null
            try {
                recording = AkouRecognition.record()
                phase = 2
                val result = AkouRecognition.recognize(recording)
                if (result == null) status = I18nR.string.akou_no_match
                else {
                    entries = withContext(Dispatchers.IO) { history.add(result) }
                    status = I18nR.string.akou_matched
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                status = I18nR.string.akou_microphone_denied
            } catch (_: Exception) {
                status = I18nR.string.akou_failed
            } finally {
                recording?.fill(0)
                phase = 0
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) start() else status = I18nR.string.akou_microphone_denied
        }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        foreground = true
        scope.launch {
            officialMusicAvailable =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.packageManager
                                .getApplicationInfo("com.google.android.apps.youtube.music", 0)
                                .enabled
                        }
                        .getOrDefault(false)
                }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        foreground = false
        job?.cancel()
        if (context.akouActivity()?.isChangingConfigurations != true) autoAttempted = false
    }
    AkouAutoListenEffect(
        enabled = autoListen,
        ready = preferencesLoaded,
        foreground = foreground,
        attempted = autoAttempted,
        canRecord = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        },
        onAttempt = { autoAttempted = true },
        onListen = {
            if (job?.isActive != true) {
                job?.join()
                if (foreground) start()
            }
        },
        onPermissionDenied = { status = I18nR.string.akou_microphone_denied },
    )
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(I18nR.string.telos_akou_app_label)) },
                navigationIcon = {
                    if (backStack.size > 1) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(
                                painterResource(BaseR.drawable.arrow_back_24px),
                                stringResource(I18nR.string.hc_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(
                            painterResource(BaseR.drawable.more_vert_24px),
                            stringResource(I18nR.string.hc_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (showDisclosure) {
                    Text(
                        stringResource(I18nR.string.akou_provider_disclosure),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = {
                            showDisclosure = false
                            prefs.edit().putBoolean("show_disclosure", false).apply()
                        }
                    ) {
                        Text(stringResource(I18nR.string.app_polish_dont_show))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (phase != 0) job?.cancel()
                        else if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                            start()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Text(
                        stringResource(
                            if (phase == 0) I18nR.string.akou_start else I18nR.string.hc_cancel
                        )
                    )
                }
                if (phase != 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(
                            if (phase == 1) I18nR.string.akou_recording
                            else I18nR.string.akou_identifying
                        )
                    )
                }
                if (status != 0) Text(stringResource(status))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(I18nR.string.akou_history),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { history.clear() }
                                entries = emptyList()
                            }
                        },
                    ) {
                        Text(stringResource(I18nR.string.hc_delete))
                    }
                }
            }
            items(entries, key = { "${it.recognizedAt}:${it.title}:${it.artist}" }) { song ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium)
                        Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                        if (song.album.isNotBlank())
                            Text(song.album, style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                val uri =
                                    Uri.parse(
                                        "https://music.youtube.com/search?q=" +
                                            Uri.encode("${song.artist} ${song.title}")
                                    )
                                if (officialMusicAvailable) songTarget = song
                                else
                                    context.startActivity(
                                        telosYouTubeIntent(context, uri.toString(), true)
                                    )
                            }
                        ) {
                            Text(stringResource(I18nR.string.akou_youtube_music))
                        }
                    }
                }
            }
        }
    }
    songTarget?.let { song ->
        val url = "https://music.youtube.com/search?q=" + Uri.encode("${song.artist} ${song.title}")
        AlertDialog(
            onDismissRequest = { songTarget = null },
            title = { Text(stringResource(I18nR.string.akou_youtube_music)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            songTarget = null
                            context.startActivity(telosYouTubeIntent(context, url, true))
                        }
                    ) {
                        Text(stringResource(I18nR.string.telos_youtube_music_handler_label))
                    }
                    if (officialMusicAvailable)
                        TextButton(
                            onClick = {
                                songTarget = null
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .setPackage("com.google.android.apps.youtube.music")
                                    )
                                } catch (_: android.content.ActivityNotFoundException) {
                                    context.startActivity(telosYouTubeIntent(context, url, true))
                                }
                            }
                        ) {
                            Text(stringResource(I18nR.string.telos_youtube_music_label))
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { songTarget = null }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
        )
    }
    if (showAbout)
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(I18nR.string.telos_akou_app_label)) },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(I18nR.string.akou_provider_disclosure))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = autoListen,
                            onCheckedChange = {
                                autoListen = it
                                prefs.edit().putBoolean("auto_listen", it).apply()
                            },
                        )
                        Text(
                            stringResource(I18nR.string.akou_auto_listen),
                            Modifier.weight(1f).padding(start = 12.dp),
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = showDisclosure,
                            onCheckedChange = {
                                showDisclosure = it
                                prefs.edit().putBoolean("show_disclosure", it).apply()
                            },
                        )
                        Text(stringResource(I18nR.string.app_polish_show_info), Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
}

private fun android.content.Context.akouActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.akouActivity()
        else -> null
    }

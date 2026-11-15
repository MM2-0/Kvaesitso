package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoServices
import de.mm20.launcher2.comms.media.video.VideoPrefs
import de.mm20.launcher2.comms.media.video.SubtitleFiles
import de.mm20.launcher2.comms.media.video.SubtitleService
import de.mm20.launcher2.comms.media.video.SubtitleResult
import de.mm20.launcher2.comms.media.video.torrent.TorrentState
import de.mm20.launcher2.comms.media.video.torrent.TorrentStreamer
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays local videos, web streams, and torrents. A torrent is opened first (finding peers, reading
 * the file list), then its video files play through the local stream address.
 */
/** A subtitle file that comes with the video (from a Stremio add-on, for example) */
data class ExternalSubtitle(val url: String, val language: String, val label: String)

@Composable
fun VideoPlayerScreen(
    externalSubtitles: List<ExternalSubtitle> = emptyList(),
    torrentFileIdx: Int? = null,
    uris: List<Uri>,
    titles: List<String>,
    startIndex: Int,
    torrentSource: String?,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    inPictureInPicture: Boolean,
) {
    val context = LocalContext.current
    var resolved by remember {
        mutableStateOf<ResolvedMedia?>(
            if (torrentSource == null) ResolvedMedia(uris, titles, uris.map { it.toString() }, startIndex) else null
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val torrent by TorrentStreamer.state.collectAsStateWithLifecycle()

    if (torrentSource != null) {
        LaunchedEffect(torrentSource) {
            val config = VideoServices.config()
            runCatching { TorrentStreamer.open(context, torrentSource, config.torrentWifiOnly, context.findActivity(), torrentFileIdx) }
                .onSuccess { opened ->
                    if (context.findActivity()?.intent?.getBooleanExtra("stremio_auto_continuation", false) == true &&
                        opened.videos.none { it.index == torrentFileIdx }) {
                        error = context.getString(I18nR.string.au_video_torrent_failed)
                        context.findActivity()?.let { TorrentStreamer.release(it) }
                        return@onSuccess
                    }
                    val name = TorrentStreamer.torrentName()
                    val selectedOnly = context.findActivity()?.intent?.getBooleanExtra("stremio_playback", false) == true
                    val videos = if (selectedOnly) listOf(opened.videos[opened.startPosition]) else opened.videos
                    resolved = ResolvedMedia(
                        uris = videos.map { Uri.parse(it.url) },
                        titles = videos.map { it.name.substringBeforeLast('.') },
                        mediaIds = videos.map { "torrent:$name/${it.name}" },
                        startIndex = if (selectedOnly) 0 else opened.startPosition,
                    )
                }
                .onFailure {
                    error = if (it is de.mm20.launcher2.comms.media.video.torrent.TorrentRouteNotReadyException) {
                        context.getString(I18nR.string.au6_wgvideo_route_not_ready)
                    } else it.message ?: context.getString(I18nR.string.au_video_torrent_failed)
                }
        }
        LaunchedEffect(torrentSource) {
            while (true) {
                delay(1000)
                TorrentStreamer.refreshStatus()
            }
        }
    }

    val r = resolved
    if (r == null) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                if (error == null) CircularProgressIndicator(color = Color.White)
                Text(
                    text = error ?: stringResource(
                        if (torrent.stage == TorrentState.Stage.FindingPeers) I18nR.string.au_video_finding_peers else I18nR.string.au_video_opening
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (error == null && torrent.stage == TorrentState.Stage.FindingPeers) {
                    TorrentConnectionCard(torrent)
                    Text(stringResource(I18nR.string.hc_this_can_take_a_minute), color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(if (error != null) I18nR.string.hc_close else I18nR.string.hc_cancel), color = Color.White)
                }
            }
        }
        return
    }
    PlayerContent(r, torrentSource != null, torrent, onClose, onPlayingChanged, inPictureInPicture, externalSubtitles)
}

@Composable
private fun TorrentConnectionCard(torrent: TorrentState, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xCC17202B), androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(I18nR.string.telos_torrent_connecting), color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(I18nR.string.telos_torrent_peers, torrent.peers, torrent.seeds), color = Color.White)
        Text(stringResource(I18nR.string.telos_torrent_speed, torrent.downloadBytesPerSecond / 1024), color = Color.White)
        Text(stringResource(I18nR.string.telos_torrent_trackers, torrent.workingTrackers, torrent.trackers, torrent.failedTrackers), color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(I18nR.string.telos_torrent_dht, torrent.dhtNodes, torrent.elapsedSeconds), color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
    }
}

private data class ResolvedMedia(
    val uris: List<Uri>,
    val titles: List<String>,
    val mediaIds: List<String>,
    val startIndex: Int,
)

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    media: ResolvedMedia,
    isTorrent: Boolean,
    torrent: TorrentState,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    inPictureInPicture: Boolean,
    externalSubtitles: List<ExternalSubtitle> = emptyList(),
) {
    val context = LocalContext.current
    val uris = media.uris
    val titles = media.titles
    val startIndex = media.startIndex

    // music and radio pause while the video plays; the main process decides about resuming them
    fun videoState(state: String) {
        de.mm20.launcher2.comms.media.video.PlayerBridge.send(
            context.applicationContext,
            de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_VIDEO,
        ) { putString("state", state) }
    }

    val networkGate = remember { de.mm20.launcher2.comms.network.TransferNetworkGate.get(context, "torrent") }
    val managedNetwork = isTorrent || uris.any { it.scheme in setOf("http", "https") && it.host !in setOf("127.0.0.1", "localhost", "::1", "[::1]") }
    val stremioIntent = remember(context) { context.findActivity()?.intent?.let { android.content.Intent(it) } }
    val stremio = stremioIntent?.getBooleanExtra("stremio_playback", false) == true
    val playbackRuntime by produceState<Pair<de.mm20.launcher2.comms.stremio.StremioPlaybackSettings, androidx.media3.datasource.DataSource.Factory>?>(null, stremio) {
        if (stremio) value = withContext(Dispatchers.IO) {
            val settings = de.mm20.launcher2.comms.stremio.StremioPlaybackSettings.load(context)
            val factory = runCatching<androidx.media3.datasource.DataSource.Factory> {
                if (stremioIntent?.getBooleanExtra("stremio_shared_server", false) == true) return@runCatching RemoteRoutingDataSource.factory(context)
                val cache = de.mm20.launcher2.ui.media.stremio.StremioStreamCache.obtain(context, settings.cacheBytes)
                androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cache).setUpstreamDataSourceFactory(RemoteRoutingDataSource.factory(context))
                    .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            }.getOrElse { RemoteRoutingDataSource.factory(context) }
            settings to factory
        }
    }
    if (stremio && playbackRuntime == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    val player = remember {
        videoState("started")
        val renderers = de.mm20.launcher2.comms.media.MediaDecoderPack.renderersFactory(context)
        ExoPlayer.Builder(context, renderers).setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            // addresses of network storages (rem://) are read through Telos Files
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(playbackRuntime?.second ?: RemoteRoutingDataSource.factory(context)))
            // audio focus (other apps pause or duck) and pausing when the headphones are unplugged
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            playbackRuntime?.first?.let { settings ->
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages(*settings.audioLanguages.toTypedArray())
                    .setPreferredTextLanguages(*settings.subtitleLanguages.toTypedArray())
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, settings.subtitleLanguages.isEmpty())
                    .build()
            }
            setVideoChangeFrameRateStrategy(
                if (VideoPrefs.matchFrameRate(context)) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            )
            val items = uris.mapIndexed { index, uri ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(media.mediaIds[index])
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(index)).build())
                    .apply {
                        // subtitle files that came with the video belong to the one that starts
                        if (index == startIndex && externalSubtitles.isNotEmpty()) {
                            setSubtitleConfigurations(externalSubtitles.map { s ->
                                val lower = s.url.substringBefore('?').lowercase()
                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                                    .setMimeType(
                                        when {
                                            lower.endsWith(".vtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
                                            lower.endsWith(".ass") || lower.endsWith(".ssa") -> androidx.media3.common.MimeTypes.TEXT_SSA
                                            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                                        }
                                    )
                                    .setLanguage(s.language.ifBlank { null })
                                    .setLabel(s.label.ifBlank { s.language.ifBlank { null } })
                                    .build()
                            })
                        }
                    }
                    .build()
            }
            val resumeKey = Uri.parse(media.mediaIds[startIndex])
            val progress = ResumeStore.progress(context, resumeKey)
            val resumeAt = if (stremio) stremioIntent?.getLongExtra("stremio_resume_ms", 0L)?.coerceAtLeast(0L) ?: 0L
                else if (progress in 0.02f..0.95f) ResumeStore.position(context, resumeKey) else 0L
            setMediaItems(items, startIndex, resumeAt)
            if (!managedNetwork || networkGate.allowed.value) prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player, managedNetwork) {
        if (!managedNetwork) return@LaunchedEffect
        var blocked = false
        var resumePlayback = false
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { networkGate.refresh() }
            if (!networkGate.allowed.value && !blocked) {
                resumePlayback = player.playWhenReady
                player.stop()
                blocked = true
                android.widget.Toast.makeText(context, I18nR.string.netfw_blocked, android.widget.Toast.LENGTH_LONG).show()
            } else if (networkGate.allowed.value && blocked) {
                resumePlayback = player.playWhenReady
                player.prepare()
                player.playWhenReady = resumePlayback
                blocked = false
            }
            delay(500)
        }
    }

    // web streams (not local files, not the torrent's local address) reconnect by themselves when the connection is lost
    val reconnector = remember(player) {
        if (!isTorrent && uris.any { it.scheme == "http" || it.scheme == "https" }) {
            de.mm20.launcher2.comms.media.StreamReconnector(context.applicationContext, player).also { it.attach() }
        } else null
    }
    val reconnecting by (reconnector?.reconnecting ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val waitingForNetwork by (reconnector?.waiting ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()

    // Trakt.tv: report what is played (only when the user signed in and switched it on)
    val traktScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    // last Trakt action and the item it was about, so a stop is not sent twice
    val traktState = remember { arrayOfNulls<Any>(2) }
    fun scrobble(action: String, finished: Boolean = false, forItem: MediaItem? = null) {
        val item = forItem ?: player.currentMediaItem ?: return
        if (action == "stop" && traktState[0] == "stop" && traktState[1] == item.mediaId) return
        val duration = player.duration
        if (duration <= 0 && !finished) return
        val progress = if (finished || duration <= 0) 100f else (player.currentPosition * 100f / duration).coerceIn(0f, 100f)
        traktState[0] = action
        traktState[1] = item.mediaId
        if (stremio && stremioIntent != null) {
            de.mm20.launcher2.comms.media.video.PlayerBridge.send(context.applicationContext,
                de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_SCROBBLE) {
                putString("action", action); putFloat("progress", progress)
                putString("id", stremioIntent.getStringExtra("stremio_meta_id"))
                putString("trakt_session", stremioIntent.getStringExtra("trakt_session"))
                putInt("season", stremioIntent.getIntExtra("trakt_season", -1))
                putInt("episode", stremioIntent.getIntExtra("trakt_episode", -1))
                putBoolean("series", stremioIntent.getStringExtra("stremio_type") == "series")
                putLong("event_time", System.currentTimeMillis())
            }
            return
        }
        val name = item.mediaMetadata.title?.toString().orEmpty()
        val parsedName = cleanTitle(name) + ".x"
        val appContext = context.applicationContext
        if (de.mm20.launcher2.base.ProcessInfo.isolatedPlayer) {
            // the Trakt login lives in the main process
            de.mm20.launcher2.comms.media.video.PlayerBridge.send(appContext, de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_SCROBBLE) {
                putString("action", action); putString("name", parsedName); putFloat("progress", progress)
            }
            return
        }
        val parsed = EpisodeParser.parse(parsedName)
        traktScope.launch { runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.scrobble(appContext, action, parsed, progress) } }
    }

    // the item that was saved last: when the player moves on by itself, this one has been played to the end
    var lastSavedId by remember { mutableStateOf<String?>(null) }
    var lastSavedDuration by remember { mutableStateOf(0L) }

    var watchSampleAt by remember(player) { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    var watchWasPlaying by remember(player) { mutableStateOf(false) }
    var watchedTimeMs by remember(player) { mutableStateOf(stremioIntent?.getLongExtra("stremio_watched_ms", 0L)?.coerceAtLeast(0L) ?: 0L) }

    fun saveProgress(force: Boolean = true, ended: Boolean = false) {
        val item = player.currentMediaItem ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (watchWasPlaying) watchedTimeMs += ((now - watchSampleAt).coerceIn(0, 15_000) * player.playbackParameters.speed).toLong()
        watchSampleAt = now
        watchWasPlaying = player.isPlaying
        val duration = player.duration
        if (duration > 0) {
            val position = player.currentPosition.coerceIn(0L, duration)
            ResumeStore.save(context, Uri.parse(item.mediaId), position, duration)
            lastSavedId = item.mediaId
            lastSavedDuration = duration
            if (stremio && stremioIntent != null) {
                de.mm20.launcher2.comms.media.video.PlayerBridge.send(context.applicationContext,
                    de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_STREMIO_PROGRESS) {
                    putString("session", stremioIntent.getStringExtra("stremio_session"))
                    putString("meta_id", stremioIntent.getStringExtra("stremio_meta_id"))
                    putString("video_id", stremioIntent.getStringExtra("stremio_video_id"))
                    putString("next_video_id", stremioIntent.getStringExtra("stremio_next_video_id"))
                    putString("type", stremioIntent.getStringExtra("stremio_type"))
                    putString("name", stremioIntent.getStringExtra("stremio_meta_name"))
                    putString("poster", stremioIntent.getStringExtra("stremio_meta_poster"))
                    putString("video_title", stremioIntent.getStringExtra("stremio_video_title"))
                    putString("stream", stremioIntent.getStringExtra("stremio_stream_key"))
                    putStringArrayList("video_ids", stremioIntent.getStringArrayListExtra("stremio_episode_order"))
                    putLong("position", position); putLong("duration", duration); putLong("time_watched", watchedTimeMs)
                    putLong("watched_at", System.currentTimeMillis())
                    putBoolean("completed", ended || position.toDouble() > duration * de.mm20.launcher2.comms.stremio.StremioProgressState.CREDITS_THRESHOLD)
                    putBoolean("force", force)
                }
            }
        }
    }

    LaunchedEffect(player) {
        while (true) { delay(5_000); if (player.isPlaying) saveProgress(force = false) }
    }

    // notification with play / pause / previous / next (also on the lock screen and Bluetooth)
    DisposableEffect(player) {
        VideoSession.attach(context, player)
        onDispose { VideoSession.detach(context, player) }
    }

    var percentResumeApplied by remember(player) { mutableStateOf(false) }
    val continuationScope = rememberCoroutineScope()
    var continuationRequested by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveProgress(force = !isPlaying)
                onPlayingChanged(isPlaying)
                if (isPlaying) videoState("started")
                // the pause while the connection is restored is not a pause by the user
                if (isPlaying || reconnector?.reconnecting?.value != true) scrobble(if (isPlaying) "start" else "pause")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !percentResumeApplied) {
                    percentResumeApplied = true
                    val percent = stremioIntent?.getDoubleExtra("stremio_resume_percent", Double.NaN) ?: Double.NaN
                    if (stremio && percent.isFinite() && percent in 0.0..<100.0 && player.duration > 0) {
                        player.seekTo((player.duration * percent / 100).toLong())
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress(ended = true)
                    scrobble("stop", finished = true)
                    if (stremio && playbackRuntime?.first?.autoNextEpisode == true && !continuationRequested && networkGate.allowed.value) {
                        continuationRequested = true
                        continuationScope.launch {
                            val activity = context.findActivity() ?: return@launch
                            val next = runCatching {
                                de.mm20.launcher2.ui.media.stremio.StremioEpisodeContinuation.next(context.applicationContext, android.content.Intent(activity.intent))
                            }.getOrNull()
                            if (next != null && !activity.isFinishing && !activity.isDestroyed) {
                                activity.intent = next
                                activity.recreate()
                            }
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Trakt: the episode that ended is done, the next one starts
                    traktState[1]?.let { id -> if (id != mediaItem?.mediaId) {
                        val prev = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }.firstOrNull { it.mediaId == id }
                        if (prev != null) scrobble("stop", finished = true, forItem = prev)
                    } }
                    scrobble("start")
                    // player.currentMediaItem is the next one now: the episode that ended counts as watched
                    lastSavedId?.let { id -> if (id != mediaItem?.mediaId) ResumeStore.save(context, Uri.parse(id), lastSavedDuration, lastSavedDuration) }
                    saveProgress()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            scrobble("stop")
            player.removeListener(listener)
            reconnector?.release()
            de.mm20.launcher2.comms.media.MediaAudioFx.detachFromSession(player.audioSessionId)
            player.release()
            videoState("stopped")
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { saveProgress() }
    // VideoPlaybackService owns the media session while the activity is stopped/locked.
    // Explicit close and composition disposal still release the player.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { saveProgress() }

    var playbackError by remember { mutableStateOf(false) }
    var playbackStage by remember(player) { mutableStateOf(player.playbackState) }
    DisposableEffect(player) {
        val errorListener = object : Player.Listener {
            private var effectSession = -1
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                de.mm20.launcher2.comms.media.MediaAudioFx.detachFromSession(effectSession)
                effectSession = audioSessionId
                de.mm20.launcher2.comms.media.MediaAudioFx.attachToSession(audioSessionId)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStage = playbackState
                if (playbackState == Player.STATE_READY) playbackError = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) playbackError = false
            }
        }
        player.addListener(errorListener)
        onDispose { player.removeListener(errorListener) }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun attachSubtitle(subtitle: Uri, fileName: String, label: String? = null) {
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        val name = fileName.lowercase()
        val mime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            name.endsWith(".ttml") || name.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val withSubtitle = current.buildUpon()
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitle)
                        .setMimeType(mime)
                        .setLabel(label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        val position = player.currentPosition
        player.replaceMediaItem(index, withSubtitle)
        player.seekTo(index, position)
    }

    val scope = rememberCoroutineScope()
    var subFile by remember { mutableStateOf<java.io.File?>(null) }
    var subLabel by remember { mutableStateOf<String?>(null) }
    var subDelay by remember { mutableStateOf(0L) }
    var subStyle by remember { mutableStateOf(VideoPrefs.subtitleStyle(context)) }
    var matchFps by remember { mutableStateOf(VideoPrefs.matchFrameRate(context)) }

    /** Loads a subtitle file into the player; the delay starts at zero */
    fun loadSubtitleFile(file: java.io.File, label: String?) {
        subFile = file
        subLabel = label
        subDelay = 0L
        attachSubtitle(Uri.fromFile(file), file.name, label)
    }

    fun changeDelay(ms: Long) {
        val file = subFile ?: return
        subDelay = ms
        scope.launch {
            val shifted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SubtitleFiles.shifted(context, file, ms) }
            attachSubtitle(Uri.fromFile(shifted), shifted.name, subLabel)
        }
    }

    var currentFileName by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    var currentVideoKey by remember { mutableStateOf<String?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { subtitle ->
        if (subtitle != null) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val name = context.contentResolver.query(subtitle, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
                        // a subtitle file is small: a huge file picked by mistake must not fill the memory
                        val bytes = context.contentResolver.openInputStream(subtitle)!!.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                if (out.size() > 6_000_000) error("The subtitle file is too big")
                            }
                            out.toByteArray()
                        }
                        val lang = VideoServices.config().languages.substringBefore(',')
                        SubtitleFiles.import(context, name, bytes, lang, currentVideoKey)
                    }
                }.onSuccess { loadSubtitleFile(it, null) }
                    .onFailure { toast(context, context.getString(I18nR.string.au_video_subtitle_failed)) }
            }
        }
    }

    var subtitleMenu by remember { mutableStateOf(false) }
    var showOnlineSubtitles by remember { mutableStateOf(false) }
    if (subtitleMenu) SubtitleSelectionSheet(
        player = player,
        onDismiss = { subtitleMenu = false },
        onFile = { subtitleMenu = false; subtitlePicker.launch("*/*") },
        onOnline = { subtitleMenu = false; showOnlineSubtitles = true },
    )
    DisposableEffect(player) {
        var shownId = player.currentMediaItem?.mediaId
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // putting a subtitle into the item (replaceMediaItem) reports a transition to the same video:
                // the subtitle file and its delay must survive that
                if (mediaItem?.mediaId == shownId) return
                shownId = mediaItem?.mediaId
                currentFileName = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                subFile = null
                subDelay = 0L
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // The subtitle used last time for this video comes back without asking the internet; otherwise,
    // when the user turned it on, one is fetched from the sources (in their order)
    LaunchedEffect(currentFileName) {
        if (stremio && playbackRuntime?.first?.subtitleLanguages?.isEmpty() == true) return@LaunchedEffect
        val item = player.currentMediaItem ?: return@LaunchedEffect
        val config = VideoServices.config()
        val query = SubtitleService.queryFor(context, item.localConfiguration?.uri, currentFileName,
            playbackRuntime?.first?.subtitleLanguages?.joinToString(",") ?: config.languages)
        val key = SubtitleFiles.videoKey(currentFileName, query.byteSize)
        currentVideoKey = key
        if (item.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true) return@LaunchedEffect
        SubtitleFiles.lastFor(context, key)?.let { loadSubtitleFile(it, null); return@LaunchedEffect }
        if (!config.autoDownload || !SubtitleService.available(context, config)) return@LaunchedEffect
        runCatching {
            val outcome = SubtitleService.search(context, config, query)
            val best = outcome.results.firstOrNull { it.language in query.languages } ?: outcome.results.firstOrNull()
            if (best != null) {
                val file = SubtitleFiles.fetch(context, SubtitleService.provider(context, config, best.provider)!!, best, key)
                loadSubtitleFile(file, best.language.uppercase())
            }
        }
    }

    if (showOnlineSubtitles) {
        OnlineSubtitleDialog(
            rawTitle = currentFileName,
            uri = player.currentMediaItem?.localConfiguration?.uri,
            videoKey = currentVideoKey,
            onDismiss = { showOnlineSubtitles = false },
            onFile = { file, label ->
                loadSubtitleFile(file, label)
                showOnlineSubtitles = false
            },
        )
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var levelsActive by remember { mutableStateOf(false) }
    var gestureBrightness by remember { mutableStateOf<Float?>(null) }
    var gestureVolume by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            player.pause()
            sleepMinutes = 0
        }
    }
    if (showMenu) {
        PlaybackMenu(
            player = player,
            playerView = playerView,
            speed = speed,
            onSpeed = { speed = it; player.setPlaybackSpeed(it) },
            sleepMinutes = sleepMinutes,
            onSleep = { sleepMinutes = it },
            subDelayMs = subDelay,
            onSubDelay = { changeDelay(it) },
            hasExternalSub = subFile != null,
            subStyle = subStyle,
            onSubStyle = { subStyle = it; VideoPrefs.setSubtitleStyle(context, it) },
            matchFps = matchFps,
            onMatchFps = {
                matchFps = it
                VideoPrefs.setMatchFrameRate(context, it)
                player.setVideoChangeFrameRateStrategy(
                    if (it) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                )
            },
            onDismiss = { showMenu = false },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    playerView = this
                    resizeMode = VideoPrefs.resizeMode(ctx)
                    attachGestures(this, player, ctx as? android.app.Activity, { speed }, { gestureHint = it }) { b, v, active ->
                        levelsActive = active
                        if (b != null) gestureBrightness = b
                        if (v != null) gestureVolume = v
                        if (!active) {
                            gestureBrightness = null
                            gestureVolume = null
                        }
                    }
                    setShowSubtitleButton(true)
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    controllerAutoShow = false
                    controllerShowTimeoutMs = 2500
                    hideController()
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                        context.findActivity()?.window?.let { window ->
                            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                                if (controlsVisible) show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                else hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                            }
                        }
                    })
                }
            },
            update = { view ->
                view.useController = !inPictureInPicture
                view.subtitleView?.apply {
                    val box = subStyle.edge == 3
                    setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            subStyle.color,
                            if (box) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            when (subStyle.edge) {
                                1 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                2 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                            },
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subStyle.scale)
                }
            },
        )
        PlayerLevelControls(
            visible = (controlsVisible || levelsActive) && !inPictureInPicture,
            window = context.findActivity()?.window,
            brightnessOverride = gestureBrightness,
            volumeOverride = gestureVolume,
            onVolumeBoost = { ratio ->
                de.mm20.launcher2.comms.media.MediaAudioFx.attachToSession(player.audioSessionId)
                de.mm20.launcher2.comms.media.MediaAudioFx.setLoudnessGain(de.mm20.launcher2.comms.media.volumeGainMilliBel(ratio))
            },
            onAdjusting = { adjusting ->
                playerView?.let { view ->
                    view.controllerShowTimeoutMs = if (adjusting) 0 else 3000
                    view.showController()
                }
            },
        )
        if (reconnecting && !inPictureInPicture) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(if (waitingForNetwork) I18nR.string.au16_reconnect_waiting else I18nR.string.au16_reconnect_label),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else if (playbackError && !inPictureInPicture) {
            val remote = player.currentMediaItem?.localConfiguration?.uri?.let { RemoteVideo.isRemote(it) } == true
            Text(
                text = stringResource(if (remote) I18nR.string.vn_network_open_failed else I18nR.string.au_video_playback_error),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        gestureHint?.let { hint ->
            Text(
                text = hint,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (isTorrent && !inPictureInPicture && !playbackError && playbackStage == Player.STATE_BUFFERING) {
            TorrentConnectionCard(torrent, Modifier.align(Alignment.Center).padding(horizontal = 24.dp))
        }
        if (isTorrent && !inPictureInPicture && controlsVisible) {
            Text(
                text = stringResource(
                    I18nR.string.au_video_torrent_stats, torrent.peers, torrent.downloadBytesPerSecond / 1024, (torrent.progress * 100).toInt()
                ),
                color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 52.dp, end = 12.dp),
            )
        }
        AnimatedVisibility(
            visible = controlsVisible && !inPictureInPicture,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.pause(); onClose() },
                    modifier = Modifier.background(Color(0xB31A1A1A), androidx.compose.foundation.shape.CircleShape)) {
                    Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back), tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { subtitleMenu = true }) {
                        Text(stringResource(I18nR.string.hc_subtitles_2), color = Color.White)
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_playback_options), tint = Color.White)
                }
            }
        }
    }
}

/** File name without the container extension, for subtitle searches */
internal fun cleanTitle(name: String): String =
    name.replace(Regex("\\.(mkv|mp4|avi|mov|webm|m4v|ts|mpg|mpeg|wmv|flv)$", RegexOption.IGNORE_CASE), "")

@Composable
private fun OnlineSubtitleDialog(
    rawTitle: String,
    uri: Uri?,
    videoKey: String?,
    onDismiss: () -> Unit,
    onFile: (java.io.File, String) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.getString(I18nR.string.vn_searching)) }
    var results by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var config by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.VideoServicesConfig?>(null) }
    var query by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.SubtitleQuery?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searchAll by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(rawTitle, searchAll) {
        busy = true
        status = context.getString(I18nR.string.vn_searching)
        val c = VideoServices.config()
        config = c
        if (!SubtitleService.available(context, c)) {
            status = context.getString(I18nR.string.vn_no_sources)
            busy = false
            return@LaunchedEffect
        }
        runCatching {
            val q = SubtitleService.queryFor(context, uri, rawTitle, c.languages)
            query = q
            SubtitleService.search(context, c, q, all = searchAll)
        }.onSuccess { o ->
            results = o.results
            source = o.source
            status = when {
                o.results.isEmpty() && o.errors.isNotEmpty() -> context.getString(I18nR.string.vn_some_sources_failed, o.errors.joinToString("; "))
                o.results.isEmpty() -> context.getString(I18nR.string.vn_no_subtitles_found, cleanTitle(rawTitle))
                o.errors.isNotEmpty() -> context.getString(I18nR.string.vn_some_sources_failed, o.errors.joinToString("; "))
                else -> ""
            }
        }.onFailure { status = (it.message ?: context.getString(I18nR.string.au_video_unknown_error)) }
        busy = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_subtitles))  } },
        text = {
            Column {
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results.size) { i ->
                        val r = results[i]
                        Column(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                val c = config ?: return@clickable
                                val p = SubtitleService.provider(context, c, r.provider) ?: return@clickable
                                busy = true
                                status = context.getString(I18nR.string.vn_downloading)
                                scope.launch {
                                    runCatching { SubtitleFiles.fetch(context, p, r, videoKey) }
                                        .onSuccess { onFile(it, r.language.uppercase()) }
                                        .onFailure {
                                            busy = false
                                            status = (it.message ?: context.getString(I18nR.string.au_video_unknown_error))
                                        }
                                }
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(r.release.ifBlank { r.fileName }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    r.language.uppercase(),
                                    r.downloads.takeIf { it > 0 }?.let { context.getString(I18nR.string.au_video_downloads_count, it) },
                                    if (r.hearingImpaired) context.getString(I18nR.string.au_video_hearing_impaired_short) else null,
                                    if (r.hashMatch) context.getString(I18nR.string.vn_exact_match) else null,
                                    r.provider.replace('_', ' '),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy && !searchAll, onClick = { searchAll = true }) { Text(stringResource(I18nR.string.vn_search_all_sources)) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
    )
}

/** The activity behind a context, which may be wrapped */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

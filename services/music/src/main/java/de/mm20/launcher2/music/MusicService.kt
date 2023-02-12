package de.mm20.launcher2.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

interface MusicService {
    val playbackState: Flow<PlaybackState>
    val title: Flow<String?>
    val artist: Flow<String?>
    val album: Flow<String?>
    val albumArt: Flow<Bitmap?>
    val duration: Flow<Long?>

    val lastPlayerPackage: String?

    fun next()
    fun previous()
    fun pause()
    fun play()
    fun togglePause()
    fun seekTo(position: Long)
    fun openPlayer(): PendingIntent?

    fun openPlayerChooser(context: Context)

    fun resetPlayer()
}

internal class MusicServiceImpl(
    private val context: Context,
    notificationRepository: NotificationRepository
) : MusicService, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val dataStore: LauncherDataStore by inject()

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override var lastPlayerPackage: String? = null
        get() {
            if (field == null) {
                field = preferences.getString(PREFS_KEY_LAST_PLAYER, null)
            }
            return field
        }
        set(value) {
            preferences.edit {
                putString(PREFS_KEY_LAST_PLAYER, value)
            }
            field = value
        }

    private val currentMediaController: SharedFlow<MediaController?> =
        combine(
            notificationRepository.notifications,
            dataStore.data.map { it.musicWidget.filterSources }
        ) { notifications, filter ->
            withContext(Dispatchers.Default) {
                val musicApps = if (filter) getMusicApps() else null
                val sbn: StatusBarNotification? = notifications.filter {
                    it.notification.extras.getParcelable(NotificationCompat.EXTRA_MEDIA_SESSION) as? MediaSession.Token != null &&
                            (musicApps?.contains(it.packageName) != false)
                }.maxByOrNull { it.postTime }

                return@withContext (sbn?.notification?.extras?.get(NotificationCompat.EXTRA_MEDIA_SESSION) as? MediaSession.Token)
            }
        }
            .distinctUntilChanged()
            .map { token ->
                if (token == null) return@map null
                else {
                    return@map MediaController(context, token).also {
                        lastPlayerPackage = it.packageName
                    }
                }
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private val currentMetadata: SharedFlow<MediaMetadata?> = channelFlow {
        currentMediaController.collectLatest { controller ->
            if (controller == null) {
                send(null)
                return@collectLatest
            }
            send(controller.metadata)
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    trySend(metadata)
                }
            }
            try {
                controller.registerCallback(callback, Handler(Looper.getMainLooper()))
                awaitCancellation()
            } finally {
                controller.unregisterCallback(callback)
            }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override val playbackState: SharedFlow<PlaybackState> = channelFlow {
        currentMediaController.collectLatest { controller ->
            if (controller == null) return@collectLatest send(PlaybackState.Stopped)
            send(
                when (controller.playbackState?.state) {
                    android.media.session.PlaybackState.STATE_PLAYING -> PlaybackState.Playing
                    android.media.session.PlaybackState.STATE_PAUSED -> PlaybackState.Paused
                    else -> PlaybackState.Stopped
                }
            )
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    trySend(
                        when (state?.state) {
                            android.media.session.PlaybackState.STATE_PLAYING -> PlaybackState.Playing
                            android.media.session.PlaybackState.STATE_PAUSED -> PlaybackState.Paused
                            else -> PlaybackState.Stopped
                        }
                    )
                }
            }
            try {
                controller.registerCallback(callback, Handler(Looper.getMainLooper()))
                awaitCancellation()
            } finally {
                controller.unregisterCallback(callback)
            }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)


    private var lastTitle: String? = null
        get() {
            if (field == null) {
                field = preferences.getString(PREFS_KEY_TITLE, null)
            }
            return field
        }
        set(value) {
            preferences.edit {
                putString(PREFS_KEY_TITLE, value)
            }
            field = value
        }

    override val title: Flow<String?> = channelFlow {
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                send(lastTitle)
                return@collectLatest
            }

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: currentMediaController.firstOrNull()?.packageName?.let { pkg ->
                    getAppLabel(pkg)?.let {
                        context.getString(
                            R.string.music_widget_default_title,
                            it
                        )
                    }
                }
            lastTitle = title
            send(title)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private var lastArtist: String? = null
        get() {
            if (field == null) {
                field = preferences.getString(PREFS_KEY_ARTIST, null)
            }
            return field
        }
        set(value) {
            preferences.edit {
                putString(PREFS_KEY_ARTIST, value)
            }
            field = value
        }

    override val artist: Flow<String?> = channelFlow {
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                send(lastArtist)
                return@collectLatest
            }

            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: currentMediaController.firstOrNull()?.packageName?.let { pkg ->
                    getAppLabel(pkg)
                }
            lastArtist = artist
            send(artist)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private var lastAlbum: String? = null
        get() {
            if (field == null) {
                field = preferences.getString(PREFS_KEY_ALBUM, null)
            }
            return field
        }
        set(value) {
            preferences.edit {
                putString(PREFS_KEY_ALBUM, value)
            }
            field = value
        }

    override val album = channelFlow {
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                send(lastAlbum)
                return@collectLatest
            }

            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            lastAlbum = album
            send(album)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)


    override val albumArt: Flow<Bitmap?> = channelFlow {
        val size = context.resources.getDimensionPixelSize(R.dimen.album_art_size)
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                val isNull = preferences.getString(PREFS_KEY_ALBUM_ART, "null") == "null"
                if (isNull) {
                    send(null)
                } else {
                    val bmp: Bitmap? = withContext(Dispatchers.IO) {
                        val file = java.io.File(context.filesDir, "album_art")
                        val request = ImageRequest.Builder(context)
                            .data(file)
                            .size(size)
                            .build()
                        context.imageLoader.execute(request).drawable?.toBitmap()
                    }
                    send(bmp)
                }
                return@collectLatest
            }
            val bitmap =
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { resize(it, size) }
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { resize(it, size) }
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?.let { loadBitmapFromUri(Uri.parse(it), size) }
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                        ?.let { loadBitmapFromUri(Uri.parse(it), size) }
            withContext(Dispatchers.IO) {
                if (bitmap == null) {
                    preferences.edit {
                        putString(PREFS_KEY_ALBUM_ART, "null")
                    }
                } else {
                    val file = java.io.File(context.filesDir, "album_art")
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
                    preferences.edit {
                        putString(PREFS_KEY_ALBUM_ART, "notnull")
                    }
                }
            }
            send(bitmap)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override val duration: Flow<Long?> = channelFlow {
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                send(null)
                return@collectLatest
            }
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            send(duration.takeIf { it >  0 })
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)


    private suspend fun loadBitmapFromUri(uri: Uri, size: Int): Bitmap? {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size)
                .scale(Scale.FILL)
                .build()
            context.imageLoader.execute(request).drawable?.toBitmap()
        } catch (e: IOException) {
            CrashReporter.logException(e)
        } catch (e: SecurityException) {
            CrashReporter.logException(e)
        }
        return null
    }

    private suspend fun resize(bitmap: Bitmap, size: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context).data(bitmap)
                .size(size)
                .scale(Scale.FILL)
                .build()
            context.imageLoader.execute(request).drawable?.toBitmap()
        }
    }

    private fun getAppLabel(packageName: String): String? {
        return try {
            context
                .packageManager
                .getPackageInfo(packageName, 0).applicationInfo
                .loadLabel(context.packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }


    override fun previous() {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            if (controller != null) {
                controller.transportControls.skipToPrevious()
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                audioManager.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    override fun next() {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            if (controller != null) {
                controller.transportControls.skipToNext()
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                audioManager.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    override fun play() {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            if (controller != null) {
                controller.transportControls.play()
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                audioManager.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    override fun pause() {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            if (controller != null) {
                controller.transportControls.pause()
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    override fun togglePause() {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            if (controller != null && controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) {
                pause()
            } else {
                play()
            }
        }
    }

    override fun seekTo(position: Long) {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            controller?.transportControls?.seekTo(position)
        }
    }

    override fun openPlayer(): PendingIntent? {

        val controller = currentMediaController.replayCache.firstOrNull()

        controller?.sessionActivity?.let {
            return it
        }

        val packageName = controller?.packageName ?: lastPlayerPackage

        val intent = packageName?.let {
            context.packageManager.getLaunchIntentForPackage(it)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } ?: return null

        if (context.packageManager.resolveActivity(intent, 0) == null) {
            return null
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun openPlayerChooser(context: Context) {
        context.startActivity(
            Intent.createChooser(
                Intent("android.intent.action.MUSIC_PLAYER")
                    .apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                null
            )
        )
    }

    private fun getMusicApps(): Set<String> {
        // List of known music apps that don't have the correct intent filter
        val apps = mutableSetOf(
            "com.aspiro.tidal", // Tidal
            "com.bandcamp.android", // Bandcamp
            "com.qobuz.music", // Qobuz
            "tv.plex.labs.plexamp", // Plexamp
            "de.ph1b.audiobook", // Voice
            "de.eindm.boum", // Boum
        )
        var intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
        apps.addAll(context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName })
        intent = Intent("android.intent.action.MUSIC_PLAYER")
        apps.addAll(context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName })
        return apps
    }

    override fun resetPlayer() {
        scope.launch {
            preferences.edit {
                clear()
            }
        }
    }

    companion object {

        private const val PREFS = "music"
        private const val PREFS_KEY_TITLE = "title"
        private const val PREFS_KEY_ARTIST = "artist"
        private const val PREFS_KEY_ALBUM = "album"
        private const val PREFS_KEY_ALBUM_ART = "album_art"
        private const val PREFS_KEY_LAST_PLAYER = "last_player"
    }
}

enum class PlaybackState {
    Paused,
    Playing,
    Stopped
}
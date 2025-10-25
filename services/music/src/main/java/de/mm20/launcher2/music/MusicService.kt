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
import android.media.session.PlaybackState.CustomAction
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.preferences.media.MediaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.io.IOException

interface MusicService {
    val playbackState: Flow<PlaybackState>
    val title: Flow<String?>
    val artist: Flow<String?>
    val album: Flow<String?>
    val albumArt: Flow<Bitmap?>
    val position: Flow<Long?>
    val duration: Flow<Long?>

    val supportedActions: Flow<SupportedActions>

    val lastPlayerPackage: String?

    fun next()
    fun previous()
    fun pause()
    fun play()
    fun togglePause()
    fun seekTo(position: Long)
    fun performCustomAction(action: CustomAction)
    fun openPlayer(): PendingIntent?

    fun openPlayerChooser(context: Context)

    suspend fun getInstalledPlayerPackages(): Set<String>

    fun resetPlayer()
}

internal class MusicServiceImpl(
    private val context: Context,
    notificationRepository: NotificationRepository,
    private val settings: MediaSettings,
) : MusicService, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

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
            settings,
        ) { notifications, settings ->
            withContext(Dispatchers.Default) {
                val musicApps = getEnabledPlayerPackages(
                    settings.allowList,
                    settings.denyList,
                )
                val sbn: Notification? = notifications.filter {
                    it.mediaSessionToken != null && musicApps.contains(it.packageName)
                }.maxByOrNull { it.postTime }

                return@withContext sbn?.mediaSessionToken
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

    private val currentState: SharedFlow<android.media.session.PlaybackState?> = channelFlow {
        currentMediaController.collectLatest { controller ->
            if (controller == null) {
                send(null)
                return@collectLatest
            }
            send(controller.playbackState)
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    trySend(state)
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
        currentState.collectLatest { state ->
            if (state == null) {
                send(PlaybackState.Stopped)
                return@collectLatest
            }
            when (state.state) {
                android.media.session.PlaybackState.STATE_PLAYING -> send(PlaybackState.Playing)
                android.media.session.PlaybackState.STATE_PAUSED -> send(PlaybackState.Paused)
                android.media.session.PlaybackState.STATE_STOPPED -> send(PlaybackState.Stopped)
                else -> send(PlaybackState.Stopped)
            }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private var lastPosition: Long? = null
        get() {
            if (field == null) {
                field = preferences.getLong(PREFS_KEY_POSITION, -1).takeIf { it >= 0 }
            }
            return field
        }
        set(value) {
            preferences.edit {
                putLong(PREFS_KEY_POSITION, value ?: -1)
            }
            field = value
        }

    override val position: SharedFlow<Long?> = channelFlow {
        currentState.collectLatest { state ->
            if (state == null || state.state != android.media.session.PlaybackState.STATE_PLAYING) {
                send(lastPosition)
                return@collectLatest
            }
            if (state.position < 0 || state.lastPositionUpdateTime == 0L) {
                send(null)
                lastPosition = null
                return@collectLatest
            }
            while (isActive) {
                val offset = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                val position = state.position + offset
                lastPosition = position
                send(position)
                delay(1000)
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
        var lastBitmap: Bitmap? = null
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

            if (lastBitmap != null && lastBitmap!!.sameAs(bitmap))
                return@collectLatest
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
            lastBitmap = bitmap
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private var lastDuration: Long? = null
        get() {
            if (field == null) {
                field = preferences.getLong(PREFS_KEY_DURATION, -1).takeIf { it > 0 }
            }
            return field
        }
        set(value) {
            preferences.edit {
                putLong(PREFS_KEY_DURATION, value ?: -1)
            }
            field = value
        }

    override val duration: Flow<Long?> = channelFlow {
        currentMetadata.collectLatest { metadata ->
            if (metadata == null) {
                send(lastDuration)
                return@collectLatest
            }
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            lastDuration = duration
            send(duration)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override val supportedActions: Flow<SupportedActions> = channelFlow {
        currentState.collectLatest { state ->
            if (state == null) {
                send(SupportedActions())
                return@collectLatest
            }
            send(SupportedActions(lastPlayerPackage, state.actions, state.customActions))
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    private suspend fun loadBitmapFromUri(uri: Uri, size: Int): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size)
                .scale(Scale.FILL)
                .target {
                    bitmap = it.toBitmap()
                }
                .build()
            val result = context.imageLoader.execute(request)
        } catch (e: IOException) {
            CrashReporter.logException(e)
        } catch (e: SecurityException) {
            CrashReporter.logException(e)
        }
        return bitmap
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
                .getPackageInfo(packageName, 0).applicationInfo!!
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

    override fun performCustomAction(action: CustomAction) {
        scope.launch {
            val controller = currentMediaController.firstOrNull()
            controller?.transportControls?.sendCustomAction(action.action, action.extras)
        }
    }

    override suspend fun getInstalledPlayerPackages(): Set<String> {
        val apps = mutableSetOf<String>()
        withContext(Dispatchers.IO) {
            var intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
            apps.addAll(context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.applicationInfo.packageName })
            intent = Intent("android.intent.action.MUSIC_PLAYER")
            apps.addAll(context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.applicationInfo.packageName })
        }
        return apps
    }

    private suspend fun getEnabledPlayerPackages(
        allowList: Set<String>,
        denyList: Set<String>
    ): Set<String> {
        val installed = getInstalledPlayerPackages()
        return installed.union(allowList).subtract(denyList).toSet()
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
        private const val PREFS_KEY_DURATION = "duration"
        private const val PREFS_KEY_POSITION = "position"
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
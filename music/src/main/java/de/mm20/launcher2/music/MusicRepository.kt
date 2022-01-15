package de.mm20.launcher2.music

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.session.MediaSession
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.session.MediaController
import androidx.media2.session.SessionCommandGroup
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.Executors

interface MusicRepository {
    val playbackState: Flow<PlaybackState>
    val title: Flow<String?>
    val artist: Flow<String?>
    val album: Flow<String?>
    val albumArt: Flow<Bitmap?>

    fun next()
    fun previous()
    fun pause()
    fun play()
    fun togglePause()
    fun openPlayer(): PendingIntent?

    fun openPlayerChooser(context: Context)

    fun resetPlayer()
}

class MusicRepositoryImpl(
    private val context: Context,
    private val notificationRepository: NotificationRepository
) : MusicRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val dataStore: LauncherDataStore by inject()

    override val playbackState = MutableStateFlow(PlaybackState.Stopped)
    override val title = MutableStateFlow<String?>(null)
    override val artist = MutableStateFlow<String?>(null)
    override val album = MutableStateFlow<String?>(null)
    override val albumArt = MutableStateFlow<Bitmap?>(null)

    private var lastPlayer: String? = null

    private var lastToken: String? = null

    private val semaphore = Semaphore(permits = 1)

    init {
        scope.launch {
            notificationRepository.notifications
                .mapNotNull {
                    it
                        .sortedByDescending { it.postTime }
                        .find {
                            it.notification.category == Notification.CATEGORY_TRANSPORT || it.notification.category == Notification.CATEGORY_SERVICE
                        }
                }
                .collectLatest {
                    val token =
                        it.notification.extras[NotificationCompat.EXTRA_MEDIA_SESSION] as? MediaSession.Token
                            ?: return@collectLatest
                    setMediaSession(
                        MediaSessionCompat.Token.fromToken(token),
                        it.packageName
                    )
                }
        }
    }

    private fun setMediaSession(token: MediaSessionCompat.Token, packageName: String) {
        if (token.toString() == lastToken.toString()) return

        scope.launch {
            val filterMusicApps = dataStore.data.map { it.musicWidget.filterSources }.first()
            if (filterMusicApps && !isMusicApp(packageName)) {
                return@launch
            }

            try {
                semaphore.acquire()
                mediaController?.close()
                val appName = context.packageManager.getPackageInfo(
                    packageName,
                    0
                ).applicationInfo.loadLabel(context.packageManager)
                mediaController = MediaController.Builder(context)
                    .setSessionCompatToken(token)
                    .setControllerCallback(
                        Executors.newSingleThreadExecutor(),
                        mediaSessionCallback
                    )
                    .build()

                setMetadata(
                    title = context.getString(R.string.music_widget_default_title, appName),
                    artist = null,
                    album = null,
                    albumArt = null,
                    packageName
                )
                lastToken = token.toString()
            } finally {
                semaphore.release()
            }
        }
    }

    private var mediaController: MediaController? = null
        set(value) {
            if (value == null) {
                playbackState.value = PlaybackState.Stopped
            }
            field = value
        }

    private val mediaSessionCallback = object : MediaController.ControllerCallback() {
        override fun onConnected(
            controller: MediaController,
            allowedCommands: SessionCommandGroup
        ) {
            super.onConnected(controller, allowedCommands)
            if (controller != mediaController) return
            updateMetadata(controller.currentMediaItem, controller.connectedToken?.packageName)
            updateState(controller.playerState)
        }

        override fun onCurrentMediaItemChanged(controller: MediaController, item: MediaItem?) {
            super.onCurrentMediaItemChanged(controller, item)
            if (controller != mediaController) return
            updateMetadata(item, controller.connectedToken?.packageName)
        }

        override fun onPlayerStateChanged(controller: MediaController, state: Int) {
            super.onPlayerStateChanged(controller, state)
            if (controller != mediaController) return
            updateState(state)
        }

        override fun onDisconnected(controller: MediaController) {
            super.onDisconnected(controller)
            if (controller != mediaController) return
            mediaController = null
        }
    }

    private fun updateMetadata(mediaItem: MediaItem?, playerPackage: String?) {
        val metadata = mediaItem?.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_COMPOSER)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_WRITER)
            ?: return
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

        // Hack for Spotify sending inconsistent metadata updates
        if (playerPackage == "com.spotify.music" && album == null) return

        scope.launch {
            setMetadata(title, artist, album, albumArt, playerPackage)
        }
    }

    private fun updateState(playerState: Int) {
        val playbackState = when (playerState) {
            SessionPlayer.PLAYER_STATE_PLAYING -> PlaybackState.Playing
            SessionPlayer.PLAYER_STATE_PAUSED -> PlaybackState.Paused
            else -> PlaybackState.Stopped
        }
        this.playbackState.value = playbackState
    }

    init {
        loadLastPlaybackMetadata()
    }


    private fun loadLastPlaybackMetadata() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastPlayer = prefs.getString(PREFS_KEY_LAST_PLAYER, null)
        title.value = prefs.getString(PREFS_KEY_TITLE, null)
        artist.value = prefs.getString(PREFS_KEY_ARTIST, null)
        album.value = prefs.getString(PREFS_KEY_ALBUM, null)
        if (prefs.getString(PREFS_KEY_ALBUM_ART, "null") == "null") {
            albumArt.value = null
        } else scope.launch {
            val albumArt = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(File(context.cacheDir, "album_art").absolutePath)
            }
            this@MusicRepositoryImpl.albumArt.value = albumArt
        }
        playbackState.value = PlaybackState.Stopped
    }

    override fun previous() {
        if (mediaController?.skipToPreviousPlaylistItem()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    override fun next() {
        if (mediaController?.skipToNextPlaylistItem()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    override fun play() {
        if (mediaController?.play()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    override fun pause() {
        if (mediaController?.pause()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    override fun togglePause() {
        if (playbackState.value != PlaybackState.Playing) play() else pause()
    }

    override fun openPlayer(): PendingIntent? {
        mediaController?.sessionActivity?.let {
            return it
        }

        val intent = lastPlayer?.let {
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
                Intent(Intent.ACTION_MAIN)
                    .apply {
                        addCategory(Intent.CATEGORY_APP_MUSIC)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                null
            )
        )
    }

    private suspend fun isMusicApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
        return !withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(intent, 0)
                .none { it.activityInfo.packageName == packageName }
        }
    }

    private suspend fun setMetadata(
        title: String?,
        artist: String?,
        album: String?,
        albumArt: Bitmap?,
        playerPackage: String?
    ) {
        withContext(Dispatchers.IO) {
            if (albumArt == null) {
                this@MusicRepositoryImpl.albumArt.value = null
            } else {
                val size = context.resources.getDimension(R.dimen.album_art_size)
                val (scaledW, scaledH) = if (albumArt.width > albumArt.height) {
                    size * albumArt.width / albumArt.height to size
                } else {
                    size to size * albumArt.height / albumArt.width
                }
                val scaledBitmap = albumArt.scale(scaledW.toInt(), scaledH.toInt())
                val file = File(context.cacheDir, "album_art")
                val outStream = file.outputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.close()
                this@MusicRepositoryImpl.albumArt.value = scaledBitmap

            }

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(PREFS_KEY_TITLE, title)
                putString(PREFS_KEY_ARTIST, artist)
                putString(PREFS_KEY_ALBUM, album)
                putString(PREFS_KEY_LAST_PLAYER, playerPackage)
                putString(PREFS_KEY_ALBUM_ART, if (albumArt == null) "null" else "notnull")
            }


            lastPlayer = playerPackage ?: lastPlayer
            this@MusicRepositoryImpl.title.value = title
            this@MusicRepositoryImpl.artist.value = artist
            this@MusicRepositoryImpl.album.value = album
        }
    }

    override fun resetPlayer() {
        scope.launch {
            mediaController?.close()
            mediaController = null
            setMetadata(null, null, null, null, null)
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
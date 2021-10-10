package de.mm20.launcher2.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.lifecycle.MutableLiveData
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.session.MediaController
import androidx.media2.session.SessionCommand
import androidx.media2.session.SessionCommandGroup
import androidx.media2.session.SessionResult
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executors

class MusicRepository(val context: Context) {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    val playbackState = MutableLiveData<PlaybackState>()
    val title = MutableLiveData<String?>()
    val artist = MutableLiveData<String?>()
    val album = MutableLiveData<String?>()
    val albumArt = MutableLiveData<Bitmap?>()

    private var lastPlayer: String? = null
        set(value) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(PREFS_KEY_LAST_PLAYER, value)
            }
            field = value
        }

    private var lastToken: String? = null

    fun setMediaSession(token: MediaSessionCompat.Token) {
        if (token.toString() == lastToken.toString()) return
        mediaController?.close()
        mediaController = MediaController.Builder(context)
                .setSessionCompatToken(token)
                .setControllerCallback(Executors.newSingleThreadExecutor(), mediaSessionCallback)
                .build()
        lastToken = token.toString()
    }

    private var mediaController: MediaController? = null
        set(value) {
            if (value == null) {
                playbackState.postValue(PlaybackState.Stopped)
            }
            field = value
        }

    private val mediaSessionCallback = object : MediaController.ControllerCallback() {
        override fun onConnected(controller: MediaController, allowedCommands: SessionCommandGroup) {
            super.onConnected(controller, allowedCommands)
            updateMetadata()
            updateState()
        }

        override fun onCurrentMediaItemChanged(controller: MediaController, item: MediaItem?) {
            super.onCurrentMediaItemChanged(controller, item)
            updateMetadata()
        }

        override fun onPlayerStateChanged(controller: MediaController, state: Int) {
            super.onPlayerStateChanged(controller, state)
            updateState()
        }

        override fun onPlaybackInfoChanged(controller: MediaController, info: MediaController.PlaybackInfo) {
            super.onPlaybackInfoChanged(controller, info)
            Log.d("MM20", "CurrentPosition" + controller.currentPosition.toString())
        }

        override fun onDisconnected(controller: MediaController) {
            super.onDisconnected(controller)
            mediaController = null
        }

        /*override fun onMetadataChanged(metadata: MediaController?) {
            super.onMetadataChanged(metadata)
            updateState()
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            mediaController = null
            hasActiveSession.value = false
            playbackState.value = PlaybackState.Stopped
        }*/
    }

    private fun updateMetadata() {
        val metadata = mediaController?.currentMediaItem?.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_COMPOSER)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_WRITER)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        lastPlayer = mediaController?.connectedToken?.packageName ?: lastPlayer
        this@MusicRepository.title.postValue(title)
        this@MusicRepository.artist.postValue(artist)
        this@MusicRepository.album.postValue(album)

        scope.launch {
            withContext(Dispatchers.IO) {
                val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(PREFS_KEY_ALBUM_ART, if (albumArt == null) "null" else "notnull")
                }
                if (albumArt == null) {
                    this@MusicRepository.albumArt.postValue(null)
                    return@withContext
                }
                val size = context.resources.getDimensionPixelSize(R.dimen.album_art_size)
                val scaledBitmap = albumArt.scale(size, size)
                val file = File(context.cacheDir, "album_art")
                val outStream = file.outputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.close()
                this@MusicRepository.albumArt.postValue(scaledBitmap)
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(PREFS_KEY_TITLE, title)
            putString(PREFS_KEY_ARTIST, artist)
            putString(PREFS_KEY_ALBUM, album)
        }
    }

    private fun updateState() {
        val playbackState = when (mediaController?.playerState) {
            SessionPlayer.PLAYER_STATE_PLAYING -> PlaybackState.Playing
            SessionPlayer.PLAYER_STATE_PAUSED -> PlaybackState.Paused
            else -> PlaybackState.Stopped
        }
        this@MusicRepository.playbackState.postValue(playbackState)
    }

    val hasActiveSession: Boolean = mediaController?.isConnected != null

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
            this@MusicRepository.albumArt.value = albumArt
        }
        playbackState.value = PlaybackState.Stopped
    }

    fun previous() {
        if (mediaController?.skipToPreviousPlaylistItem()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    fun next() {
        if (mediaController?.skipToNextPlaylistItem()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    fun play() {
        if (mediaController?.play()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    fun pause() {
        if (mediaController?.pause()?.get() == null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }
    }

    fun togglePause() {
        if (playbackState.value != PlaybackState.Playing) play() else pause()
    }

    fun getLaunchIntent(context: Context): PendingIntent {
        mediaController?.sessionActivity?.let {
            return it
        }
        val intent = Intent(Intent.ACTION_MAIN)
                .setPackage(lastPlayer)
                .addCategory(Intent.CATEGORY_APP_MUSIC)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openPlayerChooser(context: Context) {
        context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_MAIN)
                        .apply {
                            addCategory(Intent.CATEGORY_APP_MUSIC)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                null)
        )
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
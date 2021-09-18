package de.mm20.launcher2.music

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    val musicRepository = MusicRepository.getInstance(app)

    val title: LiveData<String?> = musicRepository.title
    val artist: LiveData<String?> = musicRepository.artist
    val album: LiveData<String?> = musicRepository.album
    val albumArt: LiveData<Bitmap?> = musicRepository.albumArt
    val playbackState: LiveData<PlaybackState> = musicRepository.playbackState

    val hasActiveSession : Boolean = musicRepository.hasActiveSession

    fun previous() {
        musicRepository.previous()
    }

    fun next() {
        musicRepository.next()
    }

    fun play() {
        musicRepository.play()
    }

    fun pause() {
        musicRepository.pause()
    }

    fun togglePause() {
        musicRepository.togglePause()
    }

    fun getLaunchIntent(context: Context): PendingIntent {
        return musicRepository.getLaunchIntent(context)
    }

    fun openPlayerChooser(context: Context) {
        musicRepository.openPlayerChooser(context)
    }
}
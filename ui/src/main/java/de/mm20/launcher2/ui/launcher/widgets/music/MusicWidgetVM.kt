package de.mm20.launcher2.ui.launcher.widgets.music

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.music.MusicRepository
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicWidgetVM: ViewModel(), KoinComponent {
    private val musicRepository: MusicRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    val title: LiveData<String?> = musicRepository.title.asLiveData()
    val artist: LiveData<String?> = musicRepository.artist.asLiveData()
    val album: LiveData<String?> = musicRepository.album.asLiveData()
    val albumArt: LiveData<Bitmap?> = musicRepository.albumArt.asLiveData()
    val playbackState: LiveData<PlaybackState> = musicRepository.playbackState.asLiveData()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Notifications).asLiveData()

    fun skipPrevious() {
        musicRepository.previous()
    }

    fun skipNext() {
        musicRepository.next()
    }

    fun togglePause() {
        musicRepository.togglePause()
    }

    fun openPlayer() {
        try {
            musicRepository.
            openPlayer()?.send()
        } catch (e: PendingIntent.CanceledException) {
            CrashReporter.logException(e)
        }
    }

    fun openPlayerSelector(context: Context) {
        musicRepository.openPlayerChooser(context)
    }

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Notifications)
    }
}
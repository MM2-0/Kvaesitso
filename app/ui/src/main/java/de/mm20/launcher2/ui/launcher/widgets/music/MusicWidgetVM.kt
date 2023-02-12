package de.mm20.launcher2.ui.launcher.widgets.music

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicWidgetVM: ViewModel(), KoinComponent {
    private val musicService: MusicService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val title: LiveData<String?> = musicService.title.asLiveData()
    val artist: LiveData<String?> = musicService.artist.asLiveData()
    val album: LiveData<String?> = musicService.album.asLiveData()
    val albumArt: LiveData<Bitmap?> = musicService.albumArt.asLiveData()
    val playbackState: LiveData<PlaybackState> = musicService.playbackState.asLiveData()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Notifications).asLiveData()

    val currentPlayerPackage
        get() = musicService.lastPlayerPackage

    fun skipPrevious() {
        musicService.previous()
    }

    fun skipNext() {
        musicService.next()
    }

    fun togglePause() {
        musicService.togglePause()
    }

    fun openPlayer() {
        try {
            musicService.openPlayer()?.send()
        } catch (e: PendingIntent.CanceledException) {
            CrashReporter.logException(e)
        }
    }

    fun openPlayerSelector(context: Context) {
        musicService.openPlayerChooser(context)
    }

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Notifications)
    }
}
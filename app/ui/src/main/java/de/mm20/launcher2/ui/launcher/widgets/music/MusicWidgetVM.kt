package de.mm20.launcher2.ui.launcher.widgets.music

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.session.PlaybackState.CustomAction
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.music.SupportedActions
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicWidgetVM: ViewModel(), KoinComponent {
    private val musicService: MusicService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val title: Flow<String?> = musicService.title
    val artist: Flow<String?> = musicService.artist
    val albumArt: Flow<Bitmap?> = musicService.albumArt.debounce { if (it == null) 500 else 0L }
    val playbackState: Flow<PlaybackState> = musicService.playbackState
    val duration: Flow<Long?> = musicService.duration
    val position: Flow<Long?> = musicService.position

    val supportedActions: Flow<SupportedActions> = musicService.supportedActions

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Notifications)

    val currentPlayerPackage
        get() = musicService.lastPlayerPackage

    fun skipPrevious() {
        musicService.previous()
    }

    fun skipNext() {
        musicService.next()
    }

    fun seekTo(position: Long) {
        musicService.seekTo(position)
    }

    fun togglePause() {
        musicService.togglePause()
    }

    fun openPlayer(context: Context) {
        try {
            musicService.openPlayer()?.sendWithBackgroundPermission(context)
        } catch (e: PendingIntent.CanceledException) {
            CrashReporter.logException(e)
        }
    }

    fun openPlayerSelector(context: Context) {
        musicService.openPlayerChooser(context)
    }

    fun performCustomAction(action: CustomAction) {
        musicService.performCustomAction(action)
    }

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Notifications)
    }
}
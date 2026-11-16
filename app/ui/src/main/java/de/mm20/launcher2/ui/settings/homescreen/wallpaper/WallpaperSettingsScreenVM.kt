package de.mm20.launcher2.ui.settings.homescreen.wallpaper

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import de.mm20.launcher2.wallpapers.StaticWallpaperTarget
import de.mm20.launcher2.wallpapers.WallpapersService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WallpaperSettingsScreenVM : ViewModel(), KoinComponent {
    private val wallpapersService: WallpapersService by inject()
    private val wallpaperSettings: WallpaperSettings by inject()

    var hasVideoWallpaper by mutableStateOf(false)
        private set
    var isVideoWallpaperActive by mutableStateOf(false)
        private set

    val textColors = wallpaperSettings.textColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), de.mm20.launcher2.preferences.SystemBarColors.Auto)

    fun setTextColors(colors: de.mm20.launcher2.preferences.SystemBarColors) = wallpaperSettings.setTextColors(colors)

    val dimWallpaper = wallpaperSettings.dimWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDimWallpaper(dimWallpaper: Boolean) {
        wallpaperSettings.setDimWallpaper(dimWallpaper)
    }

    val blurWallpaper = wallpaperSettings.blurWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBlurWallpaper(blurWallpaper: Boolean) {
        wallpaperSettings.setBlurWallpaper(blurWallpaper)
    }

    val blurWallpaperRadius = wallpaperSettings.wallpaperBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    fun setBlurWallpaperRadius(blurWallpaperRadius: Int) {
        wallpaperSettings.setWallpaperBlurRadius(blurWallpaperRadius)
    }

    fun isBlurAvailable(context: android.content.Context): Boolean {
        if (!isAtLeastApiLevel(31)) return false
        return context.getSystemService<WindowManager>()?.isCrossWindowBlurEnabled == true
    }

    val pauseOnBatterySaver = wallpaperSettings.videoPauseOnBatterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pauseOnThermal = wallpaperSettings.videoPauseOnThermalThrottling
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pauseOnDesktopMode = wallpaperSettings.videoPauseOnDesktopMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val videoTransforms = wallpaperSettings.videoTransforms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val videoSpeed = wallpaperSettings.videoSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val videoStartBehavior = wallpaperSettings.videoStartBehavior
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setPauseOnBatterySaver(pause: Boolean) =
        wallpaperSettings.setVideoPauseOnBatterySaver(pause)

    fun setPauseOnThermal(pause: Boolean) =
        wallpaperSettings.setVideoPauseOnThermalThrottling(pause)

    fun setPauseOnDesktopMode(pause: Boolean) =
        wallpaperSettings.setVideoPauseOnDesktopMode(pause)

    fun setVideoScalingMode(mode: de.mm20.launcher2.preferences.VideoWallpaperScalingMode) =
        wallpaperSettings.setVideoScalingMode(mode)

    fun setVideoZoom(zoom: Float) =
        wallpaperSettings.setVideoZoom(zoom)

    fun setVideoPosition(x: Float, y: Float) =
        wallpaperSettings.setVideoPosition(x, y)

    fun setVideoBrightness(brightness: Float) =
        wallpaperSettings.setVideoBrightness(brightness)

    fun setVideoParallax(enabled: Boolean) =
        wallpaperSettings.setVideoParallax(enabled)

    fun setVideoParallaxStrength(strength: Float) =
        wallpaperSettings.setVideoParallaxStrength(strength)

    fun setVideoSpeed(speed: Float) =
        wallpaperSettings.setVideoSpeed(speed)

    fun setVideoStartBehavior(behavior: de.mm20.launcher2.preferences.VideoWallpaperStartBehavior) =
        wallpaperSettings.setVideoStartBehavior(behavior)

    fun clearVideoPlaylist() {
        viewModelScope.launch {
            wallpapersService.clearVideoPlaylist()
            refresh()
        }
    }

    fun refresh() {
        hasVideoWallpaper = wallpapersService.hasVideoWallpaper()
        isVideoWallpaperActive = wallpapersService.isVideoWallpaperActive()
    }

    fun setStaticWallpaper(uri: Uri, target: StaticWallpaperTarget, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(wallpapersService.setStaticWallpaper(uri, target))
        }
    }

    fun setVideoWallpaper(uri: Uri, appendToPlaylist: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = wallpapersService.setVideoWallpaper(uri, appendToPlaylist)
            refresh()
            onResult(ok)
        }
    }

    fun getActivationIntent(): Intent {
        wallpaperSettings.setLiveWallpaperWanted(true)
        return wallpapersService.getActivationIntent()
    }
}

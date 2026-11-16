package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.VideoWallpaperScalingMode
import de.mm20.launcher2.preferences.VideoWallpaperStartBehavior
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Visual transforms applied by the video wallpaper's GL renderer. */
data class VideoWallpaperTransforms(
    val scalingMode: VideoWallpaperScalingMode,
    val zoom: Float,
    val positionX: Float,
    val positionY: Float,
    val brightness: Float,
    val parallax: Boolean,
    val parallaxStrength: Float,
)

class WallpaperSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val videoPauseOnBatterySaver
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperPauseOnBatterySaver }
            .distinctUntilChanged()

    fun setVideoPauseOnBatterySaver(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperPauseOnBatterySaver = pause)) }
    }

    val videoPauseOnThermalThrottling
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperPauseOnThermalThrottling }
            .distinctUntilChanged()

    fun setVideoPauseOnThermalThrottling(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperPauseOnThermalThrottling = pause)) }
    }

    val videoTransforms
        get() = dataStore.data.map {
            VideoWallpaperTransforms(
                scalingMode = it.videoWallpaper.videoWallpaperScalingMode,
                zoom = it.videoWallpaper.videoWallpaperZoom,
                positionX = it.videoWallpaper.videoWallpaperPositionX,
                positionY = it.videoWallpaper.videoWallpaperPositionY,
                brightness = it.videoWallpaper.videoWallpaperBrightness,
                parallax = it.videoWallpaper.videoWallpaperParallax,
                parallaxStrength = it.videoWallpaper.videoWallpaperParallaxStrength,
            )
        }.distinctUntilChanged()

    fun setVideoScalingMode(mode: VideoWallpaperScalingMode) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperScalingMode = mode)) }
    }

    fun setVideoZoom(zoom: Float) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperZoom = zoom)) }
    }

    fun setVideoPosition(x: Float, y: Float) {
        dataStore.update {
            it.copy(
                videoWallpaper = it.videoWallpaper.copy(
                    videoWallpaperPositionX = x,
                    videoWallpaperPositionY = y,
                )
            )
        }
    }

    fun setVideoBrightness(brightness: Float) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperBrightness = brightness)) }
    }

    fun setVideoParallax(enabled: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperParallax = enabled)) }
    }

    fun setVideoParallaxStrength(strength: Float) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperParallaxStrength = strength)) }
    }

    val videoSpeed
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperSpeed }.distinctUntilChanged()

    fun setVideoSpeed(speed: Float) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperSpeed = speed)) }
    }

    val videoStartBehavior
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperStartBehavior }.distinctUntilChanged()

    fun setVideoStartBehavior(behavior: VideoWallpaperStartBehavior) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperStartBehavior = behavior)) }
    }

    val videoThemeColors
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperThemeColors }.distinctUntilChanged()

    fun setVideoThemeColors(enabled: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperThemeColors = enabled)) }
    }

    val videoPauseOnDesktopMode
        get() = dataStore.data.map { it.videoWallpaper.videoWallpaperPauseOnDesktopMode }.distinctUntilChanged()

    fun setVideoPauseOnDesktopMode(pause: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(videoWallpaperPauseOnDesktopMode = pause)) }
    }

    val liveWallpaperWanted
        get() = dataStore.data.map { it.videoWallpaper.liveWallpaperWanted }.distinctUntilChanged()

    fun setLiveWallpaperWanted(wanted: Boolean) {
        dataStore.update { it.copy(videoWallpaper = it.videoWallpaper.copy(liveWallpaperWanted = wanted)) }
    }

    val textColors
        get() = dataStore.data.map { it.wallpaper.wallpaperTextColors }.distinctUntilChanged()

    fun setTextColors(colors: de.mm20.launcher2.preferences.SystemBarColors) {
        dataStore.update { it.copy(wallpaper = it.wallpaper.copy(wallpaperTextColors = colors)) }
    }

    val dimWallpaper
        get() = dataStore.data.map {
            it.wallpaper.wallpaperDim
        }.distinctUntilChanged()

    fun setDimWallpaper(dimWallpaper: Boolean) {
        dataStore.update {
            it.copy(wallpaper = it.wallpaper.copy(wallpaperDim = dimWallpaper))
        }
    }

    val blurWallpaper
        get() = dataStore.data.map {
            it.wallpaper.wallpaperBlur
        }.distinctUntilChanged()

    fun setBlurWallpaper(blurWallpaper: Boolean) {
        dataStore.update {
            it.copy(wallpaper = it.wallpaper.copy(wallpaperBlur = blurWallpaper))
        }
    }

    val wallpaperBlurRadius
        get() = dataStore.data.map {
            it.wallpaper.wallpaperBlurRadius
        }.distinctUntilChanged()

    fun setWallpaperBlurRadius(wallpaperBlurRadius: Int) {
        dataStore.update {
            it.copy(wallpaper = it.wallpaper.copy(wallpaperBlurRadius = wallpaperBlurRadius))
        }
    }
}

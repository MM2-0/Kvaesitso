package de.mm20.launcher2.ui.theme

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WallpaperColors(
    val primary: Color = Color(0xFF3C6089),
    val secondary: Color? = null,
    val tertiary: Color? = null,
    private val hints: Int = 0,
) {
    val supportsDarkText: Boolean
        get() = hints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0

    companion object {
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        fun fromPlatformType(colors: android.app.WallpaperColors): WallpaperColors {
            return WallpaperColors(
                Color(colors.primaryColor.toArgb()),
                colors.secondaryColor?.toArgb()?.let { Color(it) },
                colors.tertiaryColor?.toArgb()?.let { Color(it) },
                colors.colorHints
            )
        }
    }
}

@Composable
fun wallpaperColorsAsState(): State<WallpaperColors> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = remember { mutableStateOf(WallpaperColors()) }
    if (isAtLeastApiLevel(27)) {
        DisposableEffect(null) {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val callback = WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM == 0) return@OnColorsChangedListener
                if (colors != null) {
                    state.value = WallpaperColors.fromPlatformType(colors)
                } else {
                    state.value = WallpaperColors()
                }
            }
            wallpaperManager.addOnColorsChangedListener(
                callback,
                Handler(Looper.getMainLooper())
            )

            scope.launch {
                val colors = withContext(Dispatchers.IO) {
                    wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                } ?: return@launch
                state.value = WallpaperColors.fromPlatformType(colors)
            }
            onDispose {
                wallpaperManager.removeOnColorsChangedListener(callback)
            }
        }
    }
    return state
}
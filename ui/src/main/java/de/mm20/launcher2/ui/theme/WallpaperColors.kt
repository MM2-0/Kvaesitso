package de.mm20.launcher2.ui.theme

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WallpaperColors(val primary: Color, val secondary: Color?, val tertiary: Color?) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        fun fromPlatformType(colors: android.app.WallpaperColors): WallpaperColors {
            return WallpaperColors(
                Color(colors.primaryColor.toArgb()),
                colors.secondaryColor?.toArgb()?.let { Color(it) },
                colors.tertiaryColor?.toArgb()?.let { Color(it) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun wallpaperColorsAsState(): State<WallpaperColors> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = remember { mutableStateOf(DefaultWallpaperColors) }
    DisposableEffect(null) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val callback = { colors: android.app.WallpaperColors?, which: Int ->
            if (colors != null && which or WallpaperManager.FLAG_SYSTEM != 0) {
                state.value = WallpaperColors.fromPlatformType(colors)
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
    return state
}

internal val DefaultWallpaperColors = WallpaperColors(Color.White, null, null)
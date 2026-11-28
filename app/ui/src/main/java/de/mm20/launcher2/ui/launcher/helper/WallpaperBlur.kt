package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import java.util.function.Consumer

/**
 * Blurs the wallpaper.
 *
 * There are two ways to blur the wallpaper:
 *
 * - *Cross window blur*: The system blurs everything that is behind the launcher window. This
 *   requires the device to support cross window blur, which is an OEM specific feature
 *   (`ro.surface_flinger.supports_background_blur`). It is for example not supported on most
 *   Samsung devices. It is also temporarily disabled by the system, e.g. while battery saver is
 *   enabled.
 * - *In-app blur*: The launcher renders the wallpaper itself and applies a `RenderEffect` to it.
 *   This works on every device running Android 12 or later, but requires access to the wallpaper
 *   image.
 *
 * Whichever mechanism is available on the device is used.
 *
 * @param enabled whether wallpaper blur is enabled at all. When disabled, any blur that is
 * currently applied is removed.
 * @param blurRadius the current blur radius in pixels. A radius of 0 disables the blur.
 * @param dim opacity of a black overlay that is drawn on top of the wallpaper.
 * @param modifier applied to the wallpaper, in case it has to be rendered by the launcher itself.
 */
@Composable
fun WallpaperBlur(
    enabled: Boolean,
    blurRadius: () -> Int,
    dim: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val radius = blurRadius()
    val crossWindowBlurEnabled = rememberCrossWindowBlurEnabled()

    // The system blur has to be removed as soon as it is not used anymore, either because the
    // setting was turned off, or because the device stopped supporting cross window blur.
    SystemWallpaperBlur(if (crossWindowBlurEnabled) radius else 0)

    if (!crossWindowBlurEnabled && enabled) {
        InAppWallpaperBlur(radius, dim, modifier)
    }
}

/**
 * Whether the device currently supports blurring content that is behind the launcher window.
 *
 * This can change at runtime, e.g. when battery saver is enabled or disabled.
 */
@Composable
fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService<WindowManager>() }

    var enabled by remember { mutableStateOf(isCrossWindowBlurEnabled(windowManager)) }

    DisposableEffect(context, windowManager) {
        if (windowManager == null || !isAtLeastApiLevel(31)) return@DisposableEffect onDispose { }

        val listener = Consumer<Boolean> { enabled = it }
        try {
            windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        } catch (e: SecurityException) {
            return@DisposableEffect onDispose { }
        }

        onDispose {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
        }
    }

    return enabled
}

private fun isCrossWindowBlurEnabled(windowManager: WindowManager?): Boolean {
    if (!isAtLeastApiLevel(31)) return false
    return windowManager?.isCrossWindowBlurEnabled == true
}

/**
 * Blurs the wallpaper using the system's cross window blur. The wallpaper itself is drawn by the
 * system, behind the launcher window.
 */
@Composable
private fun SystemWallpaperBlur(blurRadius: Int) {
    if (!isAtLeastApiLevel(31)) return

    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val window = activity.window

    LaunchedEffect(blurRadius) {
        val windowAttributes = window.attributes
        val isBlurEnabled = windowAttributes.flags and
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0

        if (blurRadius > 0) {
            windowAttributes.flags =
                windowAttributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            window.attributes = windowAttributes
            window.setBackgroundBlurRadius(blurRadius)
        } else if (isBlurEnabled) {
            // Only touch the window if the blur is actually applied, to avoid unnecessary
            // relayouts on devices that do not support cross window blur.
            windowAttributes.flags =
                windowAttributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            window.attributes = windowAttributes
            window.setBackgroundBlurRadius(0)
        }
    }
}

/**
 * Blurs the wallpaper by rendering it inside the launcher window and applying a `RenderEffect` to
 * it. This does not require any OEM support, but the launcher has to render the wallpaper itself,
 * which means that live wallpapers are not animated while the wallpaper is blurred.
 */
@Composable
private fun InAppWallpaperBlur(blurRadius: Int, dim: Float, modifier: Modifier = Modifier) {
    // Load the wallpaper eagerly, so that it is ready as soon as the blur kicks in.
    val wallpaper = rememberWallpaperImage()

    if (blurRadius <= 0 || wallpaper == null) return

    val density = LocalDensity.current
    val radius = with(density) { blurRadius.toDp() }

    Box(modifier = modifier) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius),
        )
        if (dim > 0f) {
            // The wallpaper is drawn by the launcher itself, so it has to be dimmed here. The
            // system dims the wallpaper behind the launcher window, which is not possible here.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim))
            )
        }
    }
}

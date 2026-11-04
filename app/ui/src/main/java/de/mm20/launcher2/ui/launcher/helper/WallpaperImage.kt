package de.mm20.launcher2.ui.launcher.helper

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the wallpaper image, so that it does not have to be loaded again every time the launcher
 * is recreated.
 */
private object WallpaperImageCache {
    var wallpaperId: Int? = null
    var image: ImageBitmap? = null
}

/**
 * Loads the current system wallpaper.
 *
 * The wallpaper is loaded asynchronously and cached for as long as the wallpaper does not change.
 *
 * @return the wallpaper image, or null if the wallpaper cannot be read. This is the case if the
 * app does not have permission to access the wallpaper, or if the wallpaper is a live wallpaper
 * that does not provide a thumbnail.
 */
@Composable
fun rememberWallpaperImage(): ImageBitmap? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var image by remember { mutableStateOf(WallpaperImageCache.image) }
    var wallpaperId by remember { mutableStateOf(WallpaperImageCache.wallpaperId) }

    // There is no public listener for wallpaper changes, but the wallpaper id changes whenever
    // the user changes the wallpaper. So the wallpaper is checked whenever the launcher is
    // resumed.
    DisposableEffect(lifecycleOwner) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val id = try {
                wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            } catch (e: SecurityException) {
                return@LifecycleEventObserver
            }
            if (id != wallpaperId) {
                wallpaperId = id
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(wallpaperId) {
        if (wallpaperId == null) return@LaunchedEffect
        if (WallpaperImageCache.wallpaperId == wallpaperId) return@LaunchedEffect
        image = loadWallpaperImage(context)
        WallpaperImageCache.image = image
        WallpaperImageCache.wallpaperId = wallpaperId
    }

    return image
}

/**
 * Checks whether the app is allowed to read the system wallpaper.
 *
 * Since Android 13 the wallpaper may only be read by apps that have been granted access to all
 * files. Before that, read access to external storage was sufficient.
 */
fun canReadWallpaper(context: Context): Boolean {
    return if (isAtLeastApiLevel(30)) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private suspend fun loadWallpaperImage(context: Context): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val wallpaperManager = WallpaperManager.getInstance(context)

        val drawable: Drawable? = try {
            val wallpaperInfo = wallpaperManager.getWallpaperInfo()
            if (wallpaperInfo != null) {
                // Live wallpapers cannot be read via getDrawable(), the system would return the
                // default wallpaper instead of the actual one. Use the thumbnail that the
                // wallpaper provides instead.
                wallpaperInfo.loadThumbnail(context.packageManager)
            } else if (canReadWallpaper(context)) {
                wallpaperManager.getDrawable()
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

        val wallpaper = drawable?.toBitmap() ?: return@withContext null

        scaleToDisplaySize(wallpaper, context).asImageBitmap()
    }

/**
 * Converts a drawable to a bitmap.
 */
private fun Drawable.toBitmap(): Bitmap? {
    if (this is BitmapDrawable) {
        return bitmap
    }

    val width = intrinsicWidth.takeIf { it > 0 } ?: return null
    val height = intrinsicHeight.takeIf { it > 0 } ?: return null

    return try {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        setBounds(0, 0, width, height)
        draw(canvas)
        result
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * Scales the wallpaper down to the size of the display, if it is larger. This saves a significant
 * amount of memory, as wallpapers are usually much higher resolution than the screen.
 */
private fun scaleToDisplaySize(bitmap: Bitmap, context: Context): Bitmap {
    val displayMetrics = context.resources.displayMetrics
    val maxWidth = displayMetrics.widthPixels
    val maxHeight = displayMetrics.heightPixels

    if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
        return bitmap
    }

    if (bitmap.config == Bitmap.Config.HARDWARE) {
        // Hardware bitmaps cannot be scaled, but they live on the GPU, so they are cheap to draw
        // anyway.
        return bitmap
    }

    val scale = minOf(
        maxWidth.toFloat() / bitmap.width,
        maxHeight.toFloat() / bitmap.height,
    )
    val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

    return try {
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } catch (e: IllegalArgumentException) {
        bitmap
    } catch (e: OutOfMemoryError) {
        bitmap
    }
}

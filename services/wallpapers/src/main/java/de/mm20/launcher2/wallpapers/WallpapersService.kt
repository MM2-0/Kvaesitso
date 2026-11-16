package de.mm20.launcher2.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class StaticWallpaperTarget {
    Home,
    Lock,
    Both,
}

class WallpapersService(private val context: Context) {
    val videoGlass = VideoGlass()
    private val glass = GlassWallpaperStore(context.applicationContext)
    val glassRevision = glass.revision
    suspend fun loadGlassWallpaper(radius: Int, widthDp: Int, heightDp: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        try { glass.load(radius, widthDp, heightDp) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
    }


    /**
     * Sets a static image wallpaper for the given target. Home and lock screen can be set
     * independently ([WallpaperManager.FLAG_SYSTEM] / [WallpaperManager.FLAG_LOCK]).
     */
    suspend fun setStaticWallpaper(uri: Uri, target: StaticWallpaperTarget): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (target != StaticWallpaperTarget.Lock) {
                    return@withContext glass.set(uri, if (target == StaticWallpaperTarget.Both)
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val flags = when (target) {
                        StaticWallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
                        StaticWallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
                        StaticWallpaperTarget.Both ->
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    }
                    WallpaperManager.getInstance(context).setStream(stream, null, true, flags)
                    true
                } == true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (e: Exception) {
                CrashReporter.logException(e)
                false
            }
        }
    }

    /**
     * Copies the given video into app-private storage and notifies a running wallpaper engine
     * to reload. If [appendToPlaylist] is true, adds it to the list instead of replacing.
     */
    suspend fun setVideoWallpaper(uri: Uri, appendToPlaylist: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = getVideoDir(context)
            dir.mkdirs()
            val stem = if (appendToPlaylist) "video_${System.currentTimeMillis()}" else "video"
            val tmp = File(dir, "$stem.tmp")
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().buffered(64 * 1024).use { output -> input.copyTo(output, 64 * 1024) }
                } ?: return@withContext false
                if (tmp.length() < 16L) {
                    tmp.delete()
                    return@withContext false
                }
                val gif = GifWallpaperRenderer.isGif(tmp)
                if (gif) {
                    val header = tmp.inputStream().use { stream ->
                        ByteArray(10).also { java.io.DataInputStream(stream).readFully(it) }
                    }
                    gifWallpaperDimensions(header, tmp.length())
                }
                val target = File(dir, stem + extensionFor(uri, gif))
                if (target.exists() && !target.delete()) return@withContext false
                if (!tmp.renameTo(target)) return@withContext false
                if (!appendToPlaylist) {
                    dir.listFiles()?.forEach { file ->
                        if (file != target) file.delete()
                    }
                }
                VideoWallpaperService.videoChanged.tryEmit(Unit)
                WallpaperRestorer.markWanted(context)
                true
            } catch (e: Exception) {
                CrashReporter.logException(e)
                tmp.delete()
                false
            }
        }
    }

    private fun extensionFor(uri: Uri, gif: Boolean): String {
        if (gif) return ".gif"
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val path = uri.lastPathSegment.orEmpty().lowercase()
        return when {
            "webm" in mime || path.endsWith(".webm") -> ".webm"
            "matroska" in mime || path.endsWith(".mkv") -> ".mkv"
            "3gpp" in mime || path.endsWith(".3gp") -> ".3gp"
            else -> ".mp4"
        }
    }

    /**
     * Copies the given image into app-private storage for use as the desktop mode wallpaper -
     * same reasoning as [setVideoWallpaper]: the photo picker's URI grant isn't guaranteed to
     * survive past this process, so the bytes are copied in immediately rather than persisting
     * the picker URI itself. @return the copied file's absolute path, or null on failure.
     */
    suspend fun setDesktopWallpaper(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            val dir = getDesktopWallpaperDir(context)
            dir.mkdirs()
            val target = File(dir, "image")
            val tmp = File(dir, "image.tmp")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                if (!tmp.renameTo(target)) return@withContext null
                target.absolutePath
            } catch (e: Exception) {
                CrashReporter.logException(e)
                tmp.delete()
                null
            }
        }
    }

    suspend fun clearDesktopWallpaper() {
        withContext(Dispatchers.IO) {
            getDesktopWallpaperDir(context).listFiles()?.forEach { it.delete() }
        }
    }

    fun hasVideoWallpaper(): Boolean {
        return getVideoDir(context).listFiles()?.isNotEmpty() == true ||
                @Suppress("DEPRECATION") getVideoFile(context).exists()
    }

    suspend fun clearVideoPlaylist() {
        withContext(Dispatchers.IO) {
            getVideoDir(context).listFiles()?.forEach { it.delete() }
            @Suppress("DEPRECATION") getVideoFile(context).delete()
            VideoWallpaperService.videoChanged.tryEmit(Unit)
            runCatching {
                org.koin.core.context.GlobalContext.get().get<de.mm20.launcher2.preferences.ui.WallpaperSettings>().setLiveWallpaperWanted(false)
            }
        }
    }

    /** True if our video wallpaper service is the currently active system live wallpaper. */
    fun isVideoWallpaperActive(): Boolean {
        val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
        return info.packageName == context.packageName &&
                info.serviceName == VideoWallpaperService::class.java.name
    }

    /**
     * Opens the system live-wallpaper preview for our service. Activation always requires the
     * user to confirm there; there is no API for apps to set a live wallpaper directly.
     */
    fun getActivationIntent(): Intent {
        return Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, VideoWallpaperService::class.java),
            )
        }
    }

    companion object {
        internal fun getVideoDir(context: Context): File {
            return File(context.filesDir, "wallpapers/video_playlist")
        }

        internal fun getDesktopWallpaperDir(context: Context): File {
            return File(context.filesDir, "wallpapers/desktop_image")
        }

        @Deprecated("Use getVideoDir")
        internal fun getVideoFile(context: Context): File {
            return File(context.filesDir, "wallpapers/video")
        }
    }
}

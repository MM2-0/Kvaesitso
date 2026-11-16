package de.mm20.launcher2.wallpapers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.AtomicFile
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.roundToInt

/** Only keeps pixels explicitly supplied to Telos; never reads restricted system wallpaper pixels. */
internal class GlassWallpaperStore(private val context: Context) {
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private val file get() = AtomicFile(File(context.filesDir, "wallpapers/glass.snapshot"))
    private data class Key(val wallpaperId: Int, val radius: Int, val width: Int, val height: Int)
    private val frames = LinkedHashMap<Key, Bitmap>()

    // Caller runs on IO. The same bounded input sets both the actual wallpaper and its snapshot.
    suspend fun set(uri: Uri, flags: Int): Boolean = mutex.withLock {
        val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val source = File.createTempFile("glass-", ".image", directory)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                var n = input.read(buffer)
                while (n >= 0) {
                    currentCoroutineContext().ensureActive()
                    total += n; require(total <= 32L * 1024 * 1024)
                    output.write(buffer, 0, n); n = input.read(buffer)
                }
            } } ?: return@withLock false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val sample = GlassPixels.sampleSize(bounds.outWidth, bounds.outHeight)
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    val scale = minOf(1f, GlassPixels.MAX_EDGE.toFloat() / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).roundToInt()), maxOf(1, (info.size.height * scale).roundToInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { decoded ->
                val orientation = runCatching { android.media.ExifInterface(source.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                val matrix = android.graphics.Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                }
                if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
            }
            requireNotNull(bitmap)
            try {
                currentCoroutineContext().ensureActive()
                val id = source.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, flags) }
                // A wallpaper already accepted by Android stays successful if cache storage fails.
                try {
                    val atomic = file
                    val output = atomic.startWrite()
                    try {
                        val header = DataOutputStream(output)
                        header.writeInt(0x54474c31); header.writeInt(id)
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, header))
                        header.flush(); atomic.finishWrite(output)
                    } catch (_: Exception) { atomic.failWrite(output) }
                } catch (_: java.io.IOException) { }
                frames.clear(); changes.update { it + 1 }
                true
            } finally { bitmap.recycle() }
        } finally { source.delete() }
    }

    /** After an APK update the live wallpaper is often unbound; put the last static image back. */
    suspend fun restoreStaticIfPresent(): Boolean = mutex.withLock {
        val snapshot = try { file.openRead() } catch (_: java.io.FileNotFoundException) { return@withLock false }
        val bitmap = snapshot.use { raw ->
            val input = DataInputStream(raw)
            if (input.readInt() != 0x54474c31) return@withLock false
            input.readInt()
            val bytes = input.readNBytesCompat(2 * 1024 * 1024)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return@withLock false
        try {
            java.io.ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return@withLock false
                WallpaperManager.getInstance(context).setStream(java.io.ByteArrayInputStream(out.toByteArray()), null, true, WallpaperManager.FLAG_SYSTEM)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun load(radiusDp: Int, widthDp: Int, heightDp: Int): Bitmap? = mutex.withLock {
        val manager = WallpaperManager.getInstance(context)
        if (manager.wallpaperInfo != null || radiusDp <= 0) { frames.clear(); return@withLock null }
        val id = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val key = Key(id, radiusDp.coerceIn(0, 100), widthDp, heightDp)
        frames[key]?.let { return@withLock it }
        val snapshot = try { file.openRead() } catch (_: java.io.FileNotFoundException) { frames.clear(); return@withLock null }
        val bitmap = snapshot.use { raw ->
            val input = DataInputStream(raw)
            if (input.readInt() != 0x54474c31 || input.readInt() != id) return@withLock null
            // Validate bounds before allocating even for a damaged private cache.
            val bytes = input.readNBytesCompat(2 * 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..GlassPixels.MAX_EDGE && bounds.outHeight in 1..GlassPixels.MAX_EDGE)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = GlassPixels.BLUR_SAMPLE
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } ?: return@withLock null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val radius = GlassPixels.sourceRadius(radiusDp, bitmap.width, bitmap.height, widthDp, heightDp)
            val result = Bitmap.createBitmap(GlassPixels.blur(pixels, bitmap.width, bitmap.height, radius), bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
            if (manager.wallpaperInfo != null || manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) != id) {
                result.recycle(); return@withLock null
            }
            // At most two immutable frames (glass + optional whole-wallpaper blur), <= 2 MiB.
            frames.keys.filter { it.wallpaperId != id }.forEach { frames.remove(it) }
            if (frames.size >= 2) frames.remove(frames.keys.first())
            frames[key] = result
            // Do not recycle evicted frames: a Compose frame can still be drawing one.
            result
        } finally { bitmap.recycle() }
    }
    private fun DataInputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var n = read(buffer)
        while (n >= 0) { require(output.size() + n <= limit); output.write(buffer, 0, n); n = read(buffer) }
        return output.toByteArray()
    }
}

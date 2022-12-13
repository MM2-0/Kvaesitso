package de.mm20.launcher2.media

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.IOException

object ThumbnailUtilsCompat {
    fun createVideoThumbnail(file: File, size: Size, signal: CancellationSignal? = null): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, size, signal)
            } else {
                ThumbnailUtils.createVideoThumbnail(file.absolutePath,
                        MediaStore.Video.Thumbnails.MICRO_KIND)
            }
        } catch (e: IOException) {
            null
        }
    }
}
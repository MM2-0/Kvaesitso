package dev.intelia.launcher.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.search.Application

/** Bounded, process-local cache. Package revision avoids reusing an updated app's icon. */
internal object InteliaIconCache {
    private val icons = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    @Suppress("DEPRECATION")
    fun load(context: Context, app: Application): ImageBitmap {
        val manager = context.packageManager
        val revision = manager.getPackageInfo(app.componentName.packageName, 0).lastUpdateTime
        val key = "${app.key}:$revision"
        return icons.get(key) ?: manager.getActivityIcon(app.componentName).toBitmap(128, 128)
            .asImageBitmap().also { icons.put(key, it) }
    }
}

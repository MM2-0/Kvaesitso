package de.mm20.launcher2.ui.legacy.helper

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


object WallpaperBlur {
    fun requestBlur(context: Context) {
        val wm = WallpaperManager.getInstance(context)
        val lastId = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
            .getInt("last_wallpaper_id", 0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) != lastId) {
            blurredWallpaper?.takeIf { !it.isRecycled }?.recycle()
            blurredWallpaper = null
            File(context.cacheDir, "wallpaper").takeIf { it.exists() }?.delete()
            if (wm.wallpaperInfo != null) return
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            val wallpaper = wm.drawable.toBitmap()
            Glide.with(context)
                .asBitmap()
                .load(wallpaper)
                .apply(
                    RequestOptions.bitmapTransform(
                        BlurTransformation(
                            (20 * context.dp).toInt(),
                            1
                        )
                    )
                )
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        GlobalScope.launch {
                            withContext(Dispatchers.IO) {
                                val out = FileOutputStream(File(context.cacheDir, "wallpaper"))
                                resource.compress(Bitmap.CompressFormat.PNG, 100, out)
                                out.close()
                                context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
                                    .edit()
                                    .putInt(
                                        "last_wallpaper_id", wm.getWallpaperId(
                                            WallpaperManager.FLAG_SYSTEM
                                        )
                                    )
                                    .apply()

                                if (LauncherPreferences.instance.blurCards && LauncherPreferences.instance.cardOpacity < 0xFF) {
                                    blurredWallpaper = resource
                                } else {
                                    resource.recycle()
                                }
                            }
                        }
                    }

                })
        }
    }

    fun getCachedBitmap(context: Context): Bitmap? {
        return BitmapFactory.decodeFile(File(context.cacheDir, "wallpaper").absolutePath)
    }

    var blurredWallpaper: Bitmap? = null
}

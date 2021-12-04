package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.CalendarDynamicLauncherIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.randomElementOrNull
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Searchable
import kotlin.math.roundToInt

class IconPackIconProvider(val context: Context, val iconPack: String): IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        val res = try {
            context.packageManager.getResourcesForApplication(iconPack)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MM20", "Icon pack package $iconPack not found!")
            return null
        }
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val component = ComponentName(searchable.`package`, searchable.activity)
        val icon = iconDao.getIcon(component.flattenToString(), iconPack)
            ?: return generateIcon(context, searchable.launcherActivityInfo, size)

        if (icon.type == "calendar") {
            return getIconPackCalendarIcon(context, icon.drawable ?: return null)
        }
        val drawableName = icon.drawable
        val resId = res.getIdentifier(drawableName, "drawable", iconPack).takeIf { it != 0 }
            ?: return generateIcon(context, searchable.launcherActivityInfo, size)
        val drawable = ResourcesCompat.getDrawable(res, resId, context.theme) ?: return null
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable -> {
                LauncherIcon(
                    foreground = drawable.foreground,
                    background = drawable.background,
                    foregroundScale = 1.5f,
                    backgroundScale = 1.5f
                )
            }
            else -> {
                LauncherIcon(
                    foreground = drawable,
                    foregroundScale = getScale(),
                    autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
                )
            }
        }
    }

    private fun getScale(): Float {
        return when (LauncherPreferences.instance.iconShape) {
            IconShape.CIRCLE, IconShape.PLATFORM_DEFAULT -> 0.7f
            else -> 0.8f

        }
    }

    private suspend fun generateIcon(
        context: Context,
        activity: LauncherActivityInfo,
        size: Int
    ): LauncherIcon? {
        val back = getIconBack()
        val upon = getIconUpon()
        val mask = getIconMask()
        val scale = getPackScale()

        if (back == null && upon == null && mask == null) {
            return null
        }

        val drawable = activity.getIcon(context.resources.displayMetrics.densityDpi)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true


        var inBounds: Rect
        var outBounds: Rect

        val icon = drawable.toBitmap(width = size, height = size)

        inBounds = Rect(0, 0, icon.width, icon.height)
        outBounds = Rect(
            (bitmap.width * (1 - scale) * 0.5).roundToInt(),
            (bitmap.height * (1 - scale) * 0.5).roundToInt(),
            (bitmap.width - bitmap.width * (1 - scale) * 0.5).roundToInt(),
            (bitmap.height - bitmap.height * (1 - scale) * 0.5).roundToInt()
        )
        canvas.drawBitmap(icon, inBounds, outBounds, paint)

        val pack = iconPack
        val pm = context.packageManager
        val res = try {
            pm.getResourcesForApplication(pack)
        } catch (e: Resources.NotFoundException) {
            return null
        }

        if (mask != null) {
            res.getIdentifier(mask, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                val maskDrawable = ResourcesCompat.getDrawable(res, it, null) ?: return null
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }
        if (upon != null) {
            res.getIdentifier(upon, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER);
                val maskDrawable = ResourcesCompat.getDrawable(res, it, null) ?: return null
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }
        if (back != null) {
            res.getIdentifier(back, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
                val maskDrawable = ResourcesCompat.getDrawable(res, it, null) ?: return null
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }

        return LauncherIcon(
            foreground = BitmapDrawable(context.resources, bitmap),
            foregroundScale = getScale(),
            autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }

    private suspend fun getIconBack(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconbacks = iconDao.getIconBacks(iconPack)
        return iconbacks.randomElementOrNull()
    }

    private suspend fun getIconUpon(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconupons = iconDao.getIconUpons(iconPack)
        return iconupons.randomElementOrNull()
    }

    private suspend fun getIconMask(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconmasks = iconDao.getIconMasks(iconPack)
        return iconmasks.randomElementOrNull()
    }

    private suspend fun getPackScale(): Float {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        return iconDao.getScale(iconPack) ?: 1f
    }

    private fun getIconPackCalendarIcon(
        context: Context,
        baseIconName: String
    ): CalendarDynamicLauncherIcon? {
        val resources = try {
            context.packageManager.getResourcesForApplication(iconPack)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val drawableIds = (1..31).map {
            val drawableName = baseIconName + it
            val id = resources.getIdentifier(drawableName, "drawable", iconPack)
            if (id == 0) return null
            id
        }.toIntArray()
        return CalendarDynamicLauncherIcon(
            foreground = ColorDrawable(0),
            background = ColorDrawable(0),
            foregroundScale = 1.5f,
            backgroundScale = 1.5f,
            packageName = iconPack,
            drawableIds = drawableIds,
            autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }
}
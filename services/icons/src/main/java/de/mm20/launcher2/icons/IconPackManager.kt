package de.mm20.launcher2.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.loaders.CompatThemedIconInstaller
import de.mm20.launcher2.icons.loaders.GrayscaleMapInstaller
import de.mm20.launcher2.icons.loaders.IconPackInstaller
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.ktx.randomElementOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


class IconPackManager(
    private val context: Context,
    private val appDatabase: AppDatabase,
) {
    suspend fun getInstalledIconPacks(): List<IconPack> {
        return withContext(Dispatchers.IO) {
            appDatabase.iconDao().getInstalledIconPacks().map {
                IconPack(it)
            }
        }
    }

    suspend fun getIconPack(packageName: String): IconPack? {
        return withContext(Dispatchers.IO) {
            appDatabase.iconDao().getIconPack(packageName)?.let {
                IconPack(it)
            }
        }
    }

    suspend fun updateIconPacks() {
        withContext(Dispatchers.IO) {
            IconPackInstaller(context, appDatabase).installIcons()
            GrayscaleMapInstaller(context, appDatabase).installIcons()
            CompatThemedIconInstaller(context, appDatabase).installIcons()
        }
    }

    suspend fun getIcon(
        iconPack: String,
        componentName: ComponentName,
        themed: Boolean = false
    ): LauncherIcon? {
        val res = try {
            context.packageManager.getResourcesForApplication(iconPack)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MM20", "Icon pack package $iconPack not found!")
            return null
        }
        val iconDao = appDatabase.iconDao()
        val icon = iconDao.getIcon(componentName.flattenToString(), iconPack)
            ?: return null

        val drawableName = icon.drawable ?: return null

        if (icon.type == "calendar") {
            return getIconPackCalendarIcon(context, iconPack, drawableName, themed)
        }
        val resId = res.getIdentifier(drawableName, "drawable", iconPack).takeIf { it != 0 }
            ?: return null
        val drawable = try {
            ResourcesCompat.getDrawable(res, resId, context.theme) ?: return null
        } catch (e: Resources.NotFoundException) {
            return null
        }
        return when {
            themed && drawable is AdaptiveIconDrawable -> {
                if (isAtLeastApiLevel(33) && drawable.monochrome != null) {
                    return StaticLauncherIcon(
                        foregroundLayer = StaticIconLayer(
                            icon = drawable.monochrome!!,
                            scale = 1f,
                        ),
                        backgroundLayer = ColorLayer(),
                    )
                } else {
                    return StaticLauncherIcon(
                        foregroundLayer = TintedIconLayer(
                            icon = drawable.foreground,
                            scale = 1.5f,
                        ),
                        backgroundLayer = ColorLayer(),
                    )
                }
            }

            drawable is AdaptiveIconDrawable -> {
                return StaticLauncherIcon(
                    foregroundLayer = drawable.foreground?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
                    backgroundLayer = drawable.background?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
                )
            }

            else -> {
                StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = drawable,
                        scale = 1f
                    ),
                    backgroundLayer = TransparentLayer
                )
            }
        }
    }

    suspend fun generateIcon(
        context: Context,
        iconPack: String,
        baseIcon: Drawable,
        size: Int
    ): LauncherIcon? {
        val back = getIconBack(iconPack)
        val upon = getIconUpon(iconPack)
        val mask = getIconMask(iconPack)
        val scale = getPackScale(iconPack)

        if (back == null && upon == null && mask == null) {
            return null
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true


        var inBounds: Rect
        var outBounds: Rect

        val icon = baseIcon.toBitmap(width = size, height = size)

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
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

        if (mask != null) {
            res.getIdentifier(mask, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                val maskDrawable = try {
                    ResourcesCompat.getDrawable(res, it, null) ?: return null
                } catch (e: Resources.NotFoundException) {
                    return null
                }
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }
        if (upon != null) {
            res.getIdentifier(upon, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                val maskDrawable = try {
                    ResourcesCompat.getDrawable(res, it, null) ?: return null
                } catch (e: Resources.NotFoundException) {
                    return null
                }
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }
        if (back != null) {
            res.getIdentifier(back, "drawable", pack).takeIf { it != 0 }?.let {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                val maskDrawable = try {
                    ResourcesCompat.getDrawable(res, it, null) ?: return null
                } catch (e: Resources.NotFoundException) {
                    return null
                }
                val maskBmp = maskDrawable.toBitmap(size, size)
                inBounds = Rect(0, 0, maskBmp.width, maskBmp.height)
                outBounds = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(maskBmp, inBounds, outBounds, paint)
            }
        }

        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                icon = BitmapDrawable(context.resources, bitmap),
                scale = 1f,
            ),
            backgroundLayer = TransparentLayer
        )
    }

    suspend fun getCompatThemedIcon(componentName: ComponentName): LauncherIcon? {
        val iconDao = appDatabase.iconDao()
        val icon = iconDao.getCompatThemedIcon(componentName.flattenToString())
            ?: return null

        val drawableName = icon.drawable ?: return null

        val res = try {
            context.packageManager.getResourcesForApplication(componentName.packageName)
        } catch (e: Resources.NotFoundException) {
            return null
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

        val resourceId = res.getIdentifier(drawableName, null, null)
        val drawable = try {
            ResourcesCompat.getDrawable(res, resourceId, null)
        } catch (e: Resources.NotFoundException) {
            return null
        } ?: return null

        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = drawable,
                scale = 1f,
            ),
            backgroundLayer = ColorLayer()
        )
    }

    suspend fun getAllIconPackIcons(componentName: ComponentName): List<IconPackIcon> {
        val iconDao = appDatabase.iconDao()
        return iconDao.getIconsFromAllPacks(componentName.flattenToString())
            .map { IconPackIcon(it) }
    }

    private suspend fun getIconBack(iconPack: String): String? {
        val iconDao = appDatabase.iconDao()
        val iconbacks = iconDao.getIconBacks(iconPack)
        return iconbacks.randomElementOrNull()
    }

    private suspend fun getIconUpon(iconPack: String): String? {
        val iconDao = appDatabase.iconDao()
        val iconupons = iconDao.getIconUpons(iconPack)
        return iconupons.randomElementOrNull()
    }

    private suspend fun getIconMask(iconPack: String): String? {
        val iconDao = appDatabase.iconDao()
        val iconmasks = iconDao.getIconMasks(iconPack)
        return iconmasks.randomElementOrNull()
    }

    private suspend fun getPackScale(iconPack: String): Float {
        val iconDao = appDatabase.iconDao()
        return iconDao.getScale(iconPack) ?: 1f
    }

    private fun getIconPackCalendarIcon(
        context: Context,
        iconPack: String,
        baseIconName: String,
        themed: Boolean,
    ): DynamicCalendarIcon? {
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
        return DynamicCalendarIcon(
            resources = resources,
            resourceIds = drawableIds,
            isThemed = themed,
        )
    }

    suspend fun getThemedIcon(packageName: String): LauncherIcon? {
        val icon = getGreyscaleIcon(packageName) ?: return null
        val resId = icon.drawable?.toIntOrNull() ?: return null
        try {
            val resources = context.packageManager.getResourcesForApplication(icon.iconPack)
            return getThemedClockIcon(resources, resId) ?: getThemedCalendarIcon(
                resources,
                resId,
                iconProviderPackage = icon.iconPack
            ) ?: getThemedStaticIcon(resources, resId)
        } catch (e: PackageManager.NameNotFoundException) {
            CrashReporter.logException(e)
        }
        return null
    }


    suspend fun getGreyscaleIcon(packageName: String): IconPackIcon? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        return iconDao.getGreyscaleIcon(ComponentName(packageName, packageName).flattenToString())
            ?.let { IconPackIcon(it) }

    }

    private fun getThemedStaticIcon(resources: Resources, resId: Int): LauncherIcon? {
        try {
            val fg = ResourcesCompat.getDrawable(resources, resId, null) ?: return null
            return StaticLauncherIcon(
                foregroundLayer = TintedIconLayer(
                    icon = fg,
                    scale = 0.5f,
                ),
                backgroundLayer = ColorLayer()
            )
        } catch (e: Resources.NotFoundException) {
            return null
        }
    }

    private fun getThemedClockIcon(resources: Resources, resId: Int): LauncherIcon? {
        try {
            val array = resources.obtainTypedArrayOrNull(resId) ?: return null
            var i = 0
            var drawable: LayerDrawable? = null
            var minuteIndex: Int? = null
            var defaultMinute = 0
            var hourIndex: Int? = null
            var defaultHour = 0
            while (i < array.length()) {
                when (array.getString(i)) {
                    "com.android.launcher3.LEVEL_PER_TICK_ICON_ROUND" -> {
                        i++
                        drawable = array.getDrawable(i) as? LayerDrawable
                    }

                    "com.android.launcher3.HOUR_LAYER_INDEX" -> {
                        i++
                        hourIndex = array.getInt(i, -1).takeIf { it != -1 }
                    }

                    "com.android.launcher3.MINUTE_LAYER_INDEX" -> {
                        i++
                        minuteIndex = array.getInt(i, -1).takeIf { it != -1 }
                    }

                    "com.android.launcher3.DEFAULT_HOUR" -> {
                        i++
                        defaultHour = array.getInt(i, 0)
                    }

                    "com.android.launcher3.DEFAULT_MINUTE" -> {
                        i++
                        defaultMinute = array.getInt(i, 0)
                    }
                }
                i++
            }
            if (drawable != null && minuteIndex != null && hourIndex != null) {

                return StaticLauncherIcon(
                    foregroundLayer = TintedClockLayer(
                        sublayers = (0 until drawable.numberOfLayers).map {
                            val drw = drawable.getDrawable(it)
                            if (drw is RotateDrawable) {
                                drw.level = when (it) {
                                    hourIndex -> {
                                        (12 - defaultHour) * 60
                                    }

                                    minuteIndex -> {
                                        (60 - defaultMinute)
                                    }

                                    else -> 0
                                }
                            }
                            ClockSublayer(
                                drawable = drw,
                                role = when (it) {
                                    hourIndex -> ClockSublayerRole.Hour
                                    minuteIndex -> ClockSublayerRole.Minute
                                    else -> ClockSublayerRole.Static
                                }
                            )
                        },
                        scale = 1.5f,
                    ),
                    backgroundLayer = ColorLayer()
                )
            }
        } catch (e: Resources.NotFoundException) {
        }
        return null
    }

    private fun getThemedCalendarIcon(
        resources: Resources,
        resId: Int,
        iconProviderPackage: String
    ): LauncherIcon? {
        try {
            val array = resources.obtainTypedArrayOrNull(resId) ?: return null
            if (array.length() != 31) return null

            return DynamicCalendarIcon(
                resources = resources,
                resourceIds = IntArray(31) {
                    array.getResourceId(it, 0).takeIf { it != 0 } ?: return null
                },
                isThemed = true
            )
        } catch (e: Resources.NotFoundException) {
        }
        return null
    }

    suspend fun searchIconPackIcon(query: String): List<IconPackIcon> {
        val iconDao = appDatabase.iconDao()
        val drawableQuery = query.replace(" ", "_").lowercase()
        return iconDao.searchIconPackIcons(
            drawableQuery = "%$drawableQuery%",
            componentQuery = "%$query%",
            nameQuery = "%$query%",
        ).map {
            IconPackIcon(it)
        }
    }

    suspend fun searchThemedIcons(query: String): List<IconPackIcon> {
        val iconDao = appDatabase.iconDao()
        return iconDao.searchGreyscaleIcons("%$query%").map {
            IconPackIcon(it)
        }
    }

}


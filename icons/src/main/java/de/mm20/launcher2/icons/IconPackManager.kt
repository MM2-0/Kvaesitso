package de.mm20.launcher2.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.ktx.randomElementOrNull
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStreamReader
import kotlin.math.roundToInt


class IconPackManager(
    val context: Context,
    val dynamicIconController: DynamicIconController
) {
    var selectedIconPack: String
        get() {
            return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ICON_PACK, "")!!
        }
        set(value) {
            Log.d("MM20", "Selected icon pack: $value")
            context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ICON_PACK, value)
                .apply()
        }


    fun selectIconPack(iconPack: String) {
        selectedIconPack = iconPack
    }

    fun getIcon(context: Context, activity: LauncherActivityInfo, size: Int): LauncherIcon? {
        if (selectedIconPack.isEmpty()) return getDefaultIcon(context, activity)
        return getFromPack(context, activity, size) ?: generateIcon(context, activity, size)
    }

    private fun getFromPack(
        context: Context,
        activity: LauncherActivityInfo,
        size: Int
    ): LauncherIcon? {
        val res = try {
            context.packageManager.getResourcesForApplication(selectedIconPack)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MM20", "Icon pack package $selectedIconPack not found!")
            return getDefaultIcon(context, activity)
        }
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val component = ComponentName(activity.applicationInfo.packageName, activity.name)
        val icon = iconDao.getIcon(component.flattenToString(), selectedIconPack)
            ?: return generateIcon(context, activity, size)

        if (icon.type == "calendar") {
            return getIconPackCalendarIcon(context, icon.iconPack, icon.drawable ?: return null)?.also {
                dynamicIconController.registerIcon(it)
            }
        }
        val drawableName = icon.drawable
        val resId = res.getIdentifier(drawableName, "drawable", selectedIconPack).takeIf { it != 0 }
            ?: return generateIcon(context, activity, size)
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


    private fun generateIcon(
        context: Context,
        activity: LauncherActivityInfo,
        size: Int
    ): LauncherIcon? {
        val back = getIconBack()
        val upon = getIconUpon()
        val mask = getIconMask()
        val scale = getPackScale()

        if (back == null && upon == null && mask == null) {
            return getDefaultIcon(context, activity)
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

        val pack = selectedIconPack
        val pm = context.packageManager
        val res = try {
            pm.getResourcesForApplication(pack)
        } catch (e: Resources.NotFoundException) {
            return getDefaultIcon(context, activity)
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

    private fun getDefaultIcon(context: Context, activity: LauncherActivityInfo): LauncherIcon? {
        if (activity.applicationInfo.packageName == GOOGLE_DESK_CLOCK_PACKAGE_NAME) {
            getGoogleDeskClockIcon(context)?.let {
                dynamicIconController.registerIcon(it)
                return it
            }
        }
        getCalendarIcon(context, activity)?.let {
            dynamicIconController.registerIcon(it)
            return it
        }
        try {
            val icon = activity.getIcon(context.resources.displayMetrics.densityDpi) ?: return null
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable -> {
                    return LauncherIcon(
                        foreground = icon.foreground ?: return null,
                        background = icon.background,
                        foregroundScale = 1.5f,
                        backgroundScale = 1.5f
                    )
                }
                else -> {
                    return LauncherIcon(
                        foreground = icon,
                        foregroundScale = getScale(),
                        autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
                    )
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    private fun getIconPackCalendarIcon(
        context: Context,
        iconPack: String,
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
            context = context,
            background = ColorDrawable(0),
            foreground = ColorDrawable(0),
            foregroundScale = 1.5f,
            backgroundScale = 1.5f,
            packageName = iconPack,
            drawableIds = drawableIds,
            autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }

    private fun getCalendarIcon(
        context: Context,
        activity: LauncherActivityInfo
    ): CalendarDynamicLauncherIcon? {
        val component = ComponentName(activity.applicationInfo.packageName, activity.name)
        val pm = context.packageManager
        val ai = try {
            pm.getActivityInfo(component, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val resources = pm.getResourcesForActivity(component)
        var arrayId = ai.metaData?.getInt("com.teslacoilsw.launcher.calendarIconArray") ?: 0
        if (arrayId == 0) arrayId = ai.metaData?.getInt("com.google.android.calendar.dynamic_icons")
            ?: return null
        if (arrayId == 0) return null
        val typedArray = resources.obtainTypedArrayOrNull(arrayId) ?: return null
        if (typedArray.length() != 31) {
            typedArray.recycle()
            return null
        }
        val drawableIds = IntArray(31)
        for (i in 0 until 31) {
            drawableIds[i] = typedArray.getResourceId(i, 0)
        }
        typedArray.recycle()
        return CalendarDynamicLauncherIcon(
            context = context,
            background = ColorDrawable(0),
            foreground = ColorDrawable(0),
            foregroundScale = 1.5f,
            backgroundScale = 1.5f,
            packageName = component.packageName,
            drawableIds = drawableIds,
            autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }

    private fun getGoogleDeskClockIcon(context: Context): ClockDynamicLauncherIcon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val pm = context.packageManager
        val appInfo =
            pm.getApplicationInfo(GOOGLE_DESK_CLOCK_PACKAGE_NAME, PackageManager.GET_META_DATA)
                ?: return null
        val drawable =
            appInfo.metaData.getInt("com.google.android.apps.nexuslauncher.LEVEL_PER_TICK_ICON_ROUND")
        val resources = pm.getResourcesForApplication(appInfo)
        val baseIcon = try {
            ResourcesCompat.getDrawable(resources, drawable, null) as? AdaptiveIconDrawable
                ?: return null
        } catch (e: Resources.NotFoundException) {
            return null
        }
        val foreground = baseIcon.foreground as? LayerDrawable ?: return null
        val hourLayer =
            appInfo.metaData.getInt("com.google.android.apps.nexuslauncher.HOUR_LAYER_INDEX")
        val minuteLayer =
            appInfo.metaData.getInt("com.google.android.apps.nexuslauncher.MINUTE_LAYER_INDEX")
        val secondLayer =
            appInfo.metaData.getInt("com.google.android.apps.nexuslauncher.SECOND_LAYER_INDEX")
        return ClockDynamicLauncherIcon(
            context = context,
            background = baseIcon.background,
            backgroundScale = 1.5f,
            foreground = foreground,
            foregroundScale = 1.5f,
            badgeNumber = 0f,
            hourLayer = hourLayer,
            minuteLayer = minuteLayer,
            secondLayer = secondLayer
        )
    }

    private fun getScale(): Float {
        return when (LauncherPreferences.instance.iconShape) {
            IconShape.CIRCLE, IconShape.PLATFORM_DEFAULT -> 0.7f
            else -> 0.8f

        }
    }

    private fun getIconBack(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconbacks = iconDao.getIconBacks(selectedIconPack)
        return iconbacks.randomElementOrNull()
    }

    private fun getIconUpon(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconupons = iconDao.getIconUpons(selectedIconPack)
        return iconupons.randomElementOrNull()
    }

    private fun getIconMask(): String? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        val iconmasks = iconDao.getIconMasks(selectedIconPack)
        return iconmasks.randomElementOrNull()
    }

    private fun getPackScale(): Float {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        return iconDao.getScale(selectedIconPack) ?: 1f
    }

    suspend fun getInstalledIconPacks(): List<IconPack> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).iconDao().getInstalledIconPacks().map {
                IconPack(it)
            }
        }
    }

    companion object {
        const val GOOGLE_DESK_CLOCK_PACKAGE_NAME = "com.google.android.deskclock"
        const val GOOGLE_CALENDAR_PACKAGE_NAME = "com.google.android.calendar"
    }

    @Synchronized
    suspend fun updateIconPacks() {
        withContext(Dispatchers.IO) {
            UpdateIconPacksWorker(context).doWork()
        }
    }
}


class UpdateIconPacksWorker(val context: Context) {

    fun doWork() {
        val packs = loadInstalledPacks(context).map { it.activityInfo.packageName }
        val iconDao = AppDatabase.getInstance(context).iconDao()
        iconDao.uninstallIconPacksExcept(packs)

        for (pack in packs) {
            try {
                val packInfo = context.packageManager.getPackageInfo(pack, 0)
                val iconPack = IconPack(
                    name = packInfo.applicationInfo.loadLabel(context.packageManager).toString(),
                    packageName = pack,
                    version = packInfo.versionName
                )
                //if (iconDao.isInstalled(iconPack)) continue
                installIconPack(iconPack)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
        }
    }

    private fun loadInstalledPacks(context: Context): List<ResolveInfo> {
        val packs = mutableListOf<ResolveInfo>()
        val pm = context.packageManager
        var intent = Intent("org.adw.ActivityStarter.THEMES")
        val adwPacks = pm.queryIntentActivities(intent, 0)
        packs.addAll(adwPacks)
        intent = Intent("com.novalauncher.THEME")
        val novaPacks = pm.queryIntentActivities(intent, 0)
        novaPacks.forEach {
            if (packs.none { p -> p.activityInfo.packageName == it.activityInfo.packageName }) packs.add(
                it
            )
        }
        packs.sortWith(ResolveInfo.DisplayNameComparator(pm))
        return packs
    }

    private fun installIconPack(iconPack: IconPack) {
        val pkgName = iconPack.packageName
        try {
            val res = context.packageManager.getResourcesForApplication(pkgName)
            val parser: XmlPullParser
            var inStream: InputStreamReader? = null
            val xmlId = res.getIdentifier("appfilter", "xml", pkgName)
            if (xmlId != 0) parser = res.getXml(xmlId)
            else {
                val rawId = res.getIdentifier("appfilter", "raw", pkgName)
                if (rawId == 0) {
                    Log.e(
                        "MM20",
                        "Icon pack $pkgName has no appfilter.xml, neither in xml nor in raw"
                    )
                    return
                }
                parser = XmlPullParserFactory.newInstance().newPullParser()
                inStream = res.openRawResource(rawId).reader()
                parser.setInput(inStream)
            }

            val icons = mutableListOf<Icon>()
            val iconDao = AppDatabase.getInstance(context).iconDao()

            loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component")
                            ?: continue@loop
                        val drawable = parser.getAttributeValue(null, "drawable")
                            ?: continue@loop
                        if (component.length <= 14) continue@loop
                        val componentName = ComponentName.unflattenFromString(
                            component.substring(
                                14,
                                component.lastIndex
                            )
                        )
                            ?: continue@loop
                        val icon = Icon(
                            componentName = componentName,
                            drawable = drawable,
                            iconPack = pkgName,
                            type = "app"
                        )
                        icons.add(icon)
                    }
                    "calendar" -> {
                        val component = parser.getAttributeValue(null, "component")
                            ?: continue@loop
                        val drawable = parser.getAttributeValue(null, "prefix") ?: continue@loop
                        if (component.length < 14) continue@loop
                        val componentName = ComponentName.unflattenFromString(
                            component.substring(
                                14,
                                component.lastIndex
                            )
                        )
                            ?: continue@loop

                        val icon = Icon(
                            componentName = componentName,
                            drawable = drawable,
                            iconPack = pkgName,
                            type = "calendar"
                        )
                        icons.add(icon)
                    }
                    "iconback" -> {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).startsWith("img")) {
                                val drawable = parser.getAttributeValue(i)
                                val icon = Icon(
                                    componentName = null,
                                    drawable = drawable,
                                    iconPack = pkgName,
                                    type = "iconback"
                                )
                                icons.add(icon)
                            }
                        }
                    }
                    "iconupon" -> {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).startsWith("img")) {
                                val drawable = parser.getAttributeValue(i)
                                val icon = Icon(
                                    componentName = null,
                                    drawable = drawable,
                                    iconPack = pkgName,
                                    type = "iconupon"
                                )
                                icons.add(icon)
                            }
                        }
                    }
                    "iconmask" -> {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).startsWith("img")) {
                                val drawable = parser.getAttributeValue(i)
                                val icon = Icon(
                                    componentName = null,
                                    drawable = drawable,
                                    iconPack = pkgName,
                                    type = "iconmask"
                                )
                                icons.add(icon)
                            }
                        }
                    }
                    "scale" -> {
                        val scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()
                            ?: continue@loop
                        iconPack.scale = scale
                    }
                }
            }

            iconDao.installIconPack(
                iconPack.toDatabaseEntity(),
                icons.map { it.toDatabaseEntity() })

            (parser as? XmlResourceParser)?.close()
            inStream?.close()

            Log.d("MM20", "Icon pack has been installed successfully")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MM20", "Could not install icon pack $pkgName: package not found.")
        } catch (e: XmlPullParserException) {
            CrashReporter.logException(e)
        }
    }
}

private const val PREFERENCE_NAME = "icon_pack"
private const val KEY_ICON_PACK = "icon_pack"
private const val KEY_VERSION = "version"
private const val KEY_ICONSCALE = "iconscale"
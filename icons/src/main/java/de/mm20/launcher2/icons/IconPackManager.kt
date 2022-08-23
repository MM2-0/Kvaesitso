package de.mm20.launcher2.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.*
import android.graphics.drawable.*
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.ktx.randomElementOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStreamReader
import kotlin.math.roundToInt

private val SUPPORTED_GRAYSCALE_MAP_PROVIDERS = arrayOf(
    "com.google.android.apps.nexuslauncher", // Pixel Launcher
    "app.lawnchair.lawnicons", // Lawnicons
    "app.lawnchair", // Lawnchair
    "de.mm20.launcher2.themedicons",
)


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

    suspend fun updateIconPacks() {
        withContext(Dispatchers.IO) {
            UpdateIconPacksWorker(context).doWork()
        }
    }

    suspend fun getIcon(iconPack: String, componentName: ComponentName): LauncherIcon? {
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
            return getIconPackCalendarIcon(context, iconPack, drawableName)
        }
        val resId = res.getIdentifier(drawableName, "drawable", iconPack).takeIf { it != 0 }
            ?: return null
        val drawable = try {
            ResourcesCompat.getDrawable(res, resId, context.theme) ?: return null
        } catch (e: Resources.NotFoundException) {
            return null
        }
        return when (drawable) {
            is AdaptiveIconDrawable -> {
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
        baseIconName: String
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
            resourceIds = drawableIds
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
        return iconDao.searchIconPackIcons("%$query%").map {
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


class UpdateIconPacksWorker(val context: Context) {

    fun doWork() {
        val packs = loadInstalledPacks(context).map { it.activityInfo.packageName }
        val grayscaleProviders = loadInstalledGreyscaleProviders(context)
        val iconDao = AppDatabase.getInstance(context).iconDao()
        iconDao.uninstallIconPacksExcept(
            packs.union(grayscaleProviders).toList()
        )

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

        val supportedGrayscaleMapPackages = SUPPORTED_GRAYSCALE_MAP_PROVIDERS
        supportedGrayscaleMapPackages.forEach { installGrayscaleIconMap(it) }
    }

    private fun loadInstalledGreyscaleProviders(context: Context): List<String> {
        val pm = context.packageManager
        return SUPPORTED_GRAYSCALE_MAP_PROVIDERS.filter {
            try {
                pm.getPackageInfo(it, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
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

        val icons = mutableListOf<IconPackIcon>()
        val database = AppDatabase.getInstance(context)
        database.runInTransaction {
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
                        return@runInTransaction
                    }
                    parser = XmlPullParserFactory.newInstance().newPullParser()
                    inStream = res.openRawResource(rawId).reader()
                    parser.setInput(inStream)
                }
                val iconDao = database.iconDao()

                iconDao.deleteIconPack(iconPack.toDatabaseEntity())
                iconDao.deleteIcons(iconPack.packageName)

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
                            val icon = IconPackIcon(
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

                            val icon = IconPackIcon(
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
                                    val icon = IconPackIcon(
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
                                    val icon = IconPackIcon(
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
                                    val icon = IconPackIcon(
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
                    if (icons.size >= 100) {
                        iconDao.insertAll(icons.map { it.toDatabaseEntity() })
                        icons.clear()
                    }
                }

                if (icons.isNotEmpty()) {
                    iconDao.insertAll(icons.map { it.toDatabaseEntity() })
                }
                iconDao.installIconPack(iconPack.toDatabaseEntity())

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

    private fun installGrayscaleIconMap(packageName: String) {
        val database = AppDatabase.getInstance(context)
        database.runInTransaction {
            val iconDao = database.iconDao()
            try {
                val resources = context.packageManager.getResourcesForApplication(packageName)
                val resId = resources.getIdentifier("grayscale_icon_map", "xml", packageName)
                iconDao.deleteIcons(packageName)
                if (resId == 0) {
                    return@runInTransaction
                }
                val icons = mutableListOf<IconPackIcon>()
                val parser = resources.getXml(resId)
                loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name) {
                        "icon" -> {
                            val drawable =
                                parser.getAttributeResourceValue(null, "drawable", 0).toString()
                            val pkg = parser.getAttributeValue(null, "package")
                            val componentName = ComponentName(pkg, pkg)
                            val icon = IconPackIcon(
                                drawable = drawable,
                                componentName = componentName,
                                iconPack = packageName,
                                type = "greyscale_icon"
                            )
                            icons.add(icon)
                        }
                    }
                    if (icons.size >= 100) {
                        iconDao.insertAll(icons.map { it.toDatabaseEntity() })
                        icons.clear()
                    }
                }
                if (icons.isNotEmpty()) {
                    iconDao.insertAll(icons.map { it.toDatabaseEntity() })
                }
            } catch (e: PackageManager.NameNotFoundException) {
                iconDao.deleteIcons(packageName)
                return@runInTransaction
            }

        }
    }
}

private const val PREFERENCE_NAME = "icon_pack"
private const val KEY_ICON_PACK = "icon_pack"
private const val KEY_VERSION = "version"
private const val KEY_ICONSCALE = "iconscale"
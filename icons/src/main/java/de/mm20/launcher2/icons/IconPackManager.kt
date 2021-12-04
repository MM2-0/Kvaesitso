package de.mm20.launcher2.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.XmlResourceParser
import android.graphics.*
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStreamReader

private val SUPPORTED_GRAYSCALE_MAP_PROVIDERS = arrayOf(
    "com.google.android.apps.nexuslauncher", // Pixel Launcher
    "app.lawnchair.lawnicons", // Lawnicons
    "app.lawnchair", // Lawnchair
    "de.mm20.launcher2.themedicons",
)


class IconPackManager(
    val context: Context
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

    suspend fun getInstalledIconPacks(): List<IconPack> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).iconDao().getInstalledIconPacks().map {
                IconPack(it)
            }
        }
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

        val supportedGrayscaleMapPackages = SUPPORTED_GRAYSCALE_MAP_PROVIDERS
        supportedGrayscaleMapPackages.forEach { installGrayscaleIconMap(it) }
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

    private fun installGrayscaleIconMap(packageName: String) {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val resId = resources.getIdentifier("grayscale_icon_map", "xml", packageName)
            if (resId == 0) {
                iconDao.deleteIcons(packageName)
                return
            }
            val icons = mutableListOf<Icon>()
            val parser = resources.getXml(resId)
            loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "icon" -> {
                        val drawable =
                            parser.getAttributeResourceValue(null, "drawable", 0).toString()
                        val pkg = parser.getAttributeValue(null, "package")
                        for (i in 0 until parser.attributeCount) {
                            Log.d("MM20", "${parser.getAttributeName(0)}")
                        }
                        val componentName = ComponentName(pkg, pkg)
                        val icon = Icon(
                            drawable = drawable,
                            componentName = componentName,
                            iconPack = packageName,
                            type = "greyscale_icon"
                        )
                        icons.add(icon)
                        Log.d("MM20", "Installed icon ${icon.toString()}")
                    }
                }
            }
            iconDao.installGrayscaleIconMap(packageName, icons.map { it.toDatabaseEntity() })
        } catch (e: PackageManager.NameNotFoundException) {
            iconDao.deleteIcons(packageName)
            return
        }
    }
}

private const val PREFERENCE_NAME = "icon_pack"
private const val KEY_ICON_PACK = "icon_pack"
private const val KEY_VERSION = "version"
private const val KEY_ICONSCALE = "iconscale"
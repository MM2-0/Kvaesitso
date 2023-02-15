package de.mm20.launcher2.icons.loaders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconPackIcon
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.Reader

class IconPackInstaller(
    private val context: Context,
    private val database: AppDatabase,
) {

    fun installIcons() {
        val packs = loadInstalledPacks(context)
        val iconDao = database.iconDao()

        for (pack in packs) {
            try {
                installIconPack(pack)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
        }

        iconDao.uninstallIconPacksExcept(
            packs.map { it.packageName }.toList()
        )
    }

    private fun loadInstalledPacks(context: Context): List<IconPack> {
        val packs = mutableListOf<IconPack>()
        val pm = context.packageManager
        var intent = Intent("app.lawnchair.icons.THEMED_ICON")
        val themedPacks = pm.queryIntentActivities(intent, 0)
        packs.addAll(themedPacks.map { IconPack(context, it, true) })
        intent = Intent("org.adw.ActivityStarter.THEMES")
        val adwPacks = pm.queryIntentActivities(intent, 0)
        packs.addAll(adwPacks.map { IconPack(context, it, false) })
        intent = Intent("com.novalauncher.THEME")
        val novaPacks = pm.queryIntentActivities(intent, 0)
        packs.addAll(novaPacks.map { IconPack(context, it, false) })
        return packs.distinctBy { it.packageName }
    }

    private fun installIconPack(iconPack: IconPack) {
        val pkgName = iconPack.packageName

        val icons = mutableListOf<IconPackIcon>()
        database.runInTransaction {
            try {
                val res = context.packageManager.getResourcesForApplication(pkgName)
                val parser: XmlPullParser
                var inStream: Reader? = null
                val xmlId = res.getIdentifier("appfilter", "xml", pkgName)
                val rawId = res.getIdentifier("appfilter", "raw", pkgName)
                parser = when {
                    xmlId != 0 -> res.getXml(xmlId)
                    rawId != 0 -> {
                        inStream = res.openRawResource(rawId).reader()
                        XmlPullParserFactory.newInstance().newPullParser().apply {
                            setInput(inStream)
                        }
                    }

                    else -> {
                        val iconPackContext = context.createPackageContext(
                            pkgName,
                            Context.CONTEXT_IGNORE_SECURITY
                        )
                        inStream = try {
                            iconPackContext.assets.open("appfilter.xml").reader()
                        } catch (e: IOException) {
                            CrashReporter.logException(e)
                            Log.e(
                                "MM20",
                                "appfilter.xml not found in $pkgName. Searched locations: res/xml/appfilter.xml, res/raw/appfilter.xml, assets/appfilter.xml"
                            )
                            return@runInTransaction
                        }
                        XmlPullParserFactory.newInstance().newPullParser().apply {
                            setInput(inStream)
                        }
                    }
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

                            val name = parser.getAttributeValue(null, "name")

                            val icon = IconPackIcon(
                                componentName = componentName,
                                drawable = drawable,
                                iconPack = pkgName,
                                name = name,
                                themed = iconPack.themed,
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

                            val name = parser.getAttributeValue(null, "name")

                            val icon = IconPackIcon(
                                componentName = componentName,
                                drawable = drawable,
                                iconPack = pkgName,
                                type = "calendar",
                                name = name,
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


}
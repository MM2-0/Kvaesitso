package de.mm20.launcher2.icons.loaders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.AppIcon
import de.mm20.launcher2.icons.CalendarIcon
import de.mm20.launcher2.icons.IconBack
import de.mm20.launcher2.icons.IconMask
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconUpon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.Reader

class AppFilterIconPackInstaller(
    private val context: Context,
    database: AppDatabase,
) : IconPackInstaller(database) {
    override suspend fun IconPackInstallerScope.buildIconPack(iconPack: IconPack) {
        withContext(Dispatchers.IO) {
            val pkgName = iconPack.packageName

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
                            return@withContext
                        }
                        XmlPullParserFactory.newInstance().newPullParser().apply {
                            setInput(inStream)
                        }
                    }
                }

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

                            val icon = AppIcon(
                                packageName = componentName.packageName,
                                activityName = componentName.shortClassName,
                                drawable = drawable,
                                iconPack = pkgName,
                                name = name,
                                themed = iconPack.themed,
                            )
                            addIcon(icon)
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

                            val icon = CalendarIcon(
                                packageName = componentName.packageName,
                                activityName = componentName.shortClassName,
                                drawables = (0..31).map { "$drawable$it" },
                                iconPack = pkgName,
                                themed = iconPack.themed,
                                name = name,
                            )
                            addIcon(icon)
                        }

                        "iconback" -> {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).startsWith("img")) {
                                    val drawable = parser.getAttributeValue(i)
                                    val icon = IconBack(
                                        drawable = drawable,
                                        iconPack = pkgName,
                                    )
                                    addIcon(icon)
                                }
                            }
                        }

                        "iconupon" -> {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).startsWith("img")) {
                                    val drawable = parser.getAttributeValue(i)
                                    val icon = IconUpon(
                                        drawable = drawable,
                                        iconPack = pkgName,
                                    )
                                    addIcon(icon)
                                }
                            }
                        }

                        "iconmask" -> {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).startsWith("img")) {
                                    val drawable = parser.getAttributeValue(i)
                                    val icon = IconMask(
                                        drawable = drawable,
                                        iconPack = pkgName,
                                    )
                                    addIcon(icon)
                                }
                            }
                        }

                        "scale" -> {
                            val scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()
                                ?: continue@loop
                            updatePackInfo { it.copy(scale = scale) }
                        }
                    }
                }
                (parser as? XmlResourceParser)?.close()
                inStream?.close()

                Log.d("MM20", "Icon pack $pkgName has been installed successfully")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("MM20", "Could not install icon pack $pkgName: package not found.")
            } catch (e: XmlPullParserException) {
                CrashReporter.logException(e)
            }
        }
    }

    override fun getInstalledIconPacks(): List<IconPack> {
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
}
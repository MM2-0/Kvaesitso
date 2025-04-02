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
import de.mm20.launcher2.icons.ClockIcon
import de.mm20.launcher2.icons.IconBack
import de.mm20.launcher2.icons.IconMask
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconUpon
import de.mm20.launcher2.icons.compat.ClockIconConfig
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
            try {
                val dynamicClocks = getDynamicClockIcons(iconPack.packageName)

                parseAppfilterXml(iconPack, dynamicClocks)
                parseDrawableXml(iconPack, dynamicClocks)

                Log.d("MM20", "Icon pack ${iconPack.packageName} has been installed successfully")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(
                    "MM20",
                    "Could not install icon pack ${iconPack.packageName}: package not found."
                )
            } catch (e: XmlPullParserException) {
                CrashReporter.logException(e)
            }
        }
    }

    private suspend fun IconPackInstallerScope.parseAppfilterXml(
        iconPack: IconPack,
        dynamicClocks: Map<String, ClockIconConfig>
    ) {
        val pkgName = iconPack.packageName
        val parser = getAppfilterParser(pkgName) ?: return

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


                    val icon = if (dynamicClocks.containsKey(drawable)) {
                        ClockIcon(
                            packageName = componentName.packageName,
                            activityName = componentName.shortClassName,
                            iconPack = pkgName,
                            themed = iconPack.themed,
                            name = name,
                            drawable = drawable,
                            config = dynamicClocks[drawable]!!,
                        )
                    } else {
                        AppIcon(
                            packageName = componentName.packageName,
                            activityName = componentName.shortClassName,
                            drawable = drawable,
                            iconPack = pkgName,
                            name = name,
                            themed = iconPack.themed,
                        )
                    }
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
                        drawables = (1..31).map { "$drawable$it" },
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
        parser.close()
    }

    private suspend fun IconPackInstallerScope.parseDrawableXml(
        iconPack: IconPack,
        dynamicClocks: Map<String, ClockIconConfig>
    ) {
        val res = context.packageManager.getResourcesForApplication(iconPack.packageName)
        val xmlId = res.getIdentifier("drawable", "xml", iconPack.packageName)
        if (xmlId == 0) return
        val parser = res.getXml(xmlId)
        loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "item") {
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue@loop
                val icon = if (dynamicClocks.containsKey(drawable)) {
                    ClockIcon(
                        iconPack = iconPack.packageName,
                        themed = iconPack.themed,
                        drawable = drawable,
                        config = dynamicClocks[drawable]!!,
                    )
                } else {
                    AppIcon(
                        drawable = drawable,
                        iconPack = iconPack.packageName,
                        themed = iconPack.themed,
                    )
                }
                addIcon(icon)
            }
        }
        parser.close()
    }

    private fun getDynamicClockIcons(packageName: String): Map<String, ClockIconConfig> {
        val parser = getAppfilterParser(packageName) ?: return emptyMap()
        val map = mutableMapOf<String, ClockIconConfig>()
        loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "dynamic-clock") {
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue@loop
                val defaultHour = parser.getAttributeValue(null, "defaultHour")?.toIntOrNull() ?: 0
                val defaultMinute =
                    parser.getAttributeValue(null, "defaultMinute")?.toIntOrNull() ?: 0
                val defaultSecond =
                    parser.getAttributeValue(null, "defaultSecond")?.toIntOrNull() ?: 0
                val hourLayerIndex =
                    parser.getAttributeValue(null, "hourLayerIndex")?.toIntOrNull() ?: -1
                val minuteLayerIndex =
                    parser.getAttributeValue(null, "minuteLayerIndex")?.toIntOrNull() ?: -1
                val secondLayerIndex =
                    parser.getAttributeValue(null, "secondLayerIndex")?.toIntOrNull() ?: -1
                map[drawable] = ClockIconConfig(
                    defaultHour = defaultHour,
                    defaultMinute = defaultMinute,
                    defaultSecond = defaultSecond,
                    hourLayer = hourLayerIndex,
                    minuteLayer = minuteLayerIndex,
                    secondLayer = secondLayerIndex,
                )
            }
        }
        parser.close()
        return map
    }

    private fun getAppfilterParser(packageName: String): ClosableXmlParser? {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val xmlId = res.getIdentifier("appfilter", "xml", packageName)
        val rawId = res.getIdentifier("appfilter", "raw", packageName)
        return when {
            xmlId != 0 -> ClosableXmlResourceParser(res.getXml(xmlId))
            rawId != 0 -> {
                val inStream = res.openRawResource(rawId).reader()
                val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(inStream)
                }
                ClosableXmlPullParser(parser, inStream)
            }

            else -> {
                val iconPackContext = context.createPackageContext(
                    packageName,
                    Context.CONTEXT_IGNORE_SECURITY
                )
                val inStream = try {
                    iconPackContext.assets.open("appfilter.xml").reader()
                } catch (e: IOException) {
                    CrashReporter.logException(e)
                    Log.e(
                        "MM20",
                        "appfilter.xml not found in $packageName. Searched locations: res/xml/appfilter.xml, res/raw/appfilter.xml, assets/appfilter.xml"
                    )
                    return null
                }
                val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(inStream)
                }
                ClosableXmlPullParser(parser, inStream)
            }
        }
    }

    override fun getInstalledIconPacks(): List<IconPack> {
        val packs = mutableListOf<IconPack>()
        val pm = context.packageManager

        val intents = listOf(
            Intent("app.lawnchair.icons.THEMED_ICON"),
            Intent("org.adw.ActivityStarter.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("org.adw.launcher.THEMES")
        )

        for (intent in intents) {
            packs.addAll(
                pm.queryIntentActivities(intent, 0).map { IconPack(context, it, true) }
            )
        }
        return packs.distinctBy { it.packageName }
    }
}

internal interface ClosableXmlParser : XmlPullParser {
    fun close()
}

internal class ClosableXmlResourceParser(private val parser: XmlResourceParser) : ClosableXmlParser,
    XmlPullParser by parser {
    override fun close() {
        parser.close()
    }
}

internal class ClosableXmlPullParser(
    private val parser: XmlPullParser,
    private val reader: Reader
) : ClosableXmlParser,
    XmlPullParser by parser {
    override fun close() {
        reader.close()
    }
}
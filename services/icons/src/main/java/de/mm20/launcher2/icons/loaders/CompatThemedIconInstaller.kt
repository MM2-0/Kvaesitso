package de.mm20.launcher2.icons.loaders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.IconPackIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.skipToNextTag
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class CompatThemedIconInstaller(
    private val context: Context,
    private val database: AppDatabase,
) {
    fun installIcons() {
        if (isAtLeastApiLevel(33)) return
        val launcherActivities = getLauncherActivities()

        val dao = database.iconDao()

        val icons = mutableListOf<IconPackIcon>()
        database.runInTransaction {
            dao.deleteAllCompatThemedIcons()
            for (activity in launcherActivities) {
                val componentName = ComponentName(activity.applicationInfo.packageName, activity.name)
                val monochromeIcon = getMonochromeIconResource(activity)

                if (monochromeIcon != null) {
                    val icon = IconPackIcon(
                        type = "themed-compat",
                        componentName = componentName,
                        name = null,
                        drawable = monochromeIcon,
                        iconPack = componentName.packageName
                    )
                    icons.add(icon)
                }

                if (icons.size > 100) {
                    dao.insertAll(icons.map { it.toDatabaseEntity() })
                    icons.clear()
                }
            }
            if (icons.isNotEmpty()) {
                dao.insertAll(icons.map { it.toDatabaseEntity() })
            }
        }
    }

    private fun getMonochromeIconResource(activityInfo: ActivityInfo): String? {
        val iconResource = activityInfo.iconResource
        val resources = try {
            context.packageManager.getResourcesForApplication(activityInfo.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            CrashReporter.logException(e)
            return null
        }
        var xmlParser: XmlResourceParser? = null
        try {
            xmlParser = resources.getXml(iconResource)
            if (!xmlParser.skipToNextTag()) return null

            if (xmlParser.name != "adaptive-icon") {
                return null
            }

            while (xmlParser.skipToNextTag()) {
                if (xmlParser.name == "monochrome") {
                    val drawable = xmlParser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android",
                        "drawable",
                        0
                    )
                    if (drawable == 0) return null
                    return resources.getResourceName(drawable)
                }
            }
        } catch (e: Resources.NotFoundException) {
            CrashReporter.logException(e)
            return null
        } catch (e: IOException) {
            CrashReporter.logException(e)
            return null
        } catch (e: XmlPullParserException) {
            CrashReporter.logException(e)
            return null
        } finally {
            xmlParser?.close()

        }

        return null
    }

    private fun getLauncherActivities(): List<ActivityInfo> {
        val resolveInfos = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )

        return resolveInfos.mapNotNull { it.activityInfo }
    }
}
package de.mm20.launcher2.icons.loaders

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.IconPackIcon
import org.xmlpull.v1.XmlPullParser

class GrayscaleMapInstaller(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val SUPPORTED_GRAYSCALE_MAP_PROVIDERS = arrayOf(
        "com.google.android.apps.nexuslauncher", // Pixel Launcher
        "app.lawnchair.lawnicons", // Lawnicons
        "app.lawnchair", // Lawnchair
        "de.mm20.launcher2.themedicons",
        "de.kvaesitso.icons",
    )

    fun installIcons() {
        val grayscaleProviders = loadInstalledGreyscaleProviders(context)

        val dao = database.iconDao()

        grayscaleProviders.forEach { installGrayscaleIconMap(it) }

        dao.deleteAllGrayscaleIconsExcept(grayscaleProviders)
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

    private fun installGrayscaleIconMap(packageName: String) {
        database.runInTransaction {
            val iconDao = database.iconDao()
            try {
                val resources = context.packageManager.getResourcesForApplication(packageName)
                val resId = resources.getIdentifier("grayscale_icon_map", "xml", packageName)
                iconDao.deleteGrayscaleIcons(packageName)
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
                iconDao.deleteGrayscaleIcons(packageName)
                return@runInTransaction
            }

        }
    }
}
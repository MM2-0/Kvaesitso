package de.mm20.launcher2.icons.loaders

import androidx.room.withTransaction
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.BuildConfig
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconPackComponent

abstract class IconPackInstaller(
    private val database: AppDatabase,
) {
    suspend fun install(iconPack: IconPack) {
        var pack = iconPack
        val dao = database.iconDao()
        database.withTransaction {
            dao.deleteIconPack(iconPack.toDatabaseEntity())
            dao.deleteIcons(iconPack.packageName)
            val icons = mutableListOf<IconPackComponent>()
            val installerScope = object: IconPackInstallerScope {
                override suspend fun addIcon(icon: IconPackComponent) {
                    icons.add(icon)
                    if (icons.size >= 100) {
                        dao.insertAll(icons.map { it.toDatabaseEntity() })
                        icons.clear()
                    }
                }

                override suspend fun updatePackInfo(update: (IconPack) -> IconPack) {
                    pack = update(iconPack)
                }
            }
            installerScope.buildIconPack(iconPack)
            if (icons.isNotEmpty()) dao.insertAll(icons.map { it.toDatabaseEntity() })
            dao.installIconPack(pack.toDatabaseEntity())
        }
    }

    abstract suspend fun IconPackInstallerScope.buildIconPack(iconPack: IconPack)

    abstract fun getInstalledIconPacks(): List<IconPack>

    suspend fun isInstalledAndUpToDate(iconPack: IconPack): Boolean {
        val dao = database.iconDao()
        val installed = dao.getIconPack(iconPack.packageName)?.let { IconPack(it) } ?: return false
        return installed.version == iconPack.version
    }
}

interface IconPackInstallerScope {
    suspend fun addIcon(icon: IconPackComponent)
    suspend fun updatePackInfo(update: (IconPack) -> IconPack)
}
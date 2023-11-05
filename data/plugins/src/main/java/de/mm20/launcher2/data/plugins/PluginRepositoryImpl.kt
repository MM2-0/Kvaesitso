package de.mm20.launcher2.data.plugins

import de.mm20.launcher2.database.daos.PluginDao
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PluginRepositoryImpl(
    private val dao: PluginDao,
): PluginRepository {
    override fun findMany(
        type: PluginType?,
        enabled: Boolean?,
        packageName: String?
    ): Flow<List<Plugin>> {
        return dao.findMany(
            type = type?.name,
            enabled = enabled,
            packageName = packageName,
        ).map {
            it.mapNotNull { Plugin(it) }
        }
    }

    override fun get(authority: String): Flow<Plugin?> {
        return dao.get(authority).map { Plugin(it) }
    }

    override fun insertMany(plugins: List<Plugin>) {
        TODO("Not yet implemented")
    }

    override fun insert(plugin: Plugin) {
        dao.insert(PluginEntity(plugin))
    }

    override fun update(plugin: Plugin) {
        dao.update(PluginEntity(plugin))
    }

    override fun deleteMany() {
        dao.deleteMany()
    }
}
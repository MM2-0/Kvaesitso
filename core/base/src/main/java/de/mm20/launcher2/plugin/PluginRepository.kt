package de.mm20.launcher2.plugin

import kotlinx.coroutines.flow.Flow

interface PluginRepository {
    fun findMany(
        type: PluginType? = null,
        enabled: Boolean? = null,
        packageName: String? = null,
    ): Flow<List<Plugin>>
    fun get(authority: String): Flow<Plugin?>

    fun insertMany(plugins: List<Plugin>)
    fun insert(plugin: Plugin)
    fun update(plugin: Plugin)
    fun deleteMany()
}
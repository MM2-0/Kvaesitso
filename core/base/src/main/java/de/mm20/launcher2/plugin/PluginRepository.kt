package de.mm20.launcher2.plugin

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface PluginRepository {
    fun findMany(
        type: PluginType? = null,
        enabled: Boolean? = null,
        packageName: String? = null,
    ): Flow<List<Plugin>>
    fun get(authority: String): Flow<Plugin?>

    fun insertMany(plugins: List<Plugin>): Job
    fun insert(plugin: Plugin): Job
    fun update(plugin: Plugin): Job
    fun updateMany(plugins: List<Plugin>): Job
    fun deleteMany(): Job
}
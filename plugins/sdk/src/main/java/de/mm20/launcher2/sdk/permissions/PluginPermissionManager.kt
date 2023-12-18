package de.mm20.launcher2.sdk.permissions

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class PluginPermissionManager(
    context: Context,
) {
    private val dataStore = context.applicationContext.permissionsDataStore

    fun hasPermission(pluginPackage: String): Flow<Boolean> {
        return dataStore.data.map { it.contains(pluginPackage) }
    }

    fun grantPermission(pluginPackage: String) {
        runBlocking {
            dataStore.updateData {
                it + pluginPackage
            }
        }
    }

    fun revokePermission(pluginPackage: String) {
        runBlocking {
            dataStore.updateData {
                it - pluginPackage
            }
        }
    }
}
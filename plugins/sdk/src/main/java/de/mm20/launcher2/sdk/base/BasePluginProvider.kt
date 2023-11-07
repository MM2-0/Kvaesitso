package de.mm20.launcher2.sdk.base

import android.content.ContentProvider
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.PluginContract
import kotlinx.coroutines.runBlocking

abstract class BasePluginProvider : ContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            PluginContract.Methods.GetType -> Bundle().apply {
                putString("type", getPluginType().name)
            }

            PluginContract.Methods.GetState -> Bundle().apply {
                val state = runBlocking {
                    getPluginState()
                }

                when (state) {
                    is PluginState.SetupRequired -> {
                        putString("type", "SetupRequired")
                        putString("setupActivity", state.setupActivity)
                        putString("message", state.message)
                    }

                    is PluginState.Ready -> {
                        putString("type", "Ready")
                    }
                }
            }

            else -> super.call(method, arg, extras)
        }
    }

    internal abstract fun getPluginType(): PluginType

    open suspend fun getPluginState(): PluginState {
        return PluginState.Ready
    }

    internal fun checkPermissionOrThrow(context: Context) {
        if (context.checkCallingPermission(PluginContract.Permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        throw SecurityException("Caller does not have permission to use plugins")
    }

}
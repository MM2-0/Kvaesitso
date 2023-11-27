package de.mm20.launcher2.sdk.base

import android.app.PendingIntent
import android.content.ContentProvider
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.PluginContract
import de.mm20.launcher2.sdk.PluginState
import kotlinx.coroutines.runBlocking

abstract class BasePluginProvider : ContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            PluginContract.Methods.GetType -> Bundle().apply {
                putString("type", getPluginType().name)
            }

            PluginContract.Methods.GetState -> {
                val state = runBlocking {
                    getPluginState()
                }

                return state.toBundle()
            }

            PluginContract.Methods.GetConfig -> {
                getPluginConfig()
            }

            else -> super.call(method, arg, extras)
        }
    }

    internal abstract fun getPluginType(): PluginType

    internal open fun getPluginConfig(): Bundle {
        return Bundle.EMPTY
    }

    open suspend fun getPluginState(): PluginState {
        return PluginState.Ready()
    }

    internal fun checkPermissionOrThrow(context: Context) {
        if (context.checkCallingPermission(PluginContract.Permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        throw SecurityException("Caller does not have permission to use plugins")
    }

    private fun PluginState.toBundle(): Bundle {
        when (this) {
            is PluginState.Ready -> {
                return Bundle().apply {
                    putString("type", "Ready")
                    putString("text", text)
                }
            }

            is PluginState.SetupRequired -> {
                val requestCode = (this::class.qualifiedName + "-setup").hashCode()
                return Bundle().apply {
                    putString("type", "SetupRequired")
                    putParcelable(
                        "setupActivity",
                        PendingIntent.getActivity(
                            context,
                            requestCode,
                            setupActivity,
                            PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                    putString("message", message)
                }
            }
        }
    }
}
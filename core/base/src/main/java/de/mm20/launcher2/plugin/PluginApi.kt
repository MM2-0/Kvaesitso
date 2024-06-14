package de.mm20.launcher2.plugin

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.contracts.PluginContract

class PluginApi(
    private val pluginAuthority: String,
    private val contentResolver: ContentResolver,
) {
    fun getConfig(): Bundle? {
        val configBundle = try {
            contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(pluginAuthority)
                    .build(),
                PluginContract.Methods.GetConfig,
                null,
                null
            ) ?: return null
        } catch (e: Exception) {
            Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
            return null
        }
        return configBundle
    }

    fun getSearchPluginConfig(): QueryPluginConfig? {
        val configBundle = try {
            contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(pluginAuthority)
                    .build(),
                PluginContract.Methods.GetConfig,
                null,
                null
            ) ?: return null
        } catch (e: Exception) {
            Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
            return null
        }

        return QueryPluginConfig(configBundle)
    }

    fun getWeatherPluginConfig(): WeatherPluginConfig? {
        val configBundle = try {
            contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(pluginAuthority)
                    .build(),
                PluginContract.Methods.GetConfig,
                null,
                null
            ) ?: return null
        } catch (e: Exception) {
            Log.e("PluginWeatherProvider", "Plugin $pluginAuthority threw exception", e)
            return null
        }

        return WeatherPluginConfig(configBundle)
    }
}
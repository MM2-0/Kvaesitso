import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.PluginType

class PluginScanner(
    private val context: Context,
) {
    suspend fun findPlugins(): List<Plugin> {
        val contentResolvers = context.packageManager.queryIntentContentProviders(
            Intent("de.mm20.launcher2.action.PLUGIN"),
            PackageManager.GET_META_DATA,
        )
        val plugins = mutableListOf<Plugin>()

        for (cr in contentResolvers) {
            try {
                val providerInfo = cr.providerInfo ?: continue
                val authority = providerInfo.authority ?: continue
                val bundle = context.contentResolver.call(
                    Uri.Builder()
                        .scheme("content")
                        .authority(authority)
                        .build(),
                    "getType",
                    null,
                    null,
                ) ?: continue
                val type = bundle.getString("type")
                    ?.let {
                        try {
                            PluginType.valueOf(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    } ?: continue
                plugins.add(
                    Plugin(
                        label = cr.loadLabel(context.packageManager).toString(),
                        description = providerInfo.metaData?.getString("de.mm20.launcher2.plugin.description"),
                        packageName = providerInfo.packageName,
                        className = providerInfo.name,
                        type = type,
                        authority = authority,
                        settingsActivity = providerInfo.metaData?.getString("de.mm20.launcher2.plugin.settings"),
                        enabled = false,
                    )
                )
            } catch (e: SecurityException) {
                continue
            } catch (e: Exception) {
                CrashReporter.logException(e)
                continue
            }
        }

        return plugins
    }
}
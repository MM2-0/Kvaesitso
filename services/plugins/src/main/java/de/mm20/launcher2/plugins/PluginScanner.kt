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
                            Log.e("MM20", "Invalid plugin type: $it")
                            null
                        }
                    } ?: continue
                plugins.add(
                    Plugin(
                        label = providerInfo.metaData?.getString("de.mm20.launcher2.plugin.label")
                            ?: cr.loadLabel(context.packageManager).toString(),
                        description = providerInfo.metaData?.getString("de.mm20.launcher2.plugin.description"),
                        packageName = providerInfo.packageName,
                        className = providerInfo.name,
                        type = type,
                        authority = authority,
                        enabled = false,
                    )
                )
            } catch (e: SecurityException) {
                CrashReporter.logException(e)
                continue
            } catch (e: Exception) {
                CrashReporter.logException(e)
                continue
            }
        }

        return plugins
    }
}
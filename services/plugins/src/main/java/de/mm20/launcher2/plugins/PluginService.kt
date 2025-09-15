package de.mm20.launcher2.plugins

import PluginScanner
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import de.mm20.launcher2.ktx.getDrawableOrNull
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.PluginPackage
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.PluginContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class PluginWithState(
    val plugin: Plugin,
    val state: PluginState?,
)

interface PluginService {
    fun enablePluginPackage(plugin: PluginPackage)
    fun disablePluginPackage(plugin: PluginPackage)
    fun getPluginsWithState(
        type: PluginType? = null,
        enabled: Boolean? = null,
    ): Flow<List<PluginWithState>>

    /**
     * Get a plugin with its current state or null if the plugin is not found.
     */
    fun getPluginWithState(
        authority: String,
    ): Flow<PluginWithState?>

    fun getPluginPackages(): Flow<List<PluginPackage>>
    fun getPluginPackage(packageName: String): Flow<PluginPackage?>
    suspend fun getPluginState(plugin: Plugin): PluginState?

    suspend fun getPluginPackageIcon(plugin: PluginPackage): Drawable?

    suspend fun getPluginIcon(plugin: Plugin): Drawable?
    fun uninstallPluginPackage(context: Context, plugin: PluginPackage)
}

internal class PluginServiceImpl(
    private val context: Context,
    private val repository: PluginRepository,
) : PluginService {

    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Default)

    private val mutex = Mutex()

    init {
        refreshPlugins()
        context.registerReceiver(AppUpdateReceiver(), IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun enablePluginPackage(plugin: PluginPackage) {
        repository.updateMany(
            plugin.plugins.map {
                it.copy(enabled = true)
            }
        )
    }

    override fun disablePluginPackage(plugin: PluginPackage) {
        repository.updateMany(
            plugin.plugins.map {
                it.copy(enabled = false)
            }
        )
    }

    private fun refreshPlugins() {
        Log.d("PluginService", "Refreshing plugins")
        scope.launch {
            mutex.withLock {
                val enabledPluginPackages =
                    repository.findMany(enabled = true).first().map { it.packageName }.distinct()
                val scanner = PluginScanner(context)
                val plugins = scanner.findPlugins().map {
                    if (it.packageName in enabledPluginPackages) {
                        it.copy(enabled = true)
                    } else {
                        it
                    }
                }
                repository.deleteMany().join()
                repository.insertMany(plugins).join()
                Log.d("PluginService", "${plugins.size} plugins found.")
            }
            Log.d("PluginService", "done.")
        }
    }

    override fun getPluginsWithState(
        type: PluginType?,
        enabled: Boolean?,
    ): Flow<List<PluginWithState>> {
        return repository.findMany(
            type = type,
            enabled = enabled,
        ).map {
            it.map {
                PluginWithState(
                    plugin = it,
                    state = getPluginState(it),
                )
            }
        }
    }

    override fun getPluginWithState(authority: String): Flow<PluginWithState?> {
        return repository.get(authority).map {
            it?.let {
                PluginWithState(
                    plugin = it,
                    state = getPluginState(it),
                )
            }
        }
    }

    override suspend fun getPluginState(plugin: Plugin): PluginState {
        val bundle =
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.call(
                        Uri.Builder()
                            .scheme("content")
                            .authority(plugin.authority)
                            .build(),
                        PluginContract.Methods.GetState,
                        null,
                        null
                    )
                } ?: return PluginState.Error
            } catch (e: SecurityException) {
                return PluginState.NoPermission
            } catch (e: IllegalArgumentException) {
                return PluginState.Error
            }
        return PluginState.fromBundle(bundle) ?: PluginState.Error
    }

    override suspend fun getPluginIcon(plugin: Plugin): Drawable? {
        return withContext(Dispatchers.IO) {
            val info = try {
                context.packageManager.getProviderInfo(
                    ComponentName(
                        plugin.packageName,
                        plugin.className
                    ), 0
                )
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
            info.loadIcon(context.packageManager)
                ?: info.applicationInfo?.loadIcon(context.packageManager)
        }
    }

    override suspend fun getPluginPackageIcon(plugin: PluginPackage): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                val appInfo = context.packageManager.getApplicationInfo(plugin.packageName, PackageManager.GET_META_DATA)
                val iconRes = appInfo.metaData?.getInt("de.mm20.launcher2.plugin.icon") ?: 0
                if (iconRes != 0) {
                    val icon = context.packageManager.getResourcesForApplication(plugin.packageName).getDrawableOrNull(iconRes, null)
                    if (icon != null) {
                        return@withContext icon
                    }
                }
                context.packageManager.getApplicationIcon(
                    plugin.packageName
                )
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
        }
    }

    override fun getPluginPackages(): Flow<List<PluginPackage>> {
        return repository.findMany().map {
            val packageGroups = it.groupBy { it.packageName }
            packageGroups.mapNotNull { (packageName, plugins) ->
                val appInfo = try {
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                val settingsActivity = context.packageManager.queryIntentActivities(
                    Intent().apply {
                        `package` = packageName
                        action = "de.mm20.launcher2.action.PLUGIN_SETTINGS"
                    },
                    0
                ).firstOrNull()
                val signature = getSignature(packageName)
                val author = appInfo.metaData?.getString("de.mm20.launcher2.plugin.author")
                PluginPackage(
                    packageName = packageName,
                    label = appInfo.metaData?.getString("de.mm20.launcher2.plugin.label")
                        ?: appInfo.loadLabel(context.packageManager).toString(),
                    description = appInfo.metaData?.getString("de.mm20.launcher2.plugin.description"),
                    author = author,
                    plugins = plugins,
                    settings = settingsActivity?.let {
                        Intent().apply {
                            component =
                                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                        }
                    },
                    isVerified = VERIFIED_PLUGIN_SIGNATURES[author]?.contains(signature) == true,
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun getPluginPackage(packageName: String): Flow<PluginPackage?> {
        val appInfo = try {
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            return flowOf(null)
        }
        val settingsActivityInfo = context.packageManager.queryIntentActivities(
            Intent().apply {
                `package` = packageName
                action = "de.mm20.launcher2.action.PLUGIN_SETTINGS"
            },
            0
        ).firstOrNull()
        val signature = getSignature(packageName)
        val author = appInfo.metaData?.getString("de.mm20.launcher2.plugin.author")
        return repository.findMany(packageName = packageName)
            .map {
                PluginPackage(
                    packageName = packageName,
                    label = appInfo.metaData?.getString("de.mm20.launcher2.plugin.label")
                        ?: appInfo.loadLabel(context.packageManager).toString(),
                    description = appInfo.metaData?.getString("de.mm20.launcher2.plugin.description"),
                    author = author,
                    plugins = it,
                    settings = settingsActivityInfo?.let {
                        Intent().apply {
                            component =
                                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                        }
                    },
                    isVerified = VERIFIED_PLUGIN_SIGNATURES[author]?.contains(signature) == true,
                )
            }
            .flowOn(Dispatchers.Default)
    }

    private fun getSignature(packageName: String): String? {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pi = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            pi.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            val pi = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            pi.signatures?.firstOrNull()
        }
        return if (signature != null) {
            val digest = MessageDigest.getInstance("SHA")
            digest.update(signature.toByteArray())
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } else null
    }

    private inner class AppUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshPlugins()
        }
    }

    override fun uninstallPluginPackage(context: Context, plugin: PluginPackage) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:${plugin.packageName}")
        context.tryStartActivity(intent)
    }

    companion object {
        private val VERIFIED_PLUGIN_SIGNATURES = mapOf(
            "MM2-0" to setOf("rx1fSnL7r5/OMoFC0e1KPqTndXQ=")
        )
    }
}
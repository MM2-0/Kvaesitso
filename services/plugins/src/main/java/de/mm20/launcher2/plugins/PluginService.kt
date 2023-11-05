package de.mm20.launcher2.plugins

import PluginScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.PluginContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PluginWithState(
    val plugin: Plugin,
    val state: PluginState?,
)

interface PluginService {
    fun enablePlugin(plugin: Plugin)
    fun disablePlugin(plugin: Plugin)
    fun getPluginsWithState(type: PluginType? = null): Flow<List<PluginWithState>>
    suspend fun getPluginState(plugin: Plugin): PluginState?
}

internal class PluginServiceImpl(
    private val context: Context,
    private val repository: PluginRepository,
) : PluginService {

    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Default)

    init {
        refreshPlugins()
        ContextCompat.registerReceiver(
            context,
            AppUpdateReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun enablePlugin(plugin: Plugin) {
        repository.update(plugin.copy(enabled = true))
    }

    override fun disablePlugin(plugin: Plugin) {
        repository.update(plugin.copy(enabled = false))
    }

    private val mutex = Mutex()
    private fun refreshPlugins() {
        scope.launch {
            mutex.withLock {
                val enabledPlugins =
                    repository.findMany(enabled = true).first().map { it.authority }
                val scanner = PluginScanner(context)
                val plugins = scanner.findPlugins().map {
                    if (it.authority in enabledPlugins) {
                        it.copy(enabled = true)
                    } else {
                        it
                    }
                }
                repository.deleteMany()
                repository.insertMany(plugins)
            }
        }
    }

    override fun getPluginsWithState(
        type: PluginType?,
    ): Flow<List<PluginWithState>> {
        return repository.findMany(
            type = type,
        ).map {
            it.map {
                PluginWithState(
                    plugin = it,
                    state = getPluginState(it),
                )
            }
        }
    }

    override suspend fun getPluginState(plugin: Plugin): PluginState? {
        val bundle = withContext(Dispatchers.IO) {
            context.contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(plugin.authority)
                    .build(),
                PluginContract.Methods.GetState,
                null,
                null
            )
        } ?: return null
        val type = bundle.getString("type") ?: return null
        return when (type) {
            "Ready" -> PluginState.Ready
            "SetupRequired" -> {
                val setupActivity = bundle.getString("setupActivity") ?: return null
                val message = bundle.getString("message")
                PluginState.SetupRequired(
                    setupActivity = setupActivity,
                    message = message,
                )
            }

            else -> null
        }
    }

    private inner class AppUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshPlugins()
        }
    }
}
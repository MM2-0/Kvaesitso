package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.FileProvider
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.files.providers.PluginFileProvider
import de.mm20.launcher2.files.settings.FileSearchSettings
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class FileRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: FileSearchSettings,
    private val pluginRepository: PluginRepository,
) : SearchableRepository<File> {

    private val nextcloudClient by lazy {
        NextcloudApiHelper(context)
    }
    private val owncloudClient by lazy {
        OwncloudClient(context)
    }

    override fun search(
        query: String,
    ) = channelFlow {
        if (query.isBlank()) {
            send(persistentListOf())
            return@channelFlow
        }

        val filePlugins = pluginRepository.findMany(
            type = PluginType.FileSearch,
            enabled = true,
        )

        settings.data.collectLatest { settings ->
                val providers = mutableListOf<FileProvider>()

                if (settings.localFiles) providers.add(
                    LocalFileProvider(
                        context,
                        permissionsManager
                    )
                )
                if (settings.gdriveFiles) providers.add(GDriveFileProvider(context))
                if (settings.nextcloudFiles) providers.add(NextcloudFileProvider(nextcloudClient))
                if (settings.owncloudFiles) providers.add(OwncloudFileProvider(owncloudClient))

                for (plugin in settings.plugins) {
                    providers.add(PluginFileProvider(context, plugin))
                }

                if (providers.isEmpty()) {
                    send(persistentListOf())
                    return@collectLatest
                }
                val results = mutableListOf<File>()
                for (provider in providers) {
                    results.addAll(provider.search(query))
                    send(results.toImmutableList())
                }
            }
    }
}
package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.files.providers.PluginFileProvider
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

internal class FileRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: FileSearchSettings,
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

        settings.enabledProviders.collectLatest { providerIds ->
                val providers = providerIds.map {
                    when (it) {
                        "local" -> LocalFileProvider(context, permissionsManager)
                        "gdrive" -> GDriveFileProvider(context)
                        "nextcloud" -> NextcloudFileProvider(nextcloudClient)
                        "owncloud" -> OwncloudFileProvider(owncloudClient)
                        else -> PluginFileProvider(context, it)
                    }
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
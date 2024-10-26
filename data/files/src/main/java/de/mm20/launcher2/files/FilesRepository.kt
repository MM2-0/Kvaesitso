package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.files.providers.PluginFileProvider
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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
        allowNetwork: Boolean,
    ): Flow<ImmutableList<File>> {
        if (query.isBlank()) {
            return flowOf(persistentListOf())
        }

        val hasPermission = permissionsManager.hasPermission(PermissionGroup.ExternalStorage)


        return combineTransform(settings.enabledProviders, hasPermission) { providerIds, permission ->
            emit(persistentListOf())
            if (providerIds.isEmpty()) {
                return@combineTransform
            }
            val providers = providerIds.mapNotNull {
                when (it) {
                    "local" -> if (permission) LocalFileProvider(
                        context,
                        permissionsManager
                    ) else null

                    "gdrive" -> GDriveFileProvider(context)
                    "nextcloud" -> NextcloudFileProvider(nextcloudClient)
                    "owncloud" -> OwncloudFileProvider(owncloudClient)
                    else -> PluginFileProvider(context, it)
                }
            }

            supervisorScope {
                val result = MutableStateFlow(persistentListOf<File>())

                for (provider in providers) {
                    launch {
                        val r = provider.search(
                            query,
                            allowNetwork,
                        )
                        result.update {
                            (it + r).toPersistentList()
                        }
                    }
                }
                emitAll(result)
            }

        }
    }
}
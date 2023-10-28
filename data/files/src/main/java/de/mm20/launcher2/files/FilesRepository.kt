package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.FileProvider
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionsManager
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
import kotlinx.coroutines.flow.map

internal class FileRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val dataStore: LauncherDataStore,
) : SearchableRepository<File> {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

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

        dataStore.data.map { it.fileSearch }.collectLatest {
            val providers = mutableListOf<FileProvider>()

            if (it.localFiles) providers.add(LocalFileProvider(context, permissionsManager))
            if (it.gdrive) providers.add(GDriveFileProvider(context))
            if (it.nextcloud) providers.add(NextcloudFileProvider(nextcloudClient))
            if (it.owncloud) providers.add(OwncloudFileProvider(owncloudClient))

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
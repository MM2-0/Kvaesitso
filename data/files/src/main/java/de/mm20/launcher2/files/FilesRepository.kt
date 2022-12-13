package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.FileProvider
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OneDriveFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.File
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

interface FileRepository {
    fun search(
        query: String,
        local: Boolean = true,
        gdrive: Boolean = true,
        onedrive: Boolean = true,
        nextcloud: Boolean = true,
        owncloud: Boolean = true,
    ): Flow<ImmutableList<File>>

    fun deleteFile(file: File)
}

internal class FileRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) : FileRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val nextcloudClient by lazy {
        NextcloudApiHelper(context)
    }
    private val owncloudClient by lazy {
        OwncloudClient(context)
    }

    override fun search(
        query: String,
        local: Boolean,
        gdrive: Boolean,
        onedrive: Boolean,
        nextcloud: Boolean,
        owncloud: Boolean
    ) = channelFlow {
        if (query.isBlank()) {
            send(persistentListOf())
            return@channelFlow
        }

        val providers = mutableListOf<FileProvider>()

        if (local) providers.add(LocalFileProvider(context, permissionsManager))
        if (gdrive) providers.add(GDriveFileProvider(context))
        if (onedrive) providers.add(OneDriveFileProvider(context))
        if (nextcloud) providers.add(NextcloudFileProvider(nextcloudClient))
        if (owncloud) providers.add(OwncloudFileProvider(owncloudClient))

        if (providers.isEmpty()) {
            send(persistentListOf())
            return@channelFlow
        }
        val results = mutableListOf<File>()
        for (provider in providers) {
            results.addAll(provider.search(query))
            send(results.toImmutableList())
        }
    }

    override fun deleteFile(file: File) {
        scope.launch {
            if (file.isDeletable) {
                file.delete(context)
            }
        }
    }
}
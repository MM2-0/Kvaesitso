package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.*
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

interface FileRepository {
    fun search(query: String): Flow<List<File>>
    fun deleteFile(file: File)
}

internal class FileRepositoryImpl(
    private val context: Context,
    private val dataStore: LauncherDataStore,
    private val permissionsManager: PermissionsManager,
) : FileRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)


    private val providers = MutableStateFlow<List<FileProvider>>(emptyList())

    private val nextcloudClient by lazy {
        NextcloudApiHelper(context)
    }
    private val owncloudClient by lazy {
        OwncloudClient(context)
    }

    init {
        scope.launch {
            dataStore.data.map { it.fileSearch }.distinctUntilChanged().collectLatest {
                val provs = mutableListOf<FileProvider>()
                if (it.localFiles) {
                    provs += LocalFileProvider(context, permissionsManager)
                }
                if (it.nextcloud) {
                    provs += NextcloudFileProvider(nextcloudClient)
                }
                if (it.owncloud) {
                    provs += OwncloudFileProvider(owncloudClient)
                }
                if (it.gdrive) {
                    provs += GDriveFileProvider(context)
                }
                if (it.onedrive) {
                    provs += OneDriveFileProvider(context)
                }
                providers.value = provs
            }
        }
    }

    override fun search(query: String): Flow<List<File>> = channelFlow {
        if (query.isBlank()) {
            send(emptyList())
            return@channelFlow
        }

        providers.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(emptyList())
                return@collectLatest
            }
            val results = mutableListOf<File>()
            for (provider in providers) {
                results.addAll(provider.search(query))
                send(results.toList())
            }
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
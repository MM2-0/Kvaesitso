package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.files.providers.*
import de.mm20.launcher2.files.providers.GDriveFileProvider
import de.mm20.launcher2.files.providers.LocalFileProvider
import de.mm20.launcher2.files.providers.NextcloudFileProvider
import de.mm20.launcher2.files.providers.OwncloudFileProvider
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface FileRepository {
    fun search(query: String): Flow<List<File>>
    suspend fun deleteFile(file: File)
}

internal class FileRepositoryImpl(
    private val context: Context,
    hiddenItemsRepository: HiddenItemsRepository,
    private val dataStore: LauncherDataStore
) : FileRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val hiddenItems = hiddenItemsRepository.hiddenItemsKeys

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
                    provs += LocalFileProvider(context)
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

        //TODO SearchListView crashes if we send too many updates at once. Rewrite this code
        // once SearchListView has been replaced with a Jetpack Compose version of itself
        providers.collectLatest { providers ->
            hiddenItems.collectLatest { hiddenItems ->
                if (providers.first() is LocalFileProvider) {
                    val localFiles = providers.first().takeIf { it is LocalFileProvider }?.search(query) ?: emptyList()
                    delay(300)
                    if (providers.size > 1) {
                        val cloudFiles = providers.subList(1, providers.size).map {
                            async { it.search(query) }
                        }.awaitAll().flatten()
                        send(localFiles + cloudFiles)
                    }
                } else {
                    val files = providers.map {
                        async { it.search(query) }
                    }.awaitAll().flatten()
                    send(files)
                }
            }
        }

        /*hiddenItems.collectLatest { hiddenItems ->
            val files = mutableListOf<File>()

            val localFiles = withContext(Dispatchers.IO) {
                LocalFile.search(context, query).sorted().filter { !hiddenItems.contains(it.key) }
            }
            files.addAll(localFiles)
            send(localFiles)

            val cloudFiles = withContext(Dispatchers.IO) {
                delay(300)
                listOf(
                    async { OneDriveFile.search(context, query) },
                    async { GDriveFile.search(context, query) },
                    async { NextcloudFile.search(context, query, nextcloudClient) },
                    async { OwncloudFile.search(context, query, owncloudClient) }
                ).awaitAll().flatten()
            }
            yield()
            files.addAll(cloudFiles.filter { !hiddenItems.contains(it.key) })
            send(files)
        }*/
    }

    override suspend fun deleteFile(file: File) {
        if (file.isDeletable) {
            file.delete(context)
        }
    }
}
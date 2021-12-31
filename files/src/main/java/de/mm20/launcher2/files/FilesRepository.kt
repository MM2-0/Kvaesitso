package de.mm20.launcher2.files

import android.content.Context
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.search.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

interface FileRepository {
    fun search(query: String): Flow<List<File>>
    suspend fun deleteFile(file: File)
}

class FileRepositoryImpl(
    private val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : FileRepository {

    private val hiddenItems = hiddenItemsRepository.hiddenItemsKeys

    private val nextcloudClient by lazy {
        NextcloudApiHelper(context)
    }
    private val owncloudClient by lazy {
        OwncloudClient(context)
    }

    override fun search(query: String): Flow<List<File>> = channelFlow {
        if (query.isBlank()) {
            send(emptyList())
            return@channelFlow
        }

        hiddenItems.collectLatest { hiddenItems ->
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
        }
    }

    override suspend fun deleteFile(file: File) {
        if (file.isDeletable) {
            file.delete(context)
        }
    }
}
package de.mm20.launcher2.files

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.*
import kotlinx.coroutines.*

class FilesRepository(
    val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : BaseSearchableRepository() {

    val files = MediatorLiveData<List<File>?>()

    private val allFiles = MutableLiveData<List<File>?>(emptyList())
    private val hiddenItemKeys = hiddenItemsRepository.hiddenItemsKeys

    private val nextcloudClient by lazy {
        NextcloudApiHelper(context)
    }
    private val owncloudClient by lazy {
        OwncloudClient(context)
    }

    init {
        files.addSource(hiddenItemKeys) { keys ->
            files.value = allFiles.value?.filter { !keys.contains(it.key) }
        }
        files.addSource(allFiles) { f ->
            files.value = f?.filter { hiddenItemKeys.value?.contains(it.key) != true }
        }
    }

    override suspend fun search(query: String) {
        if (query.isBlank()) {
            allFiles.value = null
            return
        }
        val localFiles = withContext(Dispatchers.IO) {
            File.search(context, query).sorted().toMutableList()
        }
        allFiles.value = localFiles

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
        allFiles.value = localFiles + cloudFiles
    }

    fun removeFile(file: File) {
        allFiles.value = allFiles.value?.filter { it != file }
    }
}
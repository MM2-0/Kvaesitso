package de.mm20.launcher2.files.providers

import de.mm20.launcher2.files.R
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

internal class NextcloudFileProvider(
    private val nextcloudClient: NextcloudApiHelper
) : FileProvider {
    override suspend fun search(query: String, allowNetwork: Boolean): List<File> {
        if (query.length < 4 || !allowNetwork) return emptyList()
        val server = nextcloudClient.getServer() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            nextcloudClient.files.search(query).let { it.subList(0, min(10, it.size)) }.map {
                NextcloudFile(
                    fileId = it.id,
                    label = it.name,
                    path = server + it.url,
                    mimeType = it.mimeType,
                    size = it.size,
                    isDirectory = it.isDirectory,
                    server = server,
                    metaData = it.owner?.let { persistentMapOf(FileMetaType.Owner to it) }
                        ?: persistentMapOf()
                )
            }
        }
    }
}
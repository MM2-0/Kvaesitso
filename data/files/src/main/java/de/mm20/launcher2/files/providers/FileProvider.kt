package de.mm20.launcher2.files.providers

import de.mm20.launcher2.search.data.File

interface FileProvider {
    suspend fun search(query: String): List<File>
}
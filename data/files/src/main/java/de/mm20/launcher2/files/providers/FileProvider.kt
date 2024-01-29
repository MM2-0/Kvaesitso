package de.mm20.launcher2.files.providers

import de.mm20.launcher2.search.File

internal interface FileProvider {
    suspend fun search(query: String, allowNetwork: Boolean): List<File>
}
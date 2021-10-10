package de.mm20.launcher2.files

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.File

class FilesViewModel(
    private val filesRepository: FilesRepository
): ViewModel() {


    val files = filesRepository.files

    fun removeFile(file: File) {
        filesRepository.removeFile(file)
    }
}
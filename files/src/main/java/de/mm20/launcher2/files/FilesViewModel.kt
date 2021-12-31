package de.mm20.launcher2.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.search.data.File
import kotlinx.coroutines.launch

class FilesViewModel(
    private val filesRepository: FileRepository
): ViewModel() {
    fun deleteFile(file: File) {
        viewModelScope.launch {
            filesRepository.deleteFile(file)
        }
    }
}
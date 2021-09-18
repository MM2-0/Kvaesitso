package de.mm20.launcher2.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.mm20.launcher2.search.data.File

class FilesViewModel(app: Application): AndroidViewModel(app) {


    private val repository = FilesRepository.getInstance(app)
    val files = repository.files

    fun removeFile(file: File) {
        repository.removeFile(file)
    }
}
package de.mm20.launcher2.ui.launcher.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.File
import org.koin.core.component.KoinComponent

class SearchViewModel: ViewModel(), KoinComponent {

    fun search(query: String) {

    }

    private val _files = MutableLiveData<List<File>>()
    val files: LiveData<List<File>> = _files
}
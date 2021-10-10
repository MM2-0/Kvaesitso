package de.mm20.launcher2.applications

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.Application

class AppViewModel(
    appRepository: AppRepository
): ViewModel() {
    val applications: LiveData<List<Application>> = appRepository.applications
}


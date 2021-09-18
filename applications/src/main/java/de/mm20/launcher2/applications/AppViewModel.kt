package de.mm20.launcher2.applications

import android.app.Application as AndroidApp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.search.data.Application

class AppViewModel(app: AndroidApp): AndroidViewModel(app) {
    private val repository = AppRepository.getInstance(app)
    val applications: LiveData<List<Application>> = repository.applications
}


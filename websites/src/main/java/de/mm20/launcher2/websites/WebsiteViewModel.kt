package de.mm20.launcher2.websites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.search.data.Website

class WebsiteViewModel(val app: Application): AndroidViewModel(app) {
    val website: LiveData<Website?> = WebsiteRepository.getInstance(app).website
}
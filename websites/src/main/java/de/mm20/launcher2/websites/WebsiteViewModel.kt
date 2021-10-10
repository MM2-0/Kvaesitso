package de.mm20.launcher2.websites

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.Website

class WebsiteViewModel(
    websiteRepository: WebsiteRepository
): ViewModel() {
    val website: LiveData<Website?> = websiteRepository.website
}
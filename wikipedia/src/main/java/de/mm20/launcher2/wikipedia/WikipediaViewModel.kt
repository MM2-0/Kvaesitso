package de.mm20.launcher2.wikipedia

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.Wikipedia

class WikipediaViewModel(
    wikipediaRepository: WikipediaRepository
): ViewModel() {
    val wikipedia: LiveData<Wikipedia?> = wikipediaRepository.wikipedia
}
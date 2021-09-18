package de.mm20.launcher2.wikipedia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.search.data.Wikipedia

class WikipediaViewModel(val app: Application): AndroidViewModel(app) {
    val wikipedia: LiveData<Wikipedia?> = WikipediaRepository.getInstance(app).wikipedia
}
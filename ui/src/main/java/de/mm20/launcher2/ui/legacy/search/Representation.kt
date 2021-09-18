package de.mm20.launcher2.ui.legacy.search

import androidx.transition.Scene
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.legacy.searchable.SearchableView

interface Representation {
    fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?) : Scene
}
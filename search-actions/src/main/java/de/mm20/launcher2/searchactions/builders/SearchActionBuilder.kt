package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.TextClassificationResult

interface SearchActionBuilder {
    fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction?
}
package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.WebsearchAction

class WebsearchActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_websearch))

    override val icon: SearchActionIcon = SearchActionIcon.WebSearch
    override val key: String
        get() = "websearch"

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction {
        return WebsearchAction(
            context.getString(R.string.search_action_websearch), classifiedQuery.text
        )
    }
}
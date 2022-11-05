package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.MessageAction
import de.mm20.launcher2.searchactions.actions.OpenUrlAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class OpenUrlActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_open_url))

    override val key: String
        get() = "website"

    override val icon: SearchActionIcon = SearchActionIcon.Website

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.url != null) {
            return OpenUrlAction(
                context.getString(R.string.search_action_open_url), classifiedQuery.url
            )
        }
        return null
    }
}
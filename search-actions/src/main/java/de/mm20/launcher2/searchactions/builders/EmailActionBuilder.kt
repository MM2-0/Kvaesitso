package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.EmailAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class EmailActionBuilder(
    override val label: String
): SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_email))

    override val key: String
        get() = "email"

    override val icon: SearchActionIcon = SearchActionIcon.Email

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.email != null) {
            return EmailAction(
                context.getString(R.string.search_action_email), classifiedQuery.email
            )
        }
        return null
    }

}
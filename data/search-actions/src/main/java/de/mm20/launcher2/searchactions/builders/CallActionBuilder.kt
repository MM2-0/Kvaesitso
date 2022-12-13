package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.CallAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class CallActionBuilder(
    override val label: String
): SearchActionBuilder {

    constructor(context: Context): this(context.getString(R.string.search_action_call))

    override val key: String
        get() = "call"

    override val icon: SearchActionIcon = SearchActionIcon.Phone

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.phoneNumber != null) {
            return CallAction(
                context.getString(R.string.search_action_call), classifiedQuery.phoneNumber
            )
        }
        return null
    }

}
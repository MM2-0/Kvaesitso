package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.MessageAction
import de.mm20.launcher2.searchactions.actions.SearchAction

object MessageActionBuilder: SearchActionBuilder {

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.phoneNumber != null) {
            return MessageAction(
                context.getString(R.string.search_action_message), classifiedQuery.phoneNumber
            )
        }
        return null
    }
}
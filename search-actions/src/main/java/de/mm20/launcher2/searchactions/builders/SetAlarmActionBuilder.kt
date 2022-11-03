package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SetAlarmAction
import java.time.LocalDate

object SetAlarmActionBuilder : SearchActionBuilder {

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.time != null) {
            return SetAlarmAction(
                context.getString(R.string.search_action_alarm), classifiedQuery.time
            )
        }
        return null
    }

}
package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.SetAlarmAction
import java.time.LocalDate

class SetAlarmActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_alarm))

    override val key: String
        get() = "alarm"

    override val icon: SearchActionIcon = SearchActionIcon.Alarm

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.time != null) {
            return SetAlarmAction(
                context.getString(R.string.search_action_alarm), classifiedQuery.time
            )
        }
        return null
    }

}
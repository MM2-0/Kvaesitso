package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.TimerAction

class TimerActionBuilder(
    override val label: String,
) : SearchActionBuilder {
    constructor(context: Context) : this(context.getString(R.string.search_action_timer))

    override val key: String
        get() = "timer"

    override val icon: SearchActionIcon = SearchActionIcon.Timer

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        // Maximum time span for a timer is 24 hours, see https://developer.android.com/reference/android/provider/AlarmClock#EXTRA_LENGTH
        if (classifiedQuery.timespan != null && classifiedQuery.timespan.seconds <= 86400) {
            return TimerAction(
                context.getString(R.string.search_action_timer), classifiedQuery.timespan
            )
        }
        return null
    }

}
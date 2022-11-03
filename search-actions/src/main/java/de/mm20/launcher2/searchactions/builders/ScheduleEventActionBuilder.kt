package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.MessageAction
import de.mm20.launcher2.searchactions.actions.ScheduleEventAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import java.time.LocalDateTime

object ScheduleEventActionBuilder : SearchActionBuilder {

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.date != null) {
            return ScheduleEventAction(
                context.getString(R.string.search_action_event),
                date = classifiedQuery.date,
                time = classifiedQuery.time
            )
        }
        if (classifiedQuery.timespan != null && classifiedQuery.timespan.seconds > 86400) {
            val datetime = LocalDateTime.now().plus(classifiedQuery.timespan)
            return ScheduleEventAction(
                context.getString(R.string.search_action_event),
                date = datetime.toLocalDate(),
                time = datetime.toLocalTime(),
            )
        }
        return null
    }
}
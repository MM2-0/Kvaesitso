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
        val duration = classifiedQuery.timespan
        if (duration != null && duration.seconds in 1..86400) {
            val totalSeconds = duration.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            val preview = buildList {
                if (hours > 0) add("${hours}h")
                if (minutes > 0) add("${minutes}m")
                if (seconds > 0 || isEmpty()) add("${seconds}s")
            }.joinToString(" ")

            return TimerAction(
                "${context.getString(R.string.search_action_timer)} · $preview",
                duration
            )
        }
        return null
    }

}
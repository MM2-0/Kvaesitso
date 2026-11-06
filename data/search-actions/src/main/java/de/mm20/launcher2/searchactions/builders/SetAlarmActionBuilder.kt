package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.SetAlarmAction
import java.util.Locale

class SetAlarmActionBuilder(
    override val label: String
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_alarm))

    override val key: String
        get() = "alarm"

    override val icon: SearchActionIcon = SearchActionIcon.Alarm

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (classifiedQuery.time != null) {
            val time = classifiedQuery.time
            val preview = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)
            return SetAlarmAction(
                "${context.getString(R.string.search_action_alarm)} · $preview",
                time
            )
        }
        return null
    }

}
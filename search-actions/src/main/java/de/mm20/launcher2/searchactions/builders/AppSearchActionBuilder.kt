package de.mm20.launcher2.searchactions.builders

import android.content.Context
import android.content.pm.LauncherActivityInfo
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.TextType
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction

class AppSearchActionBuilder(
    val label: String,
    val activity: LauncherActivityInfo,
    val filter: TextType? = null,
) : SearchActionBuilder {
    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        return AppSearchAction(
            label = label,
            componentName = activity.componentName,
            query = classifiedQuery.text,
        )
    }
}
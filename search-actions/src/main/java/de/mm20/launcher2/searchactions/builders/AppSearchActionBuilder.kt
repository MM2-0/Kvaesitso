package de.mm20.launcher2.searchactions.builders

import android.content.ComponentName
import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.TextType
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class AppSearchActionBuilder(
    override val label: String,
    val componentName: ComponentName,
    override val icon: SearchActionIcon = SearchActionIcon.Search,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : CustomizableSearchActionBuilder {

    override val key: String
        get() = "app://${componentName.flattenToShortString()}"
    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        return AppSearchAction(
            label = label,
            componentName = componentName,
            query = classifiedQuery.text,
        )
    }
}
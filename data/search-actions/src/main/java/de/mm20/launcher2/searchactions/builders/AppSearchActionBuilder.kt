package de.mm20.launcher2.searchactions.builders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.TextType
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

data class AppSearchActionBuilder(
    override val label: String,
    val baseIntent: Intent,
    override val icon: SearchActionIcon = SearchActionIcon.Custom,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : CustomizableSearchActionBuilder {

    override val key: String
        get() = "app://${baseIntent.toUri(0)}"
    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction {
        return AppSearchAction(
            label = label,
            baseIntent = baseIntent,
            query = classifiedQuery.text,
            icon = icon,
            iconColor = iconColor,
            customIcon = customIcon,
        )
    }
}
package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.flow.FlowAlias

/** The service resolves this builder with current app/profile/visibility state. */
data class FlowAliasActionBuilder(val alias: FlowAlias) : SearchActionBuilder {
    override val key get() = "flow-alias://${alias.id}"
    override val label get() = alias.alias
    override val icon get() = SearchActionIcon.Search
    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? = null
}

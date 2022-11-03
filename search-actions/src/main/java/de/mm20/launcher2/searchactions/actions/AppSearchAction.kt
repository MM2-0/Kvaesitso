package de.mm20.launcher2.searchactions.actions

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.ktx.tryStartActivity

data class AppSearchAction(
    override val label: String,
    val componentName: ComponentName,
    val query: String,
): SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Search
    override val iconColor: Int = 0

    override fun start(context: Context) {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            component = componentName
            putExtra(SearchManager.QUERY, query)
        }
        context.tryStartActivity(intent)
    }
}